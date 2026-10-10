package com.example.bulgariantrainer;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:sentence-tests;DB_CLOSE_DELAY=-1","training.typing.random-seed=42"})
@AutoConfigureMockMvc @ActiveProfiles("test")
class SentenceIntegrationTests {
 @Autowired MockMvc mvc;@Autowired ObjectMapper json;
 @Autowired SentenceRepository sentences;@Autowired SentenceAlternativeRepository alternatives;
 @Autowired SentenceWordRepository links;@Autowired SentenceQuestionRepository questions;
 @Autowired SentenceProgressRepository sentenceProgress;@Autowired SentenceAttemptWordRepository usage;
 @Autowired StudyAttemptRepository attempts;@Autowired StudySessionRepository sessions;
 @Autowired WordRepository words;@Autowired TranslationRepository translations;@Autowired StudyProgressRepository wordProgress;
 @BeforeEach void clean(){usage.deleteAllInBatch();questions.deleteAllInBatch();attempts.deleteAllInBatch();
  sentenceProgress.deleteAllInBatch();links.deleteAllInBatch();alternatives.deleteAllInBatch();sentences.deleteAllInBatch();
  wordProgress.deleteAllInBatch();translations.deleteAllInBatch();words.deleteAllInBatch();sessions.deleteAllInBatch();}
 @Test void suppliedCsvPreviewAndConfirmationAreControlledAndDuplicateSafe() throws Exception {
  byte[] file=Files.readAllBytes(Path.of("data/bulgarian_sentences_100.csv"));
  assertThat(file).startsWith((byte)0xEF,(byte)0xBB,(byte)0xBF);
  mvc.perform(post("/api/sentence-imports/preview-initial"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.entries.length()").value(101));
  JsonNode preview=preview(file);
  assertThat(preview.get("entries").size()).isEqualTo(101);
  var first=preview.at("/entries/1");
  assertThat(first.get("russianText").asText()).isEqualTo("Сколько стоит чёрный чай?");
  assertThat(first.get("alternativeTranslations").get(0).asText()).isEqualTo("Каква е цената на черния чай?");
  assertThat(first.get("status").asText()).isEqualTo("READY");
  assertThat(sentences.count()).isZero();
  long rowId=first.get("rowId").asLong();
  String body=json.writeValueAsString(Map.of("importId",preview.get("importId").asText(),"entries",List.of(
   Map.of("rowId",rowId,"selected",true,"sentence",request("Сколько стоит чёрный чай?","Колко струва черният чай?",List.of("Каква е цената на черния чай?"),List.of())))));
  var result=json.readTree(mvc.perform(post("/api/sentence-imports/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertThat(result.get("created").asInt()).isEqualTo(1);
  mvc.perform(post("/api/sentence-imports/confirm").contentType(MediaType.APPLICATION_JSON).content(body))
   .andExpect(status().isOk()).andExpect(jsonPath("$.created").value(1));
  assertThat(sentences.count()).isEqualTo(1);
  var repeated=preview(file);
  assertThat(repeated.at("/entries/1/status").asText()).isEqualTo("WARNING");
  assertThat(repeated.at("/entries/1/warnings/0").asText()).isEqualTo("DUPLICATE_IN_DATABASE");
  long id=sentences.findAll().getFirst().getId();
  var state=start(10);assertThat(state.get("totalQuestions").asInt()).isEqualTo(1);
  assertThat(state.at("/currentQuestion/russianText").asText()).contains("чёрный чай");
  assertThat(sentences.findById(id).orElseThrow().isApproved()).isTrue();
 }
 @Test void quotedCsvJsonAlternativesAndInvalidRowsRemainVisible() throws Exception {
  String csv="\uFEFFid,russian,bulgarian,alternativeTranslations,grammaticalExplanation,difficulty,topic\n"+
   "1,\"Дай, пожалуйста, чай.\",\"Дай ми, моля, чай.\",\"[\"\"Донеси ми чай, моля.\"\"]\",\"Обращение, просьба\",1,Кафе\n"+
   "2,Плохо,Лошо,\"[не JSON]\",,2,Тема\n";
  var p=preview(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  assertThat(p.at("/entries/1/russianText").asText()).isEqualTo("Дай, пожалуйста, чай.");
  assertThat(p.at("/entries/1/alternativeTranslations/0").asText()).isEqualTo("Донеси ми чай, моля.");
  assertThat(p.at("/entries/1/explanation").asText()).contains("Обращение, просьба");
  assertThat(p.at("/entries/2/status").asText()).isEqualTo("INVALID");
  assertThat(p.at("/entries/2/sourceLine").asText()).contains("не JSON");
  assertThat(sentences.count()).isZero();
 }
 @Test void managementAlternativesLinksArchiveAndEdit() throws Exception {
  Word word=word("чай","чай");
  var created=create("Я хочу чай.","Искам чай.",List.of("Желая чай."),List.of(word.getId()));
  long id=created.get("id").asLong();
  assertThat(created.at("/wordIds/0").asLong()).isEqualTo(word.getId());
  mvc.perform(post("/api/sentences").contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(request("Я ХОЧУ ЧАЙ.","Искам чай.",List.of(),List.of()))))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SENTENCE"));
  mvc.perform(patch("/api/sentences/"+id+"/archive").contentType(MediaType.APPLICATION_JSON)
    .content("{\"archived\":true}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.archived").value(true));
  assertThat(sentences.findTrainableIds()).isEmpty();
  mvc.perform(put("/api/sentences/"+id).contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(request("Я хочу чай.","Искам чай.",List.of("Искам чай, моля."),List.of(word.getId())))))
    .andExpect(status().isOk()).andExpect(jsonPath("$.alternativeTranslations[0]").value("Искам чай, моля."));
  assertThat(links.existsByWordId(word.getId())).isTrue();
  mvc.perform(delete("/api/words/"+word.getId())).andExpect(status().isConflict());
 }
 @Test void manualCorrectAndIncorrectAreDistinctFromAutomatic() throws Exception {
  create("Где вокзал?","Къде е гарата?",List.of(),List.of());
  for(String rating:List.of("CORRECT","INCORRECT")){
   var state=start(1);
   state=answer(state,"Къде се намира гарата?");
   assertThat(state.at("/currentQuestion/feedback/result").asText()).isEqualTo("PENDING");
   var evaluated=rate(state,rating);
   assertThat(evaluated.at("/currentQuestion/feedback/result").asText()).isEqualTo(rating);
   assertThat(evaluated.at("/currentQuestion/feedback/evaluationSource").asText()).isEqualTo("SELF");
  }
  var totals=sentenceProgress.findAll().getFirst();
  assertThat(totals.getTotalAttempts()).isEqualTo(2);
  assertThat(totals.getCorrectAttempts()).isEqualTo(1);
  assertThat(totals.getIncorrectAttempts()).isEqualTo(1);
 }
 @Test void unchangedAlternativeCanBeSavedAgain() throws Exception {
  var created=create("Дай чай.","Дай чай.",List.of("Донеси чай."),List.of());
  mvc.perform(put("/api/sentences/"+created.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(request("Дай чай.","Дай ми чай.",List.of("Донеси чай."),List.of()))))
   .andExpect(status().isOk()).andExpect(jsonPath("$.alternativeTranslations[0]").value("Донеси чай."));
 } @Test void approvedAlternativeAutomaticMatchAndSnapshotAfterEdit() throws Exception {
  var sentence=create("Принеси чай, пожалуйста.","Донеси чай, моля.",List.of("Моля, донеси чай."),List.of());
  var state=start(10);long id=state.get("id").asLong();
  assertThat(state.toString()).doesNotContain("Донеси чай", "acceptedAnswers", "explanation");
  mvc.perform(put("/api/sentences/"+sentence.get("id").asLong()).contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(request("Принеси чай, пожалуйста.","Нова версия.",List.of(),List.of()))))
    .andExpect(status().isOk());
  state=answer(state,"  МОЛЯ,   ДОНЕСИ\u00a0 ЧАЙ. ");
  assertThat(state.at("/currentQuestion/feedback/result").asText()).isEqualTo("CORRECT");
  assertThat(state.at("/currentQuestion/feedback/evaluationSource").asText()).isEqualTo("AUTOMATIC");
  assertThat(state.at("/currentQuestion/feedback/acceptedAnswers/1").asText()).isEqualTo("Моля, донеси чай.");
  assertThat(state.get("status").asText()).isEqualTo("COMPLETED");
  assertThat(read(id)).isEqualTo(state);
  assertThat(sentenceProgress.findBySentenceId(sentence.get("id").asLong()).orElseThrow().getCorrectAttempts()).isEqualTo(1);
  assertThat(attempts.count()).isEqualTo(1);
 }
 @Test void mismatchRequiresSelfAssessmentAndRecordsVocabularyUsageWithoutMastery() throws Exception {
  Word word=word("къща","дом");create("Это мой дом.","Това е моята къща.",List.of(),List.of(word.getId()));
  var state=start(10);long id=state.get("id").asLong();
  state=answer(state,"Това е моя дом.");
  assertThat(state.at("/currentQuestion/feedback/result").asText()).isEqualTo("PENDING");
  assertThat(state.at("/currentQuestion/feedback/needsSelfAssessment").asBoolean()).isTrue();
  assertThat(sentenceProgress.count()).isZero();
  assertThat(wordProgress.count()).isZero();
  assertThat(usage.countByWordId(word.getId())).isEqualTo(1);
  mvc.perform(post(path(id,"next")).contentType(MediaType.APPLICATION_JSON)
   .content("{\"questionId\":"+state.at("/currentQuestion/id").asLong()+"}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EVALUATION_REQUIRED"));
  var rated=rate(state,"PARTIAL");
  assertThat(rated.at("/currentQuestion/feedback/result").asText()).isEqualTo("PARTIAL");
  assertThat(rated.at("/currentQuestion/feedback/evaluationSource").asText()).isEqualTo("SELF");
  assertThat(rate(state,"PARTIAL")).isEqualTo(rated);
  assertThat(sentenceProgress.findAll().getFirst().getPartialAttempts()).isEqualTo(1);
  assertThat(wordProgress.count()).isZero();
  mvc.perform(post(path(id,"self-rating")).contentType(MediaType.APPLICATION_JSON)
   .content("{\"questionId\":"+state.at("/currentQuestion/id").asLong()+",\"result\":\"CORRECT\"}"))
   .andExpect(status().isConflict());
  var result=json.readTree(mvc.perform(get(path(id,"results"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertThat(result.at("/items/0/userAnswer").asText()).isEqualTo("Това е моя дом.");
 }
 @Test void emptyCollectionUniqueSessionQuestionsAndEarlyCompletion() throws Exception {
  mvc.perform(post("/api/sentence-practice/sessions").contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(Map.of("requestId",UUID.randomUUID()))))
    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMPTY_SENTENCES"));
  for(int i=0;i<12;i++)create("Русское предложение "+i,"Българско изречение "+i,List.of(),List.of());
  var state=start(10);long id=state.get("id").asLong();
  var queue=questions.findBySessionIdOrderByPositionAsc(id);
  assertThat(queue).hasSize(10);
  assertThat(queue.stream().map(q->q.getSentence().getId())).doesNotHaveDuplicates();
  var answered=answer(state,"Другой вариант.");
  var ended=json.readTree(mvc.perform(post(path(id,"complete"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
  assertThat(ended.get("status").asText()).isEqualTo("ABANDONED");
  assertThat(read(id)).isEqualTo(ended);
  assertThat(sentenceProgress.count()).isZero();
  mvc.perform(post(path(id,"answers")).contentType(MediaType.APPLICATION_JSON)
    .content(json.writeValueAsString(Map.of("questionId",queue.get(1).getId(),"answer","test"))))
    .andExpect(status().isConflict());
 }
 @Test void reviewVocabularySentenceIsPrioritizedWhenThereAreOtherChoices() throws Exception {
  for(int i=0;i<8;i++)create("Русское предложение "+i,"Българско изречение "+i,List.of(),List.of());
  Word word=word("билет","билет");
  create("Мне нужен билет.","Трябва ми билет.",List.of(),List.of(word.getId()));
  StudyProgress due=new StudyProgress(word,TrainingMode.FLASHCARD);
  due.recordAnswer(false,java.time.Instant.now().minus(java.time.Duration.ofDays(2)),1);wordProgress.saveAndFlush(due);
  var state=start(1);
  assertThat(state.at("/currentQuestion/russianText").asText()).isEqualTo("Мне нужен билет.");
 }
 private JsonNode preview(byte[] bytes)throws Exception{
  MockMultipartFile file=new MockMultipartFile("file","bulgarian_sentences_100.csv","text/csv",bytes);
  return json.readTree(mvc.perform(multipart("/api/sentence-imports/preview").file(file)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 private Map<String,Object> request(String ru,String bg,List<String> alternatives,List<Long> ids){
  Map<String,Object> data=new HashMap<>();data.put("russianText",ru);data.put("bulgarianText",bg);
  data.put("alternativeTranslations",alternatives);data.put("explanation","Грамматическое пояснение");
  data.put("difficulty",1);data.put("topic","Практика");data.put("wordIds",ids);return data;
 }
 private JsonNode create(String ru,String bg,List<String> alternatives,List<Long> ids)throws Exception{
  return json.readTree(mvc.perform(post("/api/sentences").contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(request(ru,bg,alternatives,ids))))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
 }
 private JsonNode start(int size)throws Exception{
  return json.readTree(mvc.perform(post("/api/sentence-practice/sessions").contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("requestId",UUID.randomUUID(),"size",size))))
   .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
 }
 private JsonNode answer(JsonNode state,String answer)throws Exception{
  return json.readTree(mvc.perform(post(path(state.get("id").asLong(),"answers")).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("questionId",state.at("/currentQuestion/id").asLong(),"answer",answer))))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 private JsonNode rate(JsonNode state,String rating)throws Exception{
  return json.readTree(mvc.perform(post(path(state.get("id").asLong(),"self-rating")).contentType(MediaType.APPLICATION_JSON)
   .content(json.writeValueAsString(Map.of("questionId",state.at("/currentQuestion/id").asLong(),"result",rating))))
   .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
 }
 private JsonNode read(long id)throws Exception{return json.readTree(mvc.perform(get(path(id,"question"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
 private String path(long id,String action){return "/api/sentence-practice/sessions/"+id+"/"+action;}
 private Word word(String bg,String ru){Word word=new Word(bg,bg,null,null,null);word.addTranslation(ru,ru,true);return words.saveAndFlush(word);}
}


