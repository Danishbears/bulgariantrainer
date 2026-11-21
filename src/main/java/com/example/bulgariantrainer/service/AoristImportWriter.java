package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.AoristException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AoristImportWriter {
    private final AoristDatasetRepository datasets;
    private final AoristVerbRepository verbs;
    private final AoristImportReceiptRepository receipts;
    private final AoristVerbService service;
    private final AoristJson json;
    public AoristImportWriter(AoristDatasetRepository datasets,AoristVerbRepository verbs,
                               AoristImportReceiptRepository receipts,AoristVerbService service,AoristJson json) {
        this.datasets=datasets; this.verbs=verbs; this.receipts=receipts; this.service=service; this.json=json;
    }
    @Transactional(readOnly=true)
    public Summary previous(ConfirmRequest request) {
        return receipts.findById(request.requestId()).map(receipt->{
            if(!receipt.getRequestHash().equals(json.hash(request))) throw conflict("IMPORT_REQUEST_CHANGED","Request UUID used with different data");
            return json.read(receipt.getSummaryJson(),Summary.class);
        }).orElse(null);
    }
    @Transactional
    public Summary save(AoristImportStore.Pending pending,ConfirmRequest request) {
        Preview preview=pending.preview();
        if(!preview.valid()) throw new AoristException(400,"INVALID_IMPORT_DOCUMENT","The entire document must be valid");
        String namespace=preview.metadata().path("datasetId").asText();
        int revision=preview.metadata().path("datasetRevision").intValue();
        AoristDataset dataset=datasets.findForUpdate(namespace).orElse(null);
        Summary previous=previous(request); if(previous!=null) return previous;
        if(receipts.existsByImportId(request.importId())) throw conflict("IMPORT_ALREADY_CONFIRMED","Preview already confirmed");
        if((dataset==null)!=(pending.datasetInternalId()==null) || (dataset!=null
                && (!dataset.getId().equals(pending.datasetInternalId()) || dataset.getVersion()!=pending.datasetVersion())))
            throw stale();
        if(dataset!=null && revision<dataset.getDatasetRevision() && !Boolean.TRUE.equals(request.allowRevisionDowngrade()))
            throw conflict("REVISION_DOWNGRADE_CONFIRMATION_REQUIRED","Explicitly allow the previewed source revision downgrade");
        Map<String,PreviewEntry> source=new HashMap<>(); for(var row:preview.entries()) source.put(row.externalId(),row);
        Map<String,Selection> selected=new HashMap<>();
        for(Selection selection:request.entries()) {
            PreviewEntry row=source.get(selection.externalId());
            if(row==null || selected.putIfAbsent(selection.externalId(),selection)!=null
                    || !Objects.equals(row.expectedVersion(),selection.expectedVersion()))
                throw new AoristException(400,"INVALID_IMPORT_SELECTION","Unknown/repeated ID or forged expected version");
        }
        Map<String,AoristVerb> existing=new HashMap<>();
        if(dataset!=null) {
            List<AoristVerb> toLock=new ArrayList<>();
            for(String external:selected.keySet()) verbs.findByDatasetIdAndExternalId(dataset.getId(),external).ifPresent(toLock::add);
            toLock.sort(Comparator.comparing(AoristVerb::getId));
            for(AoristVerb v:toLock) existing.put(v.getExternalId(),verbs.findForUpdate(v.getId()).orElseThrow());
        }
        for(String external:selected.keySet()) {
            PreviewEntry row=source.get(external); AoristVerb v=existing.get(external);
            if((v==null)!=(row.existingId()==null) || (v!=null && (!v.getId().equals(row.existingId()) || v.getVersion()!=row.expectedVersion()))) throw stale();
        }
        if(dataset==null) dataset=datasets.saveAndFlush(new AoristDataset(namespace,json.write(preview.metadata()),revision));
        int created=0,updated=0,unchanged=0,deselected=0; List<RowResult> rows=new ArrayList<>();
        for(PreviewEntry row:preview.entries()) {
            if(!selected.containsKey(row.externalId())) { deselected++; rows.add(new RowResult(row.externalId(),"DESELECTED",row.existingId())); continue; }
            AoristVerb v=existing.get(row.externalId()); String outcome;
            if(v==null) { v=service.insert(dataset,row.after()); created++; outcome="CREATED"; }
            else if(v.getContentHash().equals(json.hash(row.after()))) { unchanged++; outcome="UNCHANGED"; }
            else { service.apply(v,row.after()); updated++; outcome="UPDATED"; }
            rows.add(new RowResult(row.externalId(),outcome,v.getId()));
        }
        dataset.recordImport(json.write(preview.metadata()),revision,preview.fileHash());
        Summary summary=new Summary(request.importId(),namespace,revision,preview.fileHash(),created,updated,unchanged,deselected,List.copyOf(rows));
        receipts.save(new AoristImportReceipt(request.requestId(),request.importId(),dataset,json.hash(request),preview.fileHash(),json.write(preview.metadata()),json.write(summary)));
        verbs.flush(); receipts.flush(); return summary;
    }
    private AoristException stale() { return conflict("STALE_IMPORT_PREVIEW","Content changed since preview; upload again"); }
    private AoristException conflict(String code,String message) { return new AoristException(409,code,message); }
}
