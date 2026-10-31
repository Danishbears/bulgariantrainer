package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.*;
import java.nio.charset.*;
import java.time.LocalDate;
import java.util.*;

@Component
public class AspectPairImportParser {
    public static final int MAX_BYTES=1024*1024;
    private final AspectPairJson json;
    private final AspectPairValidation validation;
    public AspectPairImportParser(AspectPairJson json,AspectPairValidation validation){this.json=json;this.validation=validation;}
    public record Parsed(JsonNode metadata,List<Entry> entries,List<ValidationError> errors) {}
    public Parsed parse(byte[] bytes){
        List<ValidationError> errors=new ArrayList<>();
        if(bytes.length>MAX_BYTES)return failed("$","FILE_TOO_LARGE","Maximum file size is 1 MiB");
        String text;
        try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException e){return failed("$","INVALID_UTF8","File must be valid UTF-8");}
        if(text.startsWith("\uFEFF"))text=text.substring(1);
        JsonNode root;
        try{root=json.tree(text);}catch(Exception e){return failed("$","INVALID_JSON","Malformed JSON, duplicate object keys or trailing content");}
        if(root==null || !root.isObject())return failed("$","INVALID_DOCUMENT","Expected a JSON object");
        fields(root,Set.of("format","formatVersion","language","translationLanguage","entryForm","createdOn","sources","editorialStatus","entries"),"$",errors);
        if(!"bulgariantrainer.aspect-pairs".equals(string(root,"format")))
            errors.add(new ValidationError("$.format","UNSUPPORTED_FORMAT","Expected bulgariantrainer.aspect-pairs"));
        JsonNode version=root.get("formatVersion");
        if(version==null || !version.isIntegralNumber() || !version.canConvertToInt() || version.intValue()!=1)
            errors.add(new ValidationError("$.formatVersion","UNSUPPORTED_FORMAT_VERSION","Only integer formatVersion 1 is supported"));
        optionalConstant(root,"language","bg",errors); optionalConstant(root,"translationLanguage","ru",errors);
        optionalConstant(root,"entryForm","1st_person_singular_present_citation_form",errors);
        if(root.has("createdOn"))try{LocalDate.parse(requiredString(root,"createdOn","$",errors));}catch(Exception e){AspectPairValidation.error(errors,"$.createdOn","Expected an ISO date");}
        if(root.has("editorialStatus"))requiredString(root,"editorialStatus","$",errors);
        Set<String> sources=new HashSet<>();
        if(root.has("sources")){
            JsonNode array=root.get("sources");
            if(!array.isArray())AspectPairValidation.error(errors,"$.sources","Expected an array");
            else for(int i=0;i<array.size();i++){
                JsonNode s=array.get(i);String path="$.sources["+i+"]";
                fields(s,Set.of("id","title","url","accessedOn","extractedTableRows"),path,errors);
                String id=requiredString(s,"id",path,errors);
                if(id==null || id.isBlank() || !sources.add(id))AspectPairValidation.error(errors,path+".id","Source IDs must be unique nonblank strings");
                for(String key:List.of("title","url","accessedOn"))if(s.has(key))requiredString(s,key,path,errors);
                if(s.has("extractedTableRows") && (!s.get("extractedTableRows").isIntegralNumber() || !s.get("extractedTableRows").canConvertToInt() || s.get("extractedTableRows").intValue()<1))
                    AspectPairValidation.error(errors,path+".extractedTableRows","Expected a positive integer");
            }
        }
        List<Entry> entries=new ArrayList<>();Set<String> ids=new HashSet<>();
        JsonNode array=root.get("entries");
        if(array==null || !array.isArray() || array.isEmpty() || array.size()>1000)
            AspectPairValidation.error(errors,"$.entries","Expected 1–1000 entries");
        else for(int i=0;i<array.size();i++){
            JsonNode n=array.get(i);String path="$.entries["+i+"]";int previous=errors.size();
            fields(n,Set.of("id","imperfective","perfective","russianImperfective","russianPerfective","acceptedImperfective","acceptedPerfective","topic","studyStage","relationType","note","provenance","examples"),path,errors);
            for(String key:List.of("id","imperfective","perfective","russianImperfective","russianPerfective","topic","relationType","note"))requiredString(n,key,path,errors);
            String id=string(n,"id");
            if(id!=null && !ids.add(id))errors.add(new ValidationError(path+".id","DUPLICATE_EXTERNAL_ID","Repeated external ID: "+id));
            JsonNode stage=n.get("studyStage");
            if(stage==null || !stage.isIntegralNumber() || !stage.canConvertToInt())AspectPairValidation.error(errors,path+".studyStage","Expected an integer");
            for(String key:List.of("acceptedImperfective","acceptedPerfective")){
                JsonNode a=n.get(key);
                if(a==null || !a.isArray())AspectPairValidation.error(errors,path+"."+key,"Expected an array");
                else for(int aIndex=0;aIndex<a.size();aIndex++)if(!a.get(aIndex).isString())AspectPairValidation.error(errors,path+"."+key+"["+aIndex+"]","Expected a string");
            }
            JsonNode p=n.get("provenance");fields(p,Set.of("kind","sourceId","tableRow"),path+".provenance",errors);
            requiredString(p,"kind",path+".provenance",errors);
            if(p!=null && p.has("sourceId") && !p.get("sourceId").isNull())requiredString(p,"sourceId",path+".provenance",errors);
            if(p!=null && p.has("tableRow") && !p.get("tableRow").isNull() && (!p.get("tableRow").isIntegralNumber() || !p.get("tableRow").canConvertToInt()))
                AspectPairValidation.error(errors,path+".provenance.tableRow","Expected an integer");
            if(n.has("examples") && !n.get("examples").isNull()){
                JsonNode examples=n.get("examples"); fields(examples,Set.of("imperfective","perfective"),path+".examples",errors);
                for(String side:List.of("imperfective","perfective")){
                    JsonNode example=examples.get(side);fields(example,Set.of("bg","ru"),path+".examples."+side,errors);
                    requiredString(example,"bg",path+".examples."+side,errors);requiredString(example,"ru",path+".examples."+side,errors);
                }
            }
            if(errors.size()==previous)try{
                Entry e=json.convert(n,Entry.class);errors.addAll(validation.errors(e,path));
                if("presentation".equals(e.provenance().kind()) && !sources.contains(e.provenance().sourceId()))AspectPairValidation.error(errors,path+".provenance.sourceId","Unknown source reference");
                entries.add(e);
            }catch(Exception e){AspectPairValidation.error(errors,path,"Invalid entry structure");}
        }
        ObjectNode metadata=((ObjectNode)root).deepCopy();metadata.remove("entries");
        return new Parsed(metadata,List.copyOf(entries),List.copyOf(errors));
    }
    private Parsed failed(String path,String code,String message){return new Parsed(null,List.of(),List.of(new ValidationError(path,code,message)));}
    private void fields(JsonNode node,Set<String> allowed,String path,List<ValidationError> errors){
        if(node==null || !node.isObject()){AspectPairValidation.error(errors,path,"Expected an object");return;}
        for(String key:node.propertyNames())if(!allowed.contains(key))AspectPairValidation.error(errors,path+"."+key,"Unknown field");
    }
    private String string(JsonNode node,String field){JsonNode n=node==null?null:node.get(field);return n!=null && n.isString()?n.asText():null;}
    private String requiredString(JsonNode node,String field,String path,List<ValidationError> errors){
        String s=string(node,field);if(s==null)AspectPairValidation.error(errors,path+"."+field,"Expected a string");return s;
    }
    private void optionalConstant(JsonNode node,String field,String expected,List<ValidationError> errors){
        if(node.has(field) && !expected.equals(string(node,field)))AspectPairValidation.error(errors,"$."+field,"Expected "+expected);
    }
}
