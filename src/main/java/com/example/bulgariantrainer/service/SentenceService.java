package com.example.bulgariantrainer.service;
import com.example.bulgariantrainer.dto.*;
import com.example.bulgariantrainer.dto.SentenceDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.SentenceException;
import com.example.bulgariantrainer.repository.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
@Transactional(readOnly=true)
public class SentenceService {
 private final SentenceRepository sentences; private final SentenceWordRepository links; private final WordRepository words;
 public SentenceService(SentenceRepository sentences,SentenceWordRepository links,WordRepository words){
  this.sentences=sentences;this.links=links;this.words=words;
 }
 public PageResponse<Response> list(boolean archived,int page,int size){
  if(page<0||size<1||size>100)throw invalid("Invalid page or size");
  Page<Sentence> result=sentences.findByArchived(archived,PageRequest.of(page,size,Sort.by("id")));
  return new PageResponse<>(result.getContent().stream().map(this::response).toList(),page,size,result.getTotalElements(),result.getTotalPages());
 }
 public Response get(long id){return response(sentences.findById(id).orElseThrow(()->missing()));}
 @Transactional public Response create(Request request){
  Validated valid=validate(request);
  if(sentences.findByNormalizedRussian(valid.key()).isPresent())throw duplicate();
  Sentence sentence=new Sentence(valid.bg(),valid.ru(),valid.key(),valid.explanation(),request.difficulty(),valid.topic());
  sentence.replaceAlternatives(valid.alternatives(),valid.alternativeKeys());
  sentences.saveAndFlush(sentence); link(sentence,valid.wordIds());
  return response(sentence);
 }
 @Transactional public Response update(long id,Request request){
  Sentence sentence=sentences.findForUpdate(id).orElseThrow(()->missing());
  Validated valid=validate(request);
  if(sentences.existsByNormalizedRussianAndIdNot(valid.key(),id))throw duplicate();
  sentence.update(valid.bg(),valid.ru(),valid.key(),valid.explanation(),request.difficulty(),valid.topic());
  sentence.replaceAlternatives(valid.alternatives(),valid.alternativeKeys());
  links.deleteBySentenceId(id);links.flush();link(sentence,valid.wordIds());
  sentences.flush(); return response(sentence);
 }
 @Transactional public Response archive(long id,boolean archived){
  Sentence sentence=sentences.findForUpdate(id).orElseThrow(()->missing());sentence.setArchived(archived);sentences.flush();return response(sentence);
 }
 private void link(Sentence sentence,List<Long> ids){
  for(Long id:ids){Word word=words.findForUpdate(id).orElseThrow(()->invalid("Linked word does not exist"));links.save(new SentenceWord(sentence,word));}
  links.flush();
 }
 private Validated validate(Request request){
  if(request==null||request.alternativeTranslations()==null||request.wordIds()==null)throw invalid("Sentence fields are required");
  String ru=text(request.russianText(),4000),bg=text(request.bulgarianText(),4000);
  String explanation=optional(request.explanation(),4000),topic=optional(request.topic(),255);
  if(request.difficulty()!=null&&(request.difficulty()<1||request.difficulty()>5))throw invalid("Difficulty must be 1 to 5");
  if(request.alternativeTranslations().size()>50)throw invalid("At most 50 alternative translations are allowed");
  Set<String> keys=new HashSet<>();keys.add(VocabularyText.key(bg));
  List<String> alternatives=new ArrayList<>(),alternativeKeys=new ArrayList<>();
  for(String candidate:request.alternativeTranslations()){
   String value=text(candidate,4000),key=VocabularyText.key(value);
   if(!keys.add(key))throw invalid("Duplicate Bulgarian translation");
   alternatives.add(value);alternativeKeys.add(key);
  }
  Set<Long> unique=new HashSet<>();
  for(Long id:request.wordIds())if(id==null||id<1||!unique.add(id))throw invalid("Invalid or duplicate linked word");
  return new Validated(ru,bg,VocabularyText.key(ru),explanation,topic,alternatives,alternativeKeys,request.wordIds());
 }
 private String text(String value,int max){
  if(value==null)throw invalid("Required sentence text is missing");
  String clean=VocabularyText.clean(value);
  if(clean.isEmpty()||clean.length()>max)throw invalid("Sentence text is empty or too long");
  return clean;
 }
 private String optional(String value,int max){
  if(value==null||value.isBlank())return null;
  String clean=VocabularyText.clean(value);
  if(clean.length()>max)throw invalid("Optional sentence text is too long");return clean;
 }
 private Response response(Sentence sentence){
  return new Response(sentence.getId(),sentence.getRussianText(),sentence.getBulgarianText(),
   sentence.getAlternatives().stream().map(SentenceAlternative::getBulgarianText).toList(),
   sentence.getExplanation(),sentence.getDifficulty(),sentence.getTopic(),
   links.findBySentenceId(sentence.getId()).stream().map(l->l.getWord().getId()).sorted().toList(),
   sentence.isArchived(),sentence.isApproved());
 }
 private SentenceException missing(){return new SentenceException(404,"SENTENCE_NOT_FOUND","Sentence not found");}
 private SentenceException duplicate(){return new SentenceException(409,"DUPLICATE_SENTENCE","Russian sentence already exists");}
 private SentenceException invalid(String message){return new SentenceException(400,"INVALID_SENTENCE",message);}
 private record Validated(String ru,String bg,String key,String explanation,String topic,
     List<String> alternatives,List<String> alternativeKeys,List<Long> wordIds){}
}
