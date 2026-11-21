package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.entity.AoristDataset;
import com.example.bulgariantrainer.exception.AoristException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.time.*;
import java.util.*;

@Service
public class AoristImportService {
    private final AoristImportParser parser;
    private final AoristImportStore store;
    private final AoristImportWriter writer;
    private final AoristDatasetRepository datasets;
    private final AoristVerbRepository verbs;
    private final AoristVerbService service;
    private final AoristJson json;
    private final TransactionTemplate read;
    public AoristImportService(AoristImportParser parser,AoristImportStore store,AoristImportWriter writer,
                                AoristDatasetRepository datasets,AoristVerbRepository verbs,AoristVerbService service,
                                AoristJson json,PlatformTransactionManager manager) {
        this.parser=parser; this.store=store; this.writer=writer; this.datasets=datasets;
        this.verbs=verbs; this.service=service; this.json=json;
        read=new TransactionTemplate(manager); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public Preview preview(MultipartFile file) {
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');
        filename=filename.substring(filename.lastIndexOf('/')+1);
        if(!filename.toLowerCase(Locale.ROOT).endsWith(".json")) throw new AoristException(415,"UNSUPPORTED_FILE","Use a JSON file");
        if(file.getSize()>AoristImportParser.MAX_BYTES) throw new AoristException(413,"FILE_TOO_LARGE","Maximum file size is 1 MiB");
        byte[] bytes;
        try(var input=file.getInputStream()) { bytes=input.readNBytes(AoristImportParser.MAX_BYTES+1); }
        catch(IOException e) { throw new AoristException(400,"INVALID_UPLOAD","Cannot read uploaded file"); }
        var parsed=parser.parse(bytes); String name=filename;
        var pending=read.execute(status->{
            String namespace=AoristValidation.string(parsed.metadata(),"datasetId");
            AoristDataset dataset=namespace==null?null:datasets.findByNamespace(namespace).orElse(null);
            List<PreviewEntry> entries=new ArrayList<>();
            for(Entry e:parsed.entries()) {
                var existing=dataset==null?null:verbs.findByDatasetIdAndExternalId(dataset.getId(),e.id()).orElse(null);
                String classification=existing==null?"ADD":existing.getContentHash().equals(json.hash(e))?"UNCHANGED":"UPDATE";
                entries.add(new PreviewEntry(e.id(),classification,existing==null?null:existing.getId(),existing==null?null:existing.getVersion(),existing==null?null:service.response(existing),e));
            }
            String hash=json.hashBytes(bytes); List<Warning> warnings=new ArrayList<>(parsed.warnings());
            if(dataset!=null && parsed.errors().isEmpty()) {
                int revision=parsed.metadata().path("datasetRevision").intValue();
                if(revision<dataset.getDatasetRevision()) warnings.add(new Warning("$.datasetRevision","REVISION_DOWNGRADE","Explicit source revision downgrade confirmation is required"));
                if(revision==dataset.getDatasetRevision() && dataset.getFileHash()!=null && !hash.equals(dataset.getFileHash()))
                    warnings.add(new Warning("$.datasetRevision","SAME_REVISION_DIFFERENT_CONTENT","Source revision is unchanged but file bytes differ; select updates explicitly"));
            }
            Preview preview=new Preview(UUID.randomUUID(),name,Instant.now().plus(Duration.ofMinutes(30)),parsed.errors().isEmpty(),hash,
                    parsed.metadata(),List.copyOf(entries),parsed.errors(),List.copyOf(warnings));
            return new AoristImportStore.Pending(preview,dataset==null?null:dataset.getId(),dataset==null?null:dataset.getVersion());
        });
        store.put(pending); return pending.preview();
    }
    public Summary confirm(ConfirmRequest request) {
        Summary previous=writer.previous(request); if(previous!=null) return previous;
        var pending=store.get(request.importId());
        try { synchronized(pending) { return writer.save(pending,request); } }
        catch(DataIntegrityViolationException race) {
            // The failed writer transaction has rolled back before checking a concurrent winner.
            Summary winning=writer.previous(request); if(winning!=null) return winning;
            throw new AoristException(409,"STALE_IMPORT_PREVIEW","Concurrent content change; upload and review again");
        }
    }
}
