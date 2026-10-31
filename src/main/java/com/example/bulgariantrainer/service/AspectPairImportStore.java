package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPairDtos.Preview;
import com.example.bulgariantrainer.exception.AspectPairException;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.*;

@Component
public class AspectPairImportStore {
    public record Pending(Preview preview,Long datasetId,Long datasetVersion) {}
    private final Map<UUID,Pending> pending=new LinkedHashMap<>();
    public synchronized void put(Pending item){
        pending.values().removeIf(p->!p.preview().expiresAt().isAfter(Instant.now()));
        while(pending.size()>=5)pending.remove(pending.keySet().iterator().next());
        pending.put(item.preview().importId(),item);
    }
    public synchronized Pending get(UUID id){
        Pending p=pending.get(id);
        if(p==null || !p.preview().expiresAt().isAfter(Instant.now()))
            throw new AspectPairException(410,"PREVIEW_EXPIRED","Preview expired or application restarted; upload again");
        return p;
    }
}
