package com.example.bulgariantrainer.service;
import com.example.bulgariantrainer.dto.SentenceImportDtos.*;
import com.example.bulgariantrainer.dto.SentenceDtos;
import com.example.bulgariantrainer.exception.ImportException;
import com.example.bulgariantrainer.repository.SentenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
@Service
public class SentenceImportService {
 private final SentenceImportParser parser;private final SentenceRepository sentences;private final SentenceImportWriter writer;
 private final TransactionTemplate readTransaction;private final Map<UUID,Pending> pending=new LinkedHashMap<>();
 public SentenceImportService(SentenceImportParser parser,SentenceRepository sentences,SentenceImportWriter writer,PlatformTransactionManager transactions){
  this.parser=parser;this.sentences=sentences;this.writer=writer;
  readTransaction=new TransactionTemplate(transactions);readTransaction.setReadOnly(true);
 }
 public Preview preview(MultipartFile file){
  String filename=Optional.ofNullable(file.getOriginalFilename()).orElse("").replace('\\','/');
  filename=filename.substring(filename.lastIndexOf('/')+1);
  if(file.getSize()>SentenceImportParser.MAX_BYTES)throw new ImportException(413,"FILE_TOO_LARGE","Maximum file size is 1 MiB");
  byte[] bytes;
  try(var stream=file.getInputStream()){bytes=stream.readNBytes(SentenceImportParser.MAX_BYTES+1);}
  catch(IOException error){throw new ImportException(400,"INVALID_UPLOAD","Cannot read uploaded file");}
  return previewBytes(filename,bytes);
 }
 public Preview previewInitial(){
  Path path=Path.of("data","bulgarian_sentences_100.csv");
  if(!Files.isRegularFile(path))throw new ImportException(404,"SOURCE_FILE_MISSING","The project sentence CSV is missing");
  try(var stream=Files.newInputStream(path)){
   return previewBytes(path.getFileName().toString(),stream.readNBytes(SentenceImportParser.MAX_BYTES+1));
  }catch(IOException error){throw new ImportException(400,"INVALID_UPLOAD","Cannot read the project sentence CSV");}
 }
 private Preview previewBytes(String filename,byte[] bytes){
  List<Entry> parsed=parser.parse(filename,bytes);
  List<Entry> entries=readTransaction.execute(status->enrich(parsed));
  Preview preview=new Preview(UUID.randomUUID(),filename,Instant.now().plus(Duration.ofMinutes(30)),entries);
  synchronized(pending){pending.values().removeIf(p->p.preview.expiresAt().isBefore(Instant.now()));
   while(pending.size()>=5)pending.remove(pending.keySet().iterator().next());
   pending.put(preview.importId(),new Pending(preview));}
  return preview;
 }
 private List<Entry> enrich(List<Entry> parsed){
  Map<String,Integer> first=new HashMap<>();List<Entry> entries=new ArrayList<>();
  for(Entry entry:parsed){
   List<String> warnings=new ArrayList<>(entry.warnings());Long existing=null;
   if(!entry.russianText().isBlank()){
    String key=VocabularyText.key(entry.russianText());
    Integer earlier=first.putIfAbsent(key,entry.rowId());
    if(earlier!=null)warnings.add("DUPLICATE_IN_FILE");
    existing=sentences.findByNormalizedRussian(key).map(s->s.getId()).orElse(null);
    if(existing!=null)warnings.add("DUPLICATE_IN_DATABASE");
   }
   String status=entry.status().equals("INVALID")||entry.status().equals("HEADER")||entry.status().equals("EMPTY")?entry.status()
     :warnings.isEmpty()?"READY":"WARNING";
   entries.add(new Entry(entry.rowId(),entry.lineNumber(),entry.endLineNumber(),entry.sourceLine(),entry.sourceId(),
    entry.russianText(),entry.bulgarianText(),entry.alternativeTranslations(),entry.explanation(),entry.difficulty(),
    entry.topic(),status,List.copyOf(warnings),existing));
  }
  return List.copyOf(entries);
 }
 public Summary confirm(ConfirmRequest request){
  Pending item;
  synchronized(pending){item=pending.get(request.importId());}
  if(item==null||!item.preview.expiresAt().isAfter(Instant.now()))throw new ImportException(410,"PREVIEW_EXPIRED","Preview expired; upload again");
  synchronized(item){
   if(item.summary!=null){if(item.confirmed.equals(request))return item.summary;
    throw new ImportException(409,"IMPORT_ALREADY_CONFIRMED","Preview already confirmed");}
   Summary result=writer.save(item.preview,request);
   item.confirmed=new ConfirmRequest(request.importId(),List.copyOf(request.entries()));item.summary=result;return result;
  }
 }
 private static final class Pending{final Preview preview;ConfirmRequest confirmed;Summary summary;Pending(Preview p){preview=p;}}
}
