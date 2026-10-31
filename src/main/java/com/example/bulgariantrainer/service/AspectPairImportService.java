package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.entity.AspectDataset;
import com.example.bulgariantrainer.exception.AspectPairException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.time.*;
import java.io.IOException;
import java.util.*;

@Service
public class AspectPairImportService {
    private final AspectPairImportParser parser;
    private final AspectPairImportStore store;
    private final AspectPairImportWriter writer;
    private final AspectDatasetRepository datasets;
    private final AspectPairRepository pairs;
    private final AspectPairService service;
    private final AspectPairValidation validation;
    private final AspectPairJson json;
    private final TransactionTemplate read;
    public AspectPairImportService(AspectPairImportParser parser,AspectPairImportStore store,
            AspectPairImportWriter writer,AspectDatasetRepository datasets,AspectPairRepository pairs,
            AspectPairService service,AspectPairValidation validation,AspectPairJson json,PlatformTransactionManager manager){
        this.parser=parser;this.store=store;this.writer=writer;this.datasets=datasets;this.pairs=pairs;
        this.service=service;this.validation=validation;this.json=json;
        read=new TransactionTemplate(manager);read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }
    public Preview preview(String namespace,MultipartFile file){
        validation.namespace(namespace);
        String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');
        filename=filename.substring(filename.lastIndexOf('/')+1);
        if(!filename.toLowerCase(Locale.ROOT).endsWith(".json"))throw new AspectPairException(415,"UNSUPPORTED_FILE","Use a JSON file");
        if(file.getSize()>AspectPairImportParser.MAX_BYTES)throw new AspectPairException(413,"FILE_TOO_LARGE","Maximum file size is 1 MiB");
        byte[] bytes;
        try(var input=file.getInputStream()){bytes=input.readNBytes(AspectPairImportParser.MAX_BYTES+1);}
        catch(IOException e){throw new AspectPairException(400,"INVALID_UPLOAD","Cannot read uploaded file");}
        var parsed=parser.parse(bytes);String name=filename;
        AspectPairImportStore.Pending pending=read.execute(status->{
            AspectDataset dataset=datasets.findByNamespace(namespace).orElse(null);
            List<PreviewEntry> entries=new ArrayList<>();int index=0;
            for(Entry e:parsed.entries()){
                var existing=dataset==null?null:pairs.findByDatasetIdAndExternalId(dataset.getId(),e.id()).orElse(null);
                String classification=existing==null?"ADD":existing.getContentHash().equals(json.hash(e))?"UNCHANGED":"UPDATE";
                entries.add(new PreviewEntry(index++,e.id(),classification,existing==null?null:existing.getId(),
                        existing==null?null:existing.getVersion(),existing==null?null:service.response(existing),e));
            }
            Preview preview=new Preview(UUID.randomUUID(),namespace,name,Instant.now().plus(Duration.ofMinutes(30)),
                    parsed.errors().isEmpty(),json.hashBytes(bytes),parsed.metadata(),List.copyOf(entries),parsed.errors());
            return new AspectPairImportStore.Pending(preview,dataset==null?null:dataset.getId(),dataset==null?null:dataset.getVersion());
        });
        store.put(pending);return pending.preview();
    }
    public Summary confirm(ConfirmRequest request){
        Summary previous=writer.previous(request);if(previous!=null)return previous;
        var pending=store.get(request.importId());
        synchronized(pending){return writer.save(pending,request);}
    }
}
