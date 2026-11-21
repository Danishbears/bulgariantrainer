package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.dto.PageResponse;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.AoristException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service @Transactional(readOnly=true)
public class AoristVerbService {
    private final AoristVerbRepository verbs;
    private final AoristDatasetRepository datasets;
    private final AoristValidation validation;
    private final AoristJson json;
    public AoristVerbService(AoristVerbRepository verbs,AoristDatasetRepository datasets,AoristValidation validation,AoristJson json) {
        this.verbs=verbs; this.datasets=datasets; this.validation=validation; this.json=json;
    }
    public PageResponse<Response> list(String datasetId,String q,String aspect,String group,String particle,
                                       boolean archived,int page,int size) {
        if(page<0 || size<1 || size>100 || (q!=null && q.length()>255) || (group!=null && (group.isBlank() || group.length()>100))
                || (aspect!=null && !AoristValidation.ASPECTS.contains(aspect))
                || (particle!=null && !Set.of("none","се","си").contains(particle))) throw invalid("Invalid search, filter or pagination");
        if(datasetId!=null) validation.datasetId(datasetId);
        Specification<AoristVerb> spec=(root,query,cb)-> {
            List<jakarta.persistence.criteria.Predicate> predicates=new ArrayList<>();
            predicates.add(cb.equal(root.get("archived"),archived));
            if(datasetId!=null) predicates.add(cb.equal(root.get("dataset").get("namespace"),datasetId));
            if(aspect!=null) predicates.add(cb.equal(root.get("aspect"),aspect));
            if(group!=null) predicates.add(cb.equal(root.get("studyGroup"),group));
            if(particle!=null) predicates.add(particle.equals("none")?cb.isNull(root.get("reflexiveParticle")):cb.equal(root.get("reflexiveParticle"),particle));
            if(q!=null && !VocabularyText.clean(q).isEmpty()) predicates.add(cb.like(root.get("searchText"),VocabularyText.searchPattern(q),'!'));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<AoristVerb> rows=verbs.findAll(spec,PageRequest.of(page,size,Sort.by("id")));
        return new PageResponse<>(rows.getContent().stream().map(this::response).toList(),page,size,rows.getTotalElements(),rows.getTotalPages());
    }
    public Response get(long id) { return response(verbs.findById(id).orElseThrow(()->missing("Verb not found"))); }
    public List<Dataset> datasets() { return datasets.findAll(Sort.by("id")).stream().map(this::dataset).toList(); }
    public Filters filters(String datasetId) {
        if(datasetId!=null) validation.datasetId(datasetId);
        return new Filters(verbs.findGroups(datasetId),verbs.findAspects(datasetId));
    }
    @Transactional
    public Response create(Request request) {
        validation.datasetId(request.datasetId()); Entry e=validation.requireEntry(request.entry());
        if(request.version()!=null) throw invalid("New entries must not specify a row version");
        AoristDataset dataset=datasets.findForUpdate(request.datasetId()).orElse(null);
        if(dataset==null) {
            validation.requireMetadata(request.metadata());
            if(!request.datasetId().equals(request.metadata().path("datasetId").asText())) throw invalid("Metadata datasetId does not match");
            dataset=datasets.saveAndFlush(new AoristDataset(request.datasetId(),json.write(request.metadata()),request.metadata().path("datasetRevision").intValue()));
        } else if(request.metadata()!=null && !request.metadata().isNull()) {
            validation.requireMetadata(request.metadata());
            if(!json.tree(dataset.getMetadataJson()).equals(request.metadata())) throw invalid("Existing dataset metadata cannot be overwritten by verb creation");
        }
        if(verbs.findByDatasetIdAndExternalId(dataset.getId(),e.id()).isPresent())
            throw new AoristException(409,"DUPLICATE_AORIST_VERB","External ID already exists in this dataset, including archived entries");
        return response(insert(dataset,e));
    }
    @Transactional
    public Response update(long id,Request request) {
        validation.datasetId(request.datasetId()); Entry e=validation.requireEntry(request.entry());
        if(request.metadata()!=null && !request.metadata().isNull()) throw invalid("Edit entry content only; dataset metadata is managed by import");
        AoristVerb v=locked(id);
        if(!v.getDataset().getNamespace().equals(request.datasetId()) || !v.getExternalId().equals(e.id())) throw invalid("Dataset and external ID are immutable");
        requireVersion(v,request.version()); apply(v,e); verbs.flush(); return response(v);
    }
    @Transactional
    public Response archive(long id,ArchiveRequest request) {
        AoristVerb v=locked(id); requireVersion(v,request.version()); v.setArchived(request.archived());
        verbs.flush(); return response(v);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Export export(String datasetId,Boolean archived,String scope) {
        validation.datasetId(datasetId);
        if(scope!=null && (!scope.equals("all") || archived!=null)) throw invalid("archiveScope=all cannot be combined with archived");
        AoristDataset dataset=datasets.findByNamespace(datasetId).orElseThrow(()->missing("Dataset not found"));
        List<Entry> entries=verbs.findForExport(datasetId).stream().filter(v->scope!=null || v.isArchived()==Boolean.TRUE.equals(archived))
                .map(v->response(v).entry()).toList();
        if(entries.isEmpty()) throw new AoristException(409,"EMPTY_AORIST_EXPORT","No entries in the requested export scope");
        if(entries.size()>1000) throw new AoristException(413,"EXPORT_TOO_LARGE","Format v1 supports at most 1000 entries per document");
        ObjectNode document=(ObjectNode)json.tree(dataset.getMetadataJson());
        document.set("entries",json.tree(json.write(entries)));
        String output=json.write(document);
        if(output.getBytes(StandardCharsets.UTF_8).length>AoristImportParser.MAX_BYTES)
            throw new AoristException(413,"EXPORT_TOO_LARGE","Export exceeds the supported 1 MiB import limit");
        return new Export(datasetId+"-"+(scope!=null?"all":Boolean.TRUE.equals(archived)?"archived":"active")+".json",output);
    }
    AoristVerb insert(AoristDataset dataset,Entry entry) {
        AoristVerb verb=new AoristVerb(dataset,entry.id()); apply(verb,entry); return verbs.saveAndFlush(verb);
    }
    void apply(AoristVerb verb,Entry entry) {
        String hash=json.hash(entry); if(hash.equals(verb.getContentHash())) return;
        verb.clearForms(); if(verb.getId()!=null) verbs.flush();
        StringBuilder search=new StringBuilder(String.join(" ",entry.id(),entry.lemma(),entry.russian(),entry.studyGroup()));
        for(String person:AoristValidation.PERSONS) for(String answer:entry.forms().get(person)) search.append(' ').append(answer);
        verb.update(entry,json.write(entry.source()),VocabularyText.key(search.toString()),hash);
        for(String person:AoristValidation.PERSONS) {
            int position=0;
            for(String answer:entry.forms().get(person)) verb.addForm(person,++position,answer,AoristAnswerNormalizer.key(answer));
        }
    }
    Response response(AoristVerb v) {
        Map<String,List<String>> forms=new LinkedHashMap<>();
        for(String person:AoristValidation.PERSONS) forms.put(person,v.forms(person));
        Entry e=new Entry(v.getExternalId(),v.getLemma(),v.getRussian(),v.getAspect(),v.getTense(),v.getReflexiveParticle(),
                Collections.unmodifiableMap(forms),v.getNote(),json.read(v.getSourceJson(),Source.class),v.getStudyGroup());
        return new Response(v.getId(),v.getDataset().getNamespace(),v.getVersion(),v.isArchived(),e,v.getCreatedAt(),v.getUpdatedAt());
    }
    private Dataset dataset(AoristDataset d) { return new Dataset(d.getId(),d.getNamespace(),d.getDatasetRevision(),d.getVersion(),d.getFileHash(),json.tree(d.getMetadataJson())); }
    private AoristVerb locked(long id) {
        String namespace=verbs.findNamespace(id).orElseThrow(()->missing("Verb not found"));
        datasets.findForUpdate(namespace).orElseThrow(); return verbs.findForUpdate(id).orElseThrow();
    }
    private void requireVersion(AoristVerb v,Long expected) {
        if(expected==null || expected<0) throw invalid("Current nonnegative row version is required");
        if(v.getVersion()!=expected) throw new AoristException(409,"STALE_AORIST_VERB","Entry changed; reload before editing");
    }
    private AoristException invalid(String message) { return new AoristException(400,"INVALID_AORIST_REQUEST",message); }
    private AoristException missing(String message) { return new AoristException(404,"AORIST_NOT_FOUND",message); }
}
