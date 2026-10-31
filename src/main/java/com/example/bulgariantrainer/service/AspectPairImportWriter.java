package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.exception.AspectPairException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AspectPairImportWriter {
    private final AspectDatasetRepository datasets;
    private final AspectPairRepository pairs;
    private final AspectImportReceiptRepository receipts;
    private final AspectPairService service;
    private final AspectPairJson json;
    public AspectPairImportWriter(AspectDatasetRepository datasets,AspectPairRepository pairs,
                                   AspectImportReceiptRepository receipts,AspectPairService service,AspectPairJson json){
        this.datasets=datasets;this.pairs=pairs;this.receipts=receipts;this.service=service;this.json=json;
    }
    @Transactional(readOnly=true)
    public Summary previous(ConfirmRequest request){
        return receipts.findById(request.requestId()).map(receipt->{
            if(!receipt.getRequestHash().equals(json.hash(request)))throw conflict("IMPORT_REQUEST_CHANGED","Request ID was used with different confirmation data");
            return json.read(receipt.getSummaryJson(),Summary.class);
        }).orElse(null);
    }
    @Transactional
    public Summary save(AspectPairImportStore.Pending pending,ConfirmRequest request){
        Preview preview=pending.preview();
        AspectDataset dataset=datasets.findForUpdate(preview.namespace()).orElse(null);
        // The dataset lock serializes confirmations from separate previews and management writes.
        Summary previous=previous(request);if(previous!=null)return previous;
        if(receipts.existsByImportId(request.importId()))throw conflict("IMPORT_ALREADY_CONFIRMED","Preview already confirmed");
        if(!preview.valid())throw new AspectPairException(400,"INVALID_IMPORT_DOCUMENT","The entire document must be valid before confirmation");
        if((dataset==null)!=(pending.datasetId()==null) || (dataset!=null &&
                (!dataset.getId().equals(pending.datasetId()) || dataset.getVersion()!=pending.datasetVersion())))
            throw conflict("STALE_IMPORT_PREVIEW","Dataset changed since preview; upload again");
        Map<String,PreviewEntry> source=new HashMap<>();for(var row:preview.entries())source.put(row.externalId(),row);
        Map<String,Selection> selected=new HashMap<>();
        for(Selection selection:request.entries()){
            PreviewEntry row=source.get(selection.externalId());
            if(row==null || selected.putIfAbsent(selection.externalId(),selection)!=null
                    || !Objects.equals(row.expectedVersion(),selection.expectedVersion()))
                throw new AspectPairException(400,"INVALID_IMPORT_SELECTION","Unknown/repeated ID or forged expected version");
        }
        // Check every selected entry before creating/updating anything.
        Map<String,AspectPair> existing=new HashMap<>();
        if(dataset!=null){
            List<AspectPair> toLock=new ArrayList<>();
            for(String external:selected.keySet())pairs.findByDatasetIdAndExternalId(dataset.getId(),external).ifPresent(toLock::add);
            toLock.sort(Comparator.comparing(AspectPair::getId));
            for(AspectPair p:toLock)existing.put(p.getExternalId(),pairs.findForUpdate(p.getId()).orElseThrow());
        }
        for(String external:selected.keySet()){
            PreviewEntry row=source.get(external);AspectPair pair=existing.get(external);
            if((pair==null)!=(row.existingId()==null) || (pair!=null &&
                    (!pair.getId().equals(row.existingId()) || pair.getVersion()!=row.expectedVersion())))
                throw conflict("STALE_IMPORT_PREVIEW","Pair changed since preview; upload again");
        }
        if(dataset==null)dataset=datasets.saveAndFlush(new AspectDataset(preview.namespace()));
        int created=0,updated=0,unchanged=0,deselected=0;List<RowResult> rows=new ArrayList<>();
        for(PreviewEntry row:preview.entries()){
            if(!selected.containsKey(row.externalId())){deselected++;rows.add(new RowResult(row.externalId(),"DESELECTED",row.existingId()));continue;}
            AspectPair pair=existing.get(row.externalId());String outcome;
            if(pair==null){pair=service.insert(dataset,row.after());created++;outcome="CREATED";}
            else if(pair.getContentHash().equals(json.hash(row.after()))){unchanged++;outcome="UNCHANGED";}
            else {service.apply(pair,row.after());updated++;outcome="UPDATED";}
            rows.add(new RowResult(row.externalId(),outcome,pair.getId()));
        }
        dataset.recordImport(json.write(preview.metadata()),preview.fileHash());
        Summary summary=new Summary(request.importId(),preview.namespace(),preview.fileHash(),created,updated,unchanged,deselected,List.copyOf(rows));
        receipts.save(new AspectImportReceipt(request.requestId(),request.importId(),dataset,json.hash(request),preview.fileHash(),json.write(preview.metadata()),json.write(summary)));
        pairs.flush();receipts.flush();return summary;
    }
    private AspectPairException conflict(String code,String message){return new AspectPairException(409,code,message);}
}
