package com.example.bulgariantrainer.service;
import com.example.bulgariantrainer.dto.SentenceImportDtos.*;
import com.example.bulgariantrainer.exception.ImportException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
@Component
public class SentenceImportParser {
 public static final int MAX_BYTES=1024*1024;
 private static final List<String> HEADER=List.of("id","russian","bulgarian","alternativeTranslations","grammaticalExplanation","difficulty","topic");
 private final VocabularyImportParser csv; private final ObjectMapper json;
 public SentenceImportParser(VocabularyImportParser csv,ObjectMapper json){this.csv=csv;this.json=json;}
 public List<Entry> parse(String filename,byte[] bytes){
  if(!filename.toLowerCase(Locale.ROOT).endsWith(".csv"))throw new ImportException(415,"UNSUPPORTED_FILE","Use a CSV file");
  if(bytes.length>MAX_BYTES)throw new ImportException(413,"FILE_TOO_LARGE","Maximum file size is 1 MiB");
  String text;
  try{text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
  catch(CharacterCodingException error){throw new ImportException(400,"INVALID_UTF8","The file must be UTF-8");}
  if(text.startsWith("\uFEFF"))text=text.substring(1);
  List<VocabularyImportParser.CsvRecord> rows=csv.readCsv(text);
  if(rows.isEmpty()||rows.getFirst().invalid()||!rows.getFirst().fields().equals(HEADER))
   throw new ImportException(400,"INVALID_HEADER","Sentence CSV header does not match the expected seven columns");
  List<Entry> entries=new ArrayList<>();int index=0;
  for(var row:rows){
   int rowId=++index;
   if(rowId==1){entries.add(new Entry(rowId,row.startLine(),row.endLine(),row.source(),"","","",List.of(),null,null,null,"HEADER",List.of(),null));continue;}
   if(row.source().isBlank()){entries.add(new Entry(rowId,row.startLine(),row.endLine(),row.source(),"","","",List.of(),null,null,null,"EMPTY",List.of("EMPTY_RECORD"),null));continue;}
   List<String> warnings=new ArrayList<>();
   if(row.invalid())warnings.add("CSV_SYNTAX");
   if(row.fields().size()!=7)warnings.add("CSV_COLUMN_COUNT");
   List<String> fields=row.fields();
   String sourceId=fields.isEmpty()?"":fields.get(0);
   String ru=fields.size()>1?fields.get(1):"",bg=fields.size()>2?fields.get(2):"";
   String explanation=fields.size()>4?fields.get(4):"",topic=fields.size()>6?fields.get(6):"";
   List<String> alternatives=List.of(); Integer difficulty=null;
   if(fields.size()==7){
    try{
     JsonNode array=json.readTree(fields.get(3));
     if(!array.isArray())throw new IllegalArgumentException();
     List<String> parsed=new ArrayList<>();
     for(JsonNode item:array){if(!item.isString())throw new IllegalArgumentException();parsed.add(item.asText());}
     alternatives=List.copyOf(parsed);
    }catch(Exception error){warnings.add("INVALID_ALTERNATIVES_JSON");}
    try{difficulty=Integer.valueOf(fields.get(5));if(difficulty<1||difficulty>5)warnings.add("INVALID_DIFFICULTY");}
    catch(NumberFormatException error){warnings.add("INVALID_DIFFICULTY");}
   }
   if(ru.isBlank()||bg.isBlank())warnings.add("MISSING_TEXT");
   if(sourceId.isBlank())warnings.add("MISSING_SOURCE_ID");
   if(ru.length()>4000||bg.length()>4000||explanation.length()>4000||topic.length()>255||alternatives.size()>50||alternatives.stream().anyMatch(a->a.isBlank()||a.length()>4000))warnings.add("VALUE_TOO_LONG");
   String status=warnings.isEmpty()?"READY":"INVALID";
   entries.add(new Entry(rowId,row.startLine(),row.endLine(),row.source(),sourceId,ru,bg,alternatives,
     explanation.isBlank()?null:explanation,difficulty,topic.isBlank()?null:topic,status,List.copyOf(warnings),null));
  }
  return entries;
 }
}
