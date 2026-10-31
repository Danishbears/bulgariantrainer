package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AspectPairDtos.*;
import com.example.bulgariantrainer.service.*;
import com.example.bulgariantrainer.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:aspect-tests;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("test")
class AspectPairIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AspectPairRepository pairs;
    @Autowired AspectPairImportService imports;
    @Autowired AspectPairImportWriter writer;
    @Autowired AspectPairImportParser parser;
    @Autowired AspectPracticeService practice;
    @Autowired AspectDatasetRepository datasets;
    @Autowired AspectPairService service;
    @Autowired AspectPairValidation validation;
    @Autowired AspectPairJson aspectJson;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    private static final String NAMESPACE="bulgarian-aspect-pairs-100";
    private static final Path SOURCE=Path.of("learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json");

    @BeforeEach void clean(){
        for(String table:List.of("aspect_attempts","aspect_questions","aspect_sessions","aspect_import_receipts","aspect_progress","aspect_pair_answers","aspect_pairs","aspect_datasets"))jdbc.update("DELETE FROM "+table);
    }
    @Test void completeDatasetRoundTripsEveryLearningFieldAndRepeatImportIsIdempotent() throws Exception {
        byte[] bytes=Files.readAllBytes(SOURCE);
        JsonNode legacyStatistics=getJson("/api/statistics");
        JsonNode preview=preview(bytes,NAMESPACE);
        assertThat(preview.path("valid").asBoolean()).isTrue();
        assertThat(preview.path("entries").size()).isEqualTo(100);
        assertThat(pairs.count()).isZero();assertThat(count("aspect_datasets")).isZero();
        String confirm=confirmation(preview,true);
        JsonNode summary=postJson("/api/aspect-pair-imports/confirm",confirm,200);
        assertThat(summary.path("created").asInt()).isEqualTo(100);
        assertThat(postJson("/api/aspect-pair-imports/confirm",confirm,200)).isEqualTo(summary);
        assertThat(count("aspect_pair_answers")).isEqualTo(212);
        assertThat(count("aspect_progress")).isZero();
        var source=json.readTree(bytes);Map<String,JsonNode> originals=new HashMap<>();
        for(JsonNode entry:source.path("entries"))originals.put(entry.path("id").asText(),entry);
        var list=getJson("/api/aspect-pairs?size=100&namespace="+NAMESPACE);
        for(JsonNode response:list.path("content")){
            JsonNode entry=response.path("entry"),original=originals.get(entry.path("id").asText());
            for(String field:original.propertyNames())assertThat(entry.get(field)).as(entry.path("id")+"."+field).isEqualTo(original.get(field));
            assertThat(response.path("id").isIntegralNumber()).isTrue();
        }
        JsonNode dataset=getJson("/api/aspect-pairs/datasets").get(0);
        assertThat(dataset.path("metadata").path("formatVersion").asInt()).isEqualTo(1);
        assertThat(dataset.path("contentRevision").asText()).matches("[0-9a-f]{64}");
        assertThat(dataset.path("metadata").get("sources")).isEqualTo(source.get("sources"));
        JsonNode repeated=preview(bytes,NAMESPACE);
        for(JsonNode row:repeated.path("entries"))assertThat(row.path("classification").asText()).isEqualTo("UNCHANGED");
        var repeatSummary=postJson("/api/aspect-pair-imports/confirm",confirmation(repeated,true),200);
        assertThat(repeatSummary.path("created").asInt()).isZero();
        assertThat(repeatSummary.path("unchanged").asInt()).isEqualTo(100);
        assertThat(pairs.count()).isEqualTo(100);
        assertThat(count("aspect_import_receipts")).isEqualTo(2);
        assertThat(getJson("/api/statistics")).isEqualTo(legacyStatistics);
    }
    @Test void editingArchivingAndSelectedImportUpdatePreserveIdentityAndLearningHistory() throws Exception {
        var created=create("manual",entry("custom-1"));long id=created.path("id").asLong();
        jdbc.update("INSERT INTO aspect_progress(pair_id,direction,total_attempts,correct_attempts,successful_review_count,consecutive_correct,repetition_interval,next_review_at) VALUES (?, 'IMPERFECTIVE_TO_PERFECTIVE',8,6,6,2,3,CURRENT_TIMESTAMP)",id);
        var history=jdbc.queryForMap("SELECT * FROM aspect_progress WHERE pair_id=?",id);
        ObjectNode changed=(ObjectNode)created.path("entry").deepCopy();changed.put("note","Новое пояснение");
        var edited=postJsonMethod("PUT","/api/aspect-pairs/"+id,json.writeValueAsString(Map.of("namespace","manual","version",created.path("version").asLong(),"entry",changed)),200);
        assertThat(edited.path("createdAt")).isEqualTo(created.path("createdAt"));
        assertThat(edited.path("entry").path("acceptedImperfective")).isEqualTo(created.path("entry").path("acceptedImperfective"));
        assertThat(jdbc.queryForMap("SELECT * FROM aspect_progress WHERE pair_id=?",id)).isEqualTo(history);
        postJsonMethod("PUT","/api/aspect-pairs/"+id,json.writeValueAsString(Map.of("namespace","manual","version",0,"entry",changed)),409);
        var archived=archive(edited,true);
        assertThat(getJson("/api/aspect-pairs?namespace=manual").path("totalElements").asInt()).isZero();
        assertThat(getJson("/api/aspect-pairs?namespace=manual&archived=true").path("totalElements").asInt()).isEqualTo(1);
        ObjectNode updated=changed.deepCopy();updated.put("note","Из импорта");
        JsonNode p=preview(document(List.of(updated)),"manual");
        assertThat(p.at("/entries/0/classification").asText()).isEqualTo("UPDATE");
        assertThat(getJson("/api/aspect-pairs/"+id).path("entry").path("note").asText()).isEqualTo("Новое пояснение");
        postJson("/api/aspect-pair-imports/confirm",confirmation(p,true),200);
        var imported=getJson("/api/aspect-pairs/"+id);
        assertThat(imported.path("archived").asBoolean()).isTrue();
        assertThat(imported.path("id")).isEqualTo(created.path("id"));
        assertThat(imported.path("entry").path("note").asText()).isEqualTo("Из импорта");
        var restored=archive(imported,false);assertThat(restored.path("archived").asBoolean()).isFalse();
        assertThat(jdbc.queryForMap("SELECT * FROM aspect_progress WHERE pair_id=?",id)).isEqualTo(history);
        assertThat(archived.path("entry").path("id").asText()).isEqualTo("custom-1");
    }
    @Test void namespaceSeparatesIdentityAndListingCombinesSearchFiltersBeforePagination() throws Exception {
        for(int i=1;i<=3;i++){
            ObjectNode e=entry("item-"+i);e.put("topic","Быт");e.put("studyStage",2);e.put("relationType","resultative");
            create("first",e);
        }
        ObjectNode e=entry("item-1");e.put("topic","Покупки");create("second",e);
        var result=getJson("/api/aspect-pairs?namespace=first&q=КУПЯ&studyStage=2&topic=Быт&relationType=resultative&size=2&page=1");
        assertThat(result.path("totalElements").asInt()).isEqualTo(3);
        assertThat(result.path("content").size()).isEqualTo(1);
        assertThat(getJson("/api/aspect-pairs?q=%").path("totalElements").asInt()).isZero();
        assertThat(getJson("/api/aspect-pairs?size=2&page=50").path("content").size()).isZero();
        mvc.perform(get("/api/aspect-pairs").param("studyStage","4")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/aspect-pairs").param("size","101")).andExpect(status().isBadRequest());
        postJson("/api/aspect-pairs",json.writeValueAsString(Map.of("namespace","second","entry",e)),409);
    }
    @Test void explicitSelectionControlsUpdatesAndDoesNotDeleteOmittedEntries() throws Exception {
        var first=create("manual",entry("one"));var second=create("manual",entry("two"));
        ObjectNode update=(ObjectNode)first.path("entry").deepCopy();update.put("note","Обновление");
        var p=preview(document(List.of(update,entry("three"))),"manual");
        String body=json.writeValueAsString(Map.of("importId",p.path("importId").asText(),"requestId",UUID.randomUUID(),
                "entries",List.of(Map.of("externalId","three"))));
        var summary=postJson("/api/aspect-pair-imports/confirm",body,200);
        assertThat(summary.path("updated").asInt()).isZero();assertThat(summary.path("deselected").asInt()).isEqualTo(1);
        assertThat(getJson("/api/aspect-pairs/"+first.path("id").asLong()).path("entry").path("note").asText()).isEmpty();
        assertThat(getJson("/api/aspect-pairs/"+second.path("id").asLong()).path("entry").path("id").asText()).isEqualTo("two");
        assertThat(pairs.count()).isEqualTo(3);
    }
    @ParameterizedTest @ValueSource(strings={"format","version","stringVersion","duplicateId","blank","emptyAnswers","wrongAnswerType","missingPrimary","stage","stringStage","relation","examples","provenance","unknown","duplicateAnswer"})
    void invalidWholeDocumentCannotBePartiallyConfirmed(String kind) throws Exception {
        ObjectNode root=(ObjectNode)json.readTree(document(List.of(entry("valid"),entry("invalid"))));
        ObjectNode e=(ObjectNode)root.at("/entries/1");
        switch(kind){
            case "format" -> root.put("format","other");case "version" -> root.put("formatVersion",2);
            case "stringVersion" -> root.put("formatVersion","1");case "duplicateId" -> e.put("id","valid");
            case "blank" -> e.put("russianPerfective"," ");case "emptyAnswers" -> e.putArray("acceptedPerfective");
            case "wrongAnswerType" -> e.putArray("acceptedPerfective").add(12);
            case "missingPrimary" -> e.putArray("acceptedPerfective").add("прочета");
            case "stage" -> e.put("studyStage",4);case "stringStage" -> e.put("studyStage","1");
            case "relation" -> e.put("relationType","unknown");case "examples" -> e.putObject("examples").putObject("imperfective").put("bg","Пример");
            case "provenance" -> e.putObject("provenance").put("kind","presentation").put("sourceId","missing").put("tableRow",1);
            case "unknown" -> e.put("ignoredField","must not disappear");
            case "duplicateAnswer" -> e.putArray("acceptedPerfective").add("купя").add(" КУПЯ ");
        }
        var p=preview(json.writeValueAsBytes(root),"invalid");
        assertThat(p.path("valid").asBoolean()).isFalse();assertThat(p.path("errors").size()).isGreaterThan(0);
        String body=json.writeValueAsString(Map.of("importId",p.path("importId").asText(),"requestId",UUID.randomUUID(),"entries",List.of(Map.of("externalId","valid"))));
        postJson("/api/aspect-pair-imports/confirm",body,400);
        assertThat(pairs.count()).isZero();assertThat(count("aspect_datasets")).isZero();assertThat(count("aspect_import_receipts")).isZero();
    }
    @Test void malformedUtf8DuplicateJsonKeysAndTrailingContentAreReportedWithoutWrites() throws Exception {
        for(byte[] bytes:List.of(new byte[]{(byte)0xc3,(byte)0x28},"{\"entries\":[}".getBytes(StandardCharsets.UTF_8),"{\"formatVersion\":1,\"formatVersion\":1}".getBytes(StandardCharsets.UTF_8),
                (new String(document(List.of(entry("one"))),StandardCharsets.UTF_8)+" {}").getBytes(StandardCharsets.UTF_8))){
            var p=preview(bytes,"invalid");assertThat(p.path("valid").asBoolean()).isFalse();
            assertThat(p.path("errors").size()).isGreaterThan(0);
        }
        assertThat(pairs.count()).isZero();
    }
    @Test void rejectedDocumentsPreservePreviouslyImportedPairsReceiptsAndPracticeHistory()throws Exception{
        byte[] bytes=Files.readAllBytes(SOURCE);
        var imported=preview(bytes,NAMESPACE);
        postJson("/api/aspect-pair-imports/confirm",confirmation(imported,true),200);
        var session=practice.start(new com.example.bulgariantrainer.dto.AspectPracticeDtos.StartRequest(UUID.randomUUID(),1,List.of(1),null,
                com.example.bulgariantrainer.entity.AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,
                com.example.bulgariantrainer.entity.StudyScope.MIXED,NAMESPACE,false));
        practice.reveal(session.id(),new com.example.bulgariantrainer.dto.AspectPracticeDtos.QuestionRequest(session.currentQuestion().id()));
        var beforePairs=getJson("/api/aspect-pairs?size=100");var beforeDatasets=getJson("/api/aspect-pairs/datasets");
        var beforeProgress=jdbc.queryForList("SELECT * FROM aspect_progress");
        ObjectNode version=(ObjectNode)json.readTree(bytes);version.put("formatVersion",2);
        ObjectNode duplicate=(ObjectNode)json.readTree(bytes);((ObjectNode)duplicate.at("/entries/1")).put("id",duplicate.at("/entries/0/id").asText());
        for(byte[] invalid:List.of("{\"entries\":[}".getBytes(StandardCharsets.UTF_8),json.writeValueAsBytes(version),json.writeValueAsBytes(duplicate))){
            var p=preview(invalid,NAMESPACE);assertThat(p.path("valid").asBoolean()).isFalse();
            postJson("/api/aspect-pair-imports/confirm",json.writeValueAsString(Map.of("importId",p.path("importId").asText(),"requestId",UUID.randomUUID(),
                    "entries",List.of(Map.of("externalId","bgasp-001")))),400);
            assertThat(getJson("/api/aspect-pairs?size=100")).isEqualTo(beforePairs);
            assertThat(getJson("/api/aspect-pairs/datasets")).isEqualTo(beforeDatasets);
            assertThat(jdbc.queryForList("SELECT * FROM aspect_progress")).isEqualTo(beforeProgress);
            assertThat(count("aspect_import_receipts")).isEqualTo(1);assertThat(count("aspect_attempts")).isEqualTo(1);
        }
    }
    @Test void staleEditOrArchivePreventsAllSelectedImportWritesAndCanBeRepreviewed() throws Exception {
        var created=create("manual",entry("existing"));
        ObjectNode update=(ObjectNode)created.path("entry").deepCopy();update.put("note","Из файла");
        var p=preview(document(List.of(entry("new"),update)),"manual");
        archive(created,true);
        postJson("/api/aspect-pair-imports/confirm",confirmation(p,true),409);
        assertThat(pairs.count()).isEqualTo(1);assertThat(count("aspect_import_receipts")).isZero();
        var fresh=preview(document(List.of(entry("new"),update)),"manual");
        postJson("/api/aspect-pair-imports/confirm",confirmation(fresh,true),200);
        assertThat(pairs.count()).isEqualTo(2);
    }
    @Test void receiptSurvivesPreviewStoreLossAndChangedOrForgedConfirmationsConflict() throws Exception {
        var p=preview(document(List.of(entry("one"))),"receipt");String body=confirmation(p,true);
        var summary=postJson("/api/aspect-pair-imports/confirm",body,200);
        var request=json.readValue(body,ConfirmRequest.class);
        var restarted=new AspectPairImportService(parser,new AspectPairImportStore(),writer,datasets,pairs,service,validation,aspectJson,transactions);
        // Exact receipt lookup happens before any access to the restarted in-memory preview store.
        assertThat(restarted.confirm(request)).isEqualTo(json.treeToValue(summary,Summary.class));
        ObjectNode changed=(ObjectNode)json.readTree(body);changed.putArray("entries").addObject().put("externalId","other");
        postJson("/api/aspect-pair-imports/confirm",json.writeValueAsString(changed),409);
        var uncommitted=preview(document(List.of(entry("two"))),"receipt");
        ObjectNode forged=(ObjectNode)json.readTree(confirmation(uncommitted,true));
        ((ObjectNode)forged.at("/entries/0")).put("expectedVersion",99);
        postJson("/api/aspect-pair-imports/confirm",json.writeValueAsString(forged),400);
        assertThatThrownBy(()->new AspectPairImportStore().get(UUID.randomUUID())).hasMessageContaining("expired");
    }
    @Test void concurrentConfirmationsOfSamePreviewCommitOnce() throws Exception {
        JsonNode p=preview(document(List.of(entry("one"))),"concurrent");
        ConfirmRequest request=json.readValue(confirmation(p,true),ConfirmRequest.class);
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try{
            CountDownLatch start=new CountDownLatch(1);
            Callable<Summary> call=()->{start.await();return imports.confirm(request);};
            Future<Summary> a=executor.submit(call),b=executor.submit(call);start.countDown();
            assertThat(a.get(15,TimeUnit.SECONDS)).isEqualTo(b.get(15,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();}
        assertThat(pairs.count()).isEqualTo(1);assertThat(count("aspect_import_receipts")).isEqualTo(1);
    }
    @Test void anotherConfirmedPreviewInvalidatesDatasetMetadataSnapshot() throws Exception {
        create("shared",entry("original"));
        var first=preview(document(List.of(entry("one"))),"shared");
        var second=preview(document(List.of(entry("two"))),"shared");
        postJson("/api/aspect-pair-imports/confirm",confirmation(first,true),200);
        postJson("/api/aspect-pair-imports/confirm",confirmation(second,true),409);
        assertThat(pairs.count()).isEqualTo(2);assertThat(count("aspect_import_receipts")).isEqualTo(1);
    }
    @Test void creationOfPreviewedNewIdAndForgedSelectionCannotOverwriteCurrentData() throws Exception {
        var p=preview(document(List.of(entry("one"),entry("two"))),"shared");
        var existing=create("shared",entry("two"));
        postJson("/api/aspect-pair-imports/confirm",confirmation(p,true),409);
        assertThat(pairs.count()).isEqualTo(1);
        var valid=preview(document(List.of(entry("one"))),"shared");
        var forged=json.createObjectNode().put("importId",valid.path("importId").asText()).put("requestId",UUID.randomUUID().toString());
        forged.putArray("entries").addObject().put("externalId","not-in-preview");
        postJson("/api/aspect-pair-imports/confirm",json.writeValueAsString(forged),400);
        assertThat(getJson("/api/aspect-pairs/"+existing.path("id").asLong()).path("entry").path("note").asText()).isEmpty();
    }
    @Test void validationUsesAspectSpecificNormalizationWithoutLosingParticlesOrLetters() throws Exception {
        ObjectNode e=entry("one");e.put("imperfective","спомням си");
        e.putArray("acceptedImperfective").add("  СПО\u0301МНЯМ\u00a0 СИ ");
        var created=create("normalization",e);
        assertThat(created.path("entry").path("acceptedImperfective").get(0).asText()).isEqualTo("  СПО\u0301МНЯМ\u00a0 СИ ");
        e.putArray("acceptedImperfective").add("спомням");
        postJson("/api/aspect-pairs",json.writeValueAsString(Map.of("namespace","normalization","entry",e)),400);
        for(String distinct:List.of("и","й","ѝ")){
            ObjectNode spelling=entry("letter-"+distinct);spelling.put("imperfective",distinct);
            spelling.putArray("acceptedImperfective").add("и");
            var errors=validation.errors(json.treeToValue(spelling,Entry.class),"entry");
            assertThat(errors.isEmpty()).isEqualTo(distinct.equals("и"));
        }
    }
    private ObjectNode entry(String id){
        ObjectNode e=json.createObjectNode();e.put("id",id).put("imperfective","купувам").put("perfective","купя")
                .put("russianImperfective","покупать").put("russianPerfective","купить").put("topic","Покупки")
                .put("studyStage",1).put("relationType","core").put("note","");
        e.putArray("acceptedImperfective").add("купувам");e.putArray("acceptedPerfective").add("купя");
        e.putObject("provenance").put("kind","added");return e;
    }
    private byte[] document(List<ObjectNode> entries){return json.writeValueAsBytes(Map.of("format","bulgariantrainer.aspect-pairs","formatVersion",1,"entries",entries));}
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    private JsonNode preview(byte[] bytes,String namespace)throws Exception{
        return json.readTree(mvc.perform(multipart("/api/aspect-pair-imports/preview")
                .file(new MockMultipartFile("file","pairs.json","application/json",bytes)).param("namespace",namespace))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String confirmation(JsonNode preview,boolean all){
        List<Map<String,Object>> selected=new ArrayList<>();
        for(JsonNode row:preview.path("entries")){
            Map<String,Object> selection=new HashMap<>();selection.put("externalId",row.path("externalId").asText());
            if(!row.path("expectedVersion").isNull())selection.put("expectedVersion",row.path("expectedVersion").asLong());
            selected.add(selection);
        }
        return json.writeValueAsString(Map.of("importId",preview.path("importId").asText(),"requestId",UUID.randomUUID(),"entries",selected));
    }
    private JsonNode create(String namespace,ObjectNode entry)throws Exception{return postJson("/api/aspect-pairs",json.writeValueAsString(Map.of("namespace",namespace,"entry",entry)),201);}
    private JsonNode archive(JsonNode pair,boolean value)throws Exception{return postJsonMethod("PATCH","/api/aspect-pairs/"+pair.path("id").asLong()+"/archive",
            json.writeValueAsString(Map.of("version",pair.path("version").asLong(),"archived",value)),200);}
    private JsonNode getJson(String path)throws Exception{return json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private JsonNode postJson(String path,String body,int status)throws Exception{return postJsonMethod("POST",path,body,status);}
    private JsonNode postJsonMethod(String method,String path,String body,int expected)throws Exception{
        var request=switch(method){case "PUT"->put(path);case "PATCH"->patch(path);default->post(path);};
        return json.readTree(mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }
}
