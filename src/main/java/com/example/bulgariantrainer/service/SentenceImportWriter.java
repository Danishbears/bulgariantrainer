package com.example.bulgariantrainer.service;
import com.example.bulgariantrainer.dto.SentenceImportDtos.*;
import com.example.bulgariantrainer.dto.SentenceDtos;
import com.example.bulgariantrainer.exception.ImportException;
import com.example.bulgariantrainer.repository.SentenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class SentenceImportWriter {
 private final SentenceService service;private final SentenceRepository sentences;
 public SentenceImportWriter(SentenceService service,SentenceRepository sentences){this.service=service;this.sentences=sentences;}
 @Transactional
 public Summary save(Preview preview,ConfirmRequest request){
  if(request.entries()==null)throw invalid("Entries are required");
  Map<Integer,Entry> source=new HashMap<>();for(Entry e:preview.entries())source.put(e.rowId(),e);
  Map<Integer,ConfirmEntry> choices=new HashMap<>();
  for(ConfirmEntry choice:request.entries()){
   if(!source.containsKey(choice.rowId())||choices.putIfAbsent(choice.rowId(),choice)!=null)throw invalid("Unknown or repeated row ID");
   if(choice.selected()&&(choice.sentence()==null||source.get(choice.rowId()).status().equals("HEADER")||source.get(choice.rowId()).status().equals("EMPTY")))
    throw invalid("Selected row needs a corrected sentence");
  }
  int created=0,duplicates=0,deselected=0;List<RowResult> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
  for(Entry entry:preview.entries()){
   ConfirmEntry choice=choices.get(entry.rowId());
   if(choice==null||!choice.selected()){deselected++;rows.add(new RowResult(entry.rowId(),"DESELECTED",null));continue;}
   SentenceDtos.Request corrected=choice.sentence();
   if(corrected.russianText()==null||corrected.russianText().isBlank())throw invalid("Selected row needs Russian text");
   String key=VocabularyText.key(corrected.russianText());
   var existing=sentences.findByNormalizedRussian(key);
   if(existing.isPresent()||!seen.add(key)){
    duplicates++;rows.add(new RowResult(entry.rowId(),"DUPLICATE_SKIPPED",existing.map(s->s.getId()).orElse(null)));continue;
   }
   var saved=service.create(corrected);created++;rows.add(new RowResult(entry.rowId(),"CREATED",saved.id()));
  }
  return new Summary(preview.importId(),created,duplicates,deselected,List.copyOf(rows));
 }
 private ImportException invalid(String message){return new ImportException(400,"INVALID_CONFIRMATION",message);}
}
