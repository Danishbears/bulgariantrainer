package com.example.bulgariantrainer.service;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

@Component
public class AoristImportParser {
    public static final int MAX_BYTES=1024*1024;
    private final AoristJson json;
    private final AoristValidation validation;
    public AoristImportParser(AoristJson json,AoristValidation validation) { this.json=json; this.validation=validation; }
    public record Parsed(JsonNode metadata,List<Entry> entries,List<ValidationError> errors,List<Warning> warnings) {}
    public Parsed parse(byte[] bytes) {
        if(bytes.length>MAX_BYTES) return failed("FILE_TOO_LARGE","Maximum file size is 1 MiB");
        String text;
        try { text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException e) { return failed("INVALID_UTF8","File must be valid UTF-8"); }
        if(text.startsWith("\uFEFF")) text=text.substring(1);
        JsonNode root;
        try { root=json.tree(text); } catch(Exception e) { return failed("INVALID_JSON","Malformed JSON, duplicate object keys or trailing content"); }
        if(root==null || !root.isObject()) return failed("INVALID_DOCUMENT","Expected a JSON object");
        ObjectNode metadata=((ObjectNode)root).deepCopy(); metadata.remove("entries");
        List<ValidationError> errors=new ArrayList<>(validation.metadataErrors(metadata));
        List<Entry> entries=new ArrayList<>(); List<Warning> warnings=new ArrayList<>(); Set<String> ids=new HashSet<>();
        JsonNode array=root.get("entries");
        if(array==null || !array.isArray() || array.isEmpty() || array.size()>1000)
            AoristValidation.error(errors,"$.entries","Expected 1–1000 entries");
        else for(int i=0;i<array.size();i++) {
            String path="$.entries["+i+"]"; JsonNode node=array.get(i);
            String id=AoristValidation.string(node,"id");
            if(id!=null && !ids.add(id)) errors.add(new ValidationError(path+".id","DUPLICATE_EXTERNAL_ID","Repeated external ID: "+id));
            Entry entry=validation.entry(node,path,errors);
            if(entry!=null) { entries.add(entry); warnings.addAll(validation.warnings(entry,path)); }
        }
        return new Parsed(metadata,List.copyOf(entries),List.copyOf(errors),List.copyOf(warnings));
    }
    private Parsed failed(String code,String message) { return new Parsed(null,List.of(),List.of(new ValidationError("$",code,message)),List.of()); }
}
