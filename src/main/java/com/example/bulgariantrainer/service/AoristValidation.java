package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.exception.AoristException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;

@Component
public class AoristValidation {
    public static final List<String> PERSONS=List.of("1sg","2sg","3sg","1pl","2pl","3pl");
    public static final Set<String> ASPECTS=Set.of("imperfective","perfective");
    private static final Set<String> METADATA=Set.of("format","formatVersion","datasetId","datasetRevision",
            "createdOn","language","translationLanguage","tense","entryForm","notes");

    public String datasetId(String value) {
        if(value==null || !value.matches("[a-z0-9][a-z0-9._-]{0,99}"))
            throw new AoristException(400,"INVALID_DATASET_ID","datasetId must use 1–100 lowercase ASCII letters, digits, dots, underscores or hyphens");
        return value;
    }
    public List<ValidationError> metadataErrors(JsonNode n) {
        List<ValidationError> errors=new ArrayList<>();
        fields(n,METADATA,"$",errors);
        if(n==null || !n.isObject()) return errors;
        constant(n,"format","bulgariantrainer.aorist","UNSUPPORTED_FORMAT",errors);
        if(!positiveInteger(n.get("formatVersion")) || n.get("formatVersion").intValue()!=1)
            errors.add(new ValidationError("$.formatVersion","UNSUPPORTED_FORMAT_VERSION","Only integer formatVersion 1 is supported"));
        String id=string(n,"datasetId");
        try { datasetId(id); } catch(AoristException e) { error(errors,"$.datasetId",e.getMessage()); }
        if(!positiveInteger(n.get("datasetRevision"))) error(errors,"$.datasetRevision","Expected a positive integer");
        constant(n,"language","bg","INVALID_FIELD",errors);
        constant(n,"translationLanguage","ru","INVALID_FIELD",errors);
        constant(n,"tense","aorist","INVALID_FIELD",errors);
        constant(n,"entryForm","first_person_singular_present_dictionary_form","INVALID_FIELD",errors);
        date(string(n,"createdOn"),"$.createdOn",errors);
        JsonNode notes=n.get("notes");
        if(notes==null || !notes.isArray() || notes.size()>50) error(errors,"$.notes","Expected up to 50 strings");
        else for(int i=0;i<notes.size();i++) textNode(notes.get(i),4000,false,"$.notes["+i+"]",errors);
        return errors;
    }
    public Entry entry(JsonNode n,String path,List<ValidationError> errors) {
        int before=errors.size();
        fields(n,Set.of("id","lemma","russian","aspect","tense","reflexiveParticle","forms","note","source","studyGroup"),path,errors);
        if(n==null || !n.isObject()) return null;
        for(String key:List.of("id","lemma","russian","aspect","tense","studyGroup")) {
            int max=switch(key) { case "id","studyGroup"->100; case "russian"->1000; default->255; };
            textNode(n.get(key),max,true,path+"."+key,errors);
        }
        String id=string(n,"id"), lemma=string(n,"lemma"), particle=string(n,"reflexiveParticle");
        if(id!=null && !id.equals(id.strip())) error(errors,path+".id","External IDs cannot have surrounding whitespace");
        if(!ASPECTS.contains(Objects.toString(string(n,"aspect"),""))) error(errors,path+".aspect","Expected imperfective or perfective");
        if(!"aorist".equals(string(n,"tense"))) error(errors,path+".tense","Expected aorist");
        JsonNode particleNode=n.get("reflexiveParticle");
        if(particleNode!=null && !particleNode.isNull() && (particle==null || !Set.of("се","си").contains(particle)))
            error(errors,path+".reflexiveParticle","Expected null, се or си");
        textNode(n.get("note"),4000,false,path+".note",errors);
        Map<String,List<String>> forms=new LinkedHashMap<>();
        JsonNode f=n.get("forms"); fields(f,new HashSet<>(PERSONS),path+".forms",errors);
        for(String person:PERSONS) {
            JsonNode answers=f!=null && f.isObject()?f.get(person):null;
            String answerPath=path+".forms."+person;
            if(answers==null || !answers.isArray() || answers.isEmpty() || answers.size()>50) {
                error(errors,answerPath,"Expected 1–50 accepted answers"); continue;
            }
            Set<String> keys=new HashSet<>(); List<String> values=new ArrayList<>();
            for(int i=0;i<answers.size();i++) {
                JsonNode a=answers.get(i); textNode(a,255,true,answerPath+"["+i+"]",errors);
                if(!a.isString()) continue;
                String answer=a.asText(), key=AoristAnswerNormalizer.key(answer);
                if(key.length()>255 || !keys.add(key)) error(errors,answerPath+"["+i+"]","Duplicate or oversized normalized answer");
                if(particle!=null && Set.of("се","си").contains(particle) && !key.endsWith(" "+particle))
                    error(errors,answerPath+"["+i+"]","Isolated form must retain its final particle");
                values.add(answer);
            }
            forms.put(person,List.copyOf(values));
        }
        if(particle!=null && Set.of("се","си").contains(particle) && lemma!=null
                && !AoristAnswerNormalizer.key(lemma).endsWith(" "+particle))
            error(errors,path+".lemma","Lemma must retain its final particle");
        JsonNode source=n.get("source");
        fields(source,Set.of("title","url","section","checkedOn"),path+".source",errors);
        for(String key:List.of("title","url","section")) textNode(source==null?null:source.get(key),key.equals("url")?2048:1000,true,path+".source."+key,errors);
        date(string(source,"checkedOn"),path+".source.checkedOn",errors);
        String url=string(source,"url");
        if(url!=null) try {
            URI uri=URI.create(url);
            if(!Set.of("http","https").contains(Objects.toString(uri.getScheme(),"").toLowerCase(Locale.ROOT))
                    || uri.getHost()==null || uri.getUserInfo()!=null) throw new IllegalArgumentException();
        } catch(IllegalArgumentException e) { error(errors,path+".source.url","Expected an absolute HTTP(S) URL without credentials"); }
        if(errors.size()!=before) return null;
        return new Entry(id,lemma,string(n,"russian"),string(n,"aspect"),"aorist",particle,
                Collections.unmodifiableMap(forms),string(n,"note"),new Source(string(source,"title"),url,string(source,"section"),string(source,"checkedOn")),string(n,"studyGroup"));
    }
    public Entry requireEntry(JsonNode node) {
        List<ValidationError> errors=new ArrayList<>(); Entry entry=entry(node,"entry",errors);
        if(!errors.isEmpty()) throw new AoristException(400,"INVALID_AORIST_ENTRY",errors.getFirst().path()+": "+errors.getFirst().message());
        return entry;
    }
    public void requireMetadata(JsonNode node) {
        var errors=metadataErrors(node);
        if(!errors.isEmpty()) throw new AoristException(400,"INVALID_AORIST_METADATA",errors.getFirst().path()+": "+errors.getFirst().message());
    }
    public List<Warning> warnings(Entry e,String path) {
        // A supplied editorial inconsistency; never derive a paradigm or rewrite the group.
        if("вземам".equals(e.lemma()) && "2_ih".equals(e.studyGroup()) && e.forms().get("1sg").contains("вземах"))
            return List.of(new Warning(path+".studyGroup","GROUP_REVIEW","Supplied teaching label differs from the stored forms; preserved for editorial review"));
        return List.of();
    }
    static String string(JsonNode n,String field) {
        JsonNode value=n==null?null:n.get(field); return value!=null && value.isString()?value.asText():null;
    }
    static void fields(JsonNode n,Set<String> allowed,String path,List<ValidationError> errors) {
        if(n==null || !n.isObject()) { error(errors,path,"Expected an object"); return; }
        for(String key:n.propertyNames()) if(!allowed.contains(key)) error(errors,path+"."+key,"Unknown field");
    }
    static void error(List<ValidationError> errors,String path,String message) {
        errors.add(new ValidationError(path,"INVALID_FIELD",message));
    }
    private boolean positiveInteger(JsonNode n) { return n!=null && n.isIntegralNumber() && n.canConvertToInt() && n.intValue()>0; }
    private void constant(JsonNode n,String key,String expected,String code,List<ValidationError> errors) {
        if(!expected.equals(string(n,key))) errors.add(new ValidationError("$."+key,code,"Expected "+expected));
    }
    private void textNode(JsonNode n,int max,boolean nonblank,String path,List<ValidationError> errors) {
        if(n==null || !n.isString()) { error(errors,path,"Expected a string"); return; }
        String s=n.asText();
        if(s.length()>max || (nonblank && AoristAnswerNormalizer.key(s).isEmpty())
                || s.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\r' && c!='\n' && c!='\t'))
            error(errors,path,"Invalid text; maximum length "+max);
    }
    private void date(String value,String path,List<ValidationError> errors) {
        try { if(value==null || !value.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException(); LocalDate.parse(value); }
        catch(Exception e) { error(errors,path,"Expected an ISO calendar date"); }
    }
}
