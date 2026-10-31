package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.dto.PageResponse;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.exception.AspectPairException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.util.*;

@Service @Transactional(readOnly=true)
public class AspectPairService {
    private final AspectPairRepository pairs;
    private final AspectDatasetRepository datasets;
    private final AspectPairValidation validation;
    private final AspectPairJson json;
    public AspectPairService(AspectPairRepository pairs,AspectDatasetRepository datasets,
                             AspectPairValidation validation,AspectPairJson json){
        this.pairs=pairs;this.datasets=datasets;this.validation=validation;this.json=json;
    }
    public PageResponse<Response> list(String namespace,String q,Integer stage,String topic,String relation,
                                       boolean archived,int page,int size){
        if(page<0 || size<1 || size>100 || (stage!=null && (stage<1 || stage>3))
                || (q!=null && q.length()>255) || (topic!=null && topic.length()>255)
                || (relation!=null && !AspectPairValidation.RELATIONS.contains(relation)))
            throw invalid("Invalid search, filter or pagination settings");
        if(namespace!=null)validation.namespace(namespace);
        Specification<AspectPair> spec=(root,query,cb)->{
            List<jakarta.persistence.criteria.Predicate> predicates=new ArrayList<>();
            predicates.add(cb.equal(root.get("archived"),archived));
            if(namespace!=null)predicates.add(cb.equal(root.get("dataset").get("namespace"),namespace));
            if(stage!=null)predicates.add(cb.equal(root.get("studyStage"),stage));
            if(topic!=null)predicates.add(cb.equal(root.get("topic"),topic));
            if(relation!=null)predicates.add(cb.equal(root.get("relationType"),relation));
            if(q!=null && !VocabularyText.clean(q).isEmpty())predicates.add(cb.like(root.get("searchText"),VocabularyText.searchPattern(q),'!'));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AspectPair> result=pairs.findAll(spec,PageRequest.of(page,size,Sort.by("id")));
        return new PageResponse<>(result.getContent().stream().map(this::response).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    public Response get(long id){return response(pair(id));}
    public List<Dataset> datasets(){return datasets.findAll(Sort.by("id")).stream()
            .map(d->new Dataset(d.getId(),d.getNamespace(),d.getVersion(),d.getContentRevision(),json.tree(d.getMetadataJson()))).toList();}

    @Transactional
    public Response create(Request request){
        validation.namespace(request.namespace());validation.requireValid(request.entry());
        if(request.version()!=null)throw invalid("New pairs must not specify an existing version");
        AspectDataset dataset=datasets.findForUpdate(request.namespace())
                .orElseGet(()->datasets.saveAndFlush(new AspectDataset(request.namespace())));
        if(pairs.findByDatasetIdAndExternalId(dataset.getId(),request.entry().id()).isPresent())
            throw new AspectPairException(409,"DUPLICATE_ASPECT_PAIR","External ID already exists in this namespace, including archived entries");
        return response(insert(dataset,request.entry()));
    }
    @Transactional
    public Response update(long id,Request request){
        validation.namespace(request.namespace());validation.requireValid(request.entry());
        AspectPair pair=lockedPair(id);
        if(!pair.getDataset().getNamespace().equals(request.namespace()) || !pair.getExternalId().equals(request.entry().id()))
            throw invalid("Namespace and external ID are immutable");
        requireVersion(pair,request.version());
        apply(pair,request.entry());pairs.flush();return response(pair);
    }
    @Transactional
    public Response archive(long id,ArchiveRequest request){
        AspectPair pair=lockedPair(id);requireVersion(pair,request.version());
        pair.setArchived(request.archived());pairs.flush();return response(pair);
    }
    AspectPair insert(AspectDataset dataset,Entry entry){
        AspectPair pair=new AspectPair(dataset,entry.id());apply(pair,entry);return pairs.saveAndFlush(pair);
    }
    void apply(AspectPair pair,Entry entry){
        String hash=json.hash(entry);
        if(hash.equals(pair.getContentHash()))return;
        // Flush orphan deletions before reusing the answer positions/keys.
        pair.clearAnswers();if(pair.getId()!=null)pairs.flush();
        pair.update(entry,json.write(entry.provenance()),entry.examples()==null?null:json.write(entry.examples()),
                VocabularyText.key(String.join(" ",entry.id(),entry.imperfective(),entry.perfective(),
                        entry.russianImperfective(),entry.russianPerfective(),entry.topic(),
                        String.join(" ",entry.acceptedImperfective()),String.join(" ",entry.acceptedPerfective()))),hash);
        int position=0;for(String answer:entry.acceptedImperfective())pair.addAnswer("IMPERFECTIVE",++position,answer,AspectAnswerNormalizer.key(answer));
        position=0;for(String answer:entry.acceptedPerfective())pair.addAnswer("PERFECTIVE",++position,answer,AspectAnswerNormalizer.key(answer));
    }
    Response response(AspectPair p){
        Entry entry=new Entry(p.getExternalId(),p.getImperfective(),p.getPerfective(),p.getRussianImperfective(),
                p.getRussianPerfective(),p.answers("IMPERFECTIVE"),p.answers("PERFECTIVE"),p.getTopic(),p.getStudyStage(),
                p.getRelationType(),p.getNote(),json.read(p.getProvenanceJson(),Provenance.class),
                p.getExamplesJson()==null?null:json.read(p.getExamplesJson(),Examples.class));
        return new Response(p.getId(),p.getDataset().getNamespace(),p.getVersion(),p.isArchived(),entry,p.getCreatedAt(),p.getUpdatedAt());
    }
    private AspectPair pair(long id){if(id<1)throw invalid("ID must be positive");return pairs.findById(id)
            .orElseThrow(()->new AspectPairException(404,"ASPECT_PAIR_NOT_FOUND","Aspect pair not found"));}
    private AspectPair lockedPair(long id){
        if(id<1)throw invalid("ID must be positive");
        String namespace=pairs.findNamespace(id).orElseThrow(()->new AspectPairException(404,"ASPECT_PAIR_NOT_FOUND","Aspect pair not found"));
        datasets.findForUpdate(namespace).orElseThrow();
        return pairs.findForUpdate(id).orElseThrow();
    }
    private void requireVersion(AspectPair pair,Long expected){
        if(expected==null || expected<0)throw invalid("Current row version is required");
        if(pair.getVersion()!=expected)throw new AspectPairException(409,"STALE_ASPECT_PAIR","Pair changed; reload before editing");
    }
    private AspectPairException invalid(String message){return new AspectPairException(400,"INVALID_ASPECT_PAIR",message);}
}
