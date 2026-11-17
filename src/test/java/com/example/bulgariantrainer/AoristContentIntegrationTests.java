package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AoristDtos.*;
import com.example.bulgariantrainer.service.*;
import com.example.bulgariantrainer.exception.AoristException;
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

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:aorist-content;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc @ActiveProfiles("test")
class AoristContentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AoristImportService imports;
    @Autowired AoristImportWriter writer;
    @Autowired AoristImportStore store;
    @Autowired AoristJson aoristJson;
    private static final String DATASET="bg-aorist-core-40";
    private static final Path SOURCE=Path.of("learning-materials/aorist/bulgarian_aorist_40.json");
    private static final List<String> TABLES=List.of("aorist_import_receipts","aorist_progress","aorist_forms","aorist_verbs","aorist_datasets");

    @BeforeEach void clean() { for(String table:TABLES) jdbc.update("DELETE FROM "+table); }

    @Test void completeContentRoundTripPreservesEveryFieldAndReimportIsIdempotent() throws Exception {
        byte[] bytes=Files.readAllBytes(SOURCE); var original=json.readTree(bytes);
        var legacy=getJson("/api/statistics"); var aspect=getJson("/api/aspect-pair-practice/statistics");
        var p=preview(bytes); assertThat(p.valid()).isTrue(); assertThat(p.entries()).hasSize(40);
        assertThat(p.warnings()).extracting(Warning::code).contains("GROUP_REVIEW");
        for(String table:TABLES) assertThat(count(table)).isZero();
        var confirm=confirmation(p,p.entries()); var summary=confirm(confirm,200);
        assertThat(summary.path("created").asInt()).isEqualTo(40);
        assertThat(confirm(confirm,200)).isEqualTo(summary);
        assertThat(count("aorist_verbs")).isEqualTo(40); assertThat(count("aorist_forms")).isEqualTo(240);
        assertThat(count("aorist_progress")).isZero();
        var rows=getJson("/api/aorist-verbs?size=100").path("content");
        Map<String,JsonNode> expected=new HashMap<>(); for(var e:original.path("entries")) expected.put(e.path("id").asText(),e);
        for(var row:rows) {
            assertThat(row.path("id").isIntegralNumber()).isTrue();
            assertThat(row.path("entry")).isEqualTo(expected.get(row.at("/entry/id").asText()));
        }
        var dataset=getJson("/api/aorist-verbs/datasets").get(0);
        assertThat(dataset.path("datasetId").asText()).isEqualTo(DATASET);
        assertThat(dataset.path("datasetRevision").asInt()).isEqualTo(1);
        assertThat(dataset.path("fileHash").asText()).isEqualTo(aoristJson.hashBytes(bytes));
        assertThat(dataset.path("metadata")).isEqualTo(p.metadata());
        byte[] exported=mvc.perform(get("/api/aorist-verbs/export").param("datasetId",DATASET))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(json.readTree(exported)).isEqualTo(original);
        var repeat=preview(exported); assertThat(repeat.valid()).isTrue();
        assertThat(repeat.entries()).allMatch(e->e.classification().equals("UNCHANGED"));
        var repeated=confirm(confirmation(repeat,repeat.entries()),200);
        assertThat(repeated.path("unchanged").asInt()).isEqualTo(40); assertThat(repeated.path("created").asInt()).isZero();
        assertThat(count("aorist_forms")).isEqualTo(240);
        assertThat(getJson("/api/statistics")).isEqualTo(legacy);
        assertThat(getJson("/api/aspect-pair-practice/statistics").path("answered")).isEqualTo(aspect.path("answered"));
    }

    @Test void selectedUpdatesEditingArchiveAndRestorePreserveIdentityAndProgress() throws Exception {
        importAll(); var before=first(); long id=before.path("id").asLong();
        seedProgress(id); var progress=jdbc.queryForMap("SELECT * FROM aorist_progress WHERE verb_id=?",id);
        ObjectNode doc=document(); ObjectNode first=(ObjectNode)doc.path("entries").get(0);
        first.put("note","Изменено явно"); ((ArrayNode)first.path("forms").get("1sg")).add("  ГЛЕ́ДАХМЕ  ");
        ((ObjectNode)doc.path("entries").get(1)).put("note","Не выбирать");
        doc.put("datasetRevision",2);
        var p=preview(json.writeValueAsBytes(doc));
        assertThat(p.entries().getFirst().classification()).isEqualTo("UPDATE");
        assertThat(getJson("/api/aorist-verbs/"+id)).isEqualTo(before);
        var result=confirm(confirmation(p,List.of(p.entries().getFirst())),200);
        assertThat(result.path("updated").asInt()).isEqualTo(1); assertThat(result.path("deselected").asInt()).isEqualTo(39);
        var updated=getJson("/api/aorist-verbs/"+id);
        assertThat(updated.path("createdAt")).isEqualTo(before.path("createdAt"));
        assertThat(updated.at("/entry/forms/1sg").size()).isEqualTo(2);
        assertThat(getJson("/api/aorist-verbs?size=100").at("/content/1/entry/note").asText()).isNotEqualTo("Не выбирать");
        ObjectNode entry=(ObjectNode)updated.path("entry").deepCopy(); entry.put("studyGroup","Моя группа, не CEFR");
        ((ArrayNode)entry.path("forms").get("1sg")).remove(1);
        updated=send("PUT","/api/aorist-verbs/"+id,Map.of("datasetId",DATASET,"version",updated.path("version").asLong(),"entry",entry),200);
        var archived=archive(updated,true);
        assertThat(getJson("/api/aorist-verbs").path("totalElements").asInt()).isEqualTo(39);
        assertThat(getJson("/api/aorist-verbs?archived=true").path("totalElements").asInt()).isEqualTo(1);
        var changed=document(); ((ObjectNode)changed.path("entries").get(0)).put("note","Архив обновлён"); changed.put("datasetRevision",3);
        var reimport=preview(json.writeValueAsBytes(changed)); confirm(confirmation(reimport,List.of(reimport.entries().getFirst())),200);
        archived=getJson("/api/aorist-verbs/"+id); assertThat(archived.path("archived").asBoolean()).isTrue();
        assertThat(archive(archived,false).path("id").asLong()).isEqualTo(id);
        assertThat(jdbc.queryForMap("SELECT * FROM aorist_progress WHERE verb_id=?",id)).isEqualTo(progress);
        send("PUT","/api/aorist-verbs/"+id,Map.of("datasetId",DATASET,"version",before.path("version").asLong(),"entry",entry),409);
        entry.put("id","changed-id"); send("PUT","/api/aorist-verbs/"+id,Map.of("datasetId",DATASET,"version",archived.path("version").asLong(),"entry",entry),400);
    }

    @Test void creationUsesDatasetScopedExternalIdentityAndValidMetadata() throws Exception {
        ObjectNode source=document(), metadata=source.deepCopy(); metadata.remove("entries"); JsonNode entry=source.path("entries").get(0);
        var created=send("POST","/api/aorist-verbs",Map.of("datasetId",DATASET,"entry",entry,"metadata",metadata),201);
        send("POST","/api/aorist-verbs",Map.of("datasetId",DATASET,"entry",entry),409);
        archive(created,true); send("POST","/api/aorist-verbs",Map.of("datasetId",DATASET,"entry",entry),409);
        metadata.put("datasetId","another-dataset");
        var other=send("POST","/api/aorist-verbs",Map.of("datasetId","another-dataset","entry",entry,"metadata",metadata),201);
        assertThat(other.path("id")).isNotEqualTo(created.path("id"));
        ObjectNode homograph=(ObjectNode)entry.deepCopy(); homograph.put("id","another-sense"); homograph.put("russian","Другое значение");
        send("POST","/api/aorist-verbs",Map.of("datasetId","another-dataset","entry",homograph),201);
        send("POST","/api/aorist-verbs",Map.of("datasetId","missing-metadata","entry",entry),400);
        assertThat(count("aorist_datasets")).isEqualTo(2); assertThat(count("aorist_verbs")).isEqualTo(3);
        mvc.perform(delete("/api/aorist-verbs/"+created.path("id").asLong())).andExpect(status().isMethodNotAllowed());
    }

    @Test void searchFiltersAndPaginationIncludeFormsMeaningsAndLiteralCharacters() throws Exception {
        importAll();
        assertThat(getJson("/api/aorist-verbs?aspect=imperfective").path("totalElements").asInt()).isEqualTo(11);
        assertThat(getJson("/api/aorist-verbs?studyGroup=2_ih").path("totalElements").asInt()).isEqualTo(9);
        assertThat(getJson("/api/aorist-verbs?reflexiveParticle=се").path("totalElements").asInt()).isEqualTo(2);
        assertThat(getJson("/api/aorist-verbs?reflexiveParticle=none").path("totalElements").asInt()).isEqualTo(38);
        assertThat(getJson("/api/aorist-verbs?q=ПРОЧЕТОХМЕ").at("/content/0/entry/lemma").asText()).isEqualTo("прочета");
        assertThat(getJson("/api/aorist-verbs?q=раздеться").path("totalElements").asInt()).isEqualTo(1);
        assertThat(getJson("/api/aorist-verbs?q=%25").path("totalElements").asInt()).isZero();
        assertThat(getJson("/api/aorist-verbs?size=7&page=5").path("content").size()).isEqualTo(5);
        assertThat(getJson("/api/aorist-verbs?size=7&page=6").path("content").size()).isZero();
        assertThat(getJson("/api/aorist-verbs/filters?datasetId="+DATASET).path("studyGroups").size()).isEqualTo(6);
        for(String query:List.of("size=101","page=-1","aspect=both","reflexiveParticle=й","datasetId=Bad"))
            mvc.perform(get("/api/aorist-verbs?"+query)).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings={"format","version","versionString","dataset","revision","date","language","entryForm","rootUnknown",
            "duplicateId","id","lemma","aspect","tense","missingPerson","extraPerson","emptyAnswers","scalarAnswers","blankAnswer",
            "numericAnswer","duplicateAnswer","source","sourceUrl","sourceDate","sourceUnknown","particle","particleForm","note","group","entryUnknown","emptyEntries"})
    void invalidEntireDocumentsLeavePopulatedDatabaseUnchanged(String problem) throws Exception {
        importAll(); seedProgress(first().path("id").asLong()); var before=snapshot();
        ObjectNode doc=document(), entry=(ObjectNode)doc.path("entries").get(0), forms=(ObjectNode)entry.path("forms");
        switch(problem) {
            case "format" -> doc.put("format","bulgariantrainer.aspect-pairs");
            case "version" -> doc.put("formatVersion",2);
            case "versionString" -> doc.put("formatVersion","1");
            case "dataset" -> doc.put("datasetId"," ");
            case "revision" -> doc.put("datasetRevision",0);
            case "date" -> doc.put("createdOn","2026-02-30");
            case "language" -> doc.put("language","en");
            case "entryForm" -> doc.put("entryForm","1st_person_singular_present_citation_form");
            case "rootUnknown" -> doc.put("ignored",true);
            case "duplicateId" -> ((ObjectNode)doc.path("entries").get(39)).put("id",entry.path("id").asText());
            case "id" -> entry.put("id"," ");
            case "lemma" -> entry.put("lemma",123);
            case "aspect" -> entry.put("aspect","aorist");
            case "tense" -> entry.put("tense","imperfect");
            case "missingPerson" -> forms.remove("3pl");
            case "extraPerson" -> forms.putArray("3sg_f").add("гледа");
            case "emptyAnswers" -> forms.putArray("1sg");
            case "scalarAnswers" -> forms.put("1sg","гледах");
            case "blankAnswer" -> forms.putArray("1sg").add("\u00a0 \t");
            case "numericAnswer" -> forms.putArray("1sg").add(123);
            case "duplicateAnswer" -> forms.putArray("1sg").add("гле́дах").add(" ГЛЕДАХ ");
            case "source" -> entry.putNull("source");
            case "sourceUrl" -> ((ObjectNode)entry.path("source")).put("url","file:///C:/secret");
            case "sourceDate" -> ((ObjectNode)entry.path("source")).put("checkedOn","tomorrow");
            case "sourceUnknown" -> ((ObjectNode)entry.path("source")).put("execute","ignored");
            case "particle" -> entry.put("reflexiveParticle","ми");
            case "particleForm" -> { entry.put("lemma","гледам се"); entry.put("reflexiveParticle","се"); }
            case "note" -> entry.putNull("note");
            case "group" -> entry.put("studyGroup","");
            case "entryUnknown" -> entry.put("studyStage",1);
            case "emptyEntries" -> doc.putArray("entries");
            default -> throw new IllegalArgumentException(problem);
        }
        var invalid=preview(json.writeValueAsBytes(doc)); assertThat(invalid.valid()).as(problem).isFalse(); assertThat(invalid.errors()).isNotEmpty();
        var request=new ConfirmRequest(invalid.importId(),UUID.randomUUID(),List.of(new Selection("bgaor-001",0L)),false);
        confirm(request,400); assertThat(snapshot()).isEqualTo(before);
    }

    @Test void malformedEncodingDuplicateKeysAndTrailingDocumentsAreRejectedWithoutWrites() throws Exception {
        importAll(); var before=snapshot();
        for(byte[] bytes:List.of("{".getBytes(StandardCharsets.UTF_8),new byte[]{(byte)0xc3,0x28},
                "{\"formatVersion\":1,\"formatVersion\":1}".getBytes(StandardCharsets.UTF_8),
                (Files.readString(SOURCE)+" {}").getBytes(StandardCharsets.UTF_8))) {
            var p=preview(bytes); assertThat(p.valid()).isFalse(); assertThat(p.errors()).isNotEmpty();
            confirm(new ConfirmRequest(p.importId(),UUID.randomUUID(),List.of(new Selection("bgaor-001",0L)),false),400);
            assertThat(snapshot()).isEqualTo(before);
        }
        byte[] bom=("\uFEFF"+Files.readString(SOURCE)).getBytes(StandardCharsets.UTF_8); assertThat(preview(bom).valid()).isTrue();
        mvc.perform(multipart("/api/aorist-imports/preview").file(new MockMultipartFile("file","aorist.csv","text/csv",new byte[0]))).andExpect(status().isUnsupportedMediaType());
        mvc.perform(multipart("/api/aorist-imports/preview").file(new MockMultipartFile("file","aorist.json","application/json",new byte[1024*1024+1]))).andExpect(status().isPayloadTooLarge());
    }

    @Test void stalePreviewsAndForgedSelectionsCannotOverwriteConcurrentChanges() throws Exception {
        importAll(); var p=preview(Files.readAllBytes(SOURCE)); var row=first();
        archive(row,true); var before=snapshot();
        assertThat(confirm(confirmation(p,p.entries()),409).path("code").asText()).isEqualTo("STALE_IMPORT_PREVIEW");
        assertThat(snapshot()).isEqualTo(before);
        p=preview(Files.readAllBytes(SOURCE));
        confirm(new ConfirmRequest(p.importId(),UUID.randomUUID(),List.of(new Selection("bgaor-001",999L)),false),400);
        confirm(new ConfirmRequest(p.importId(),UUID.randomUUID(),List.of(new Selection("unknown",null)),false),400);
        var s=new Selection(p.entries().getFirst().externalId(),p.entries().getFirst().expectedVersion());
        confirm(new ConfirmRequest(p.importId(),UUID.randomUUID(),List.of(s,s),false),400);
        assertThat(snapshot()).isEqualTo(before);
        var fresh=document(); fresh.put("datasetId","new-preview"); var notYet=preview(json.writeValueAsBytes(fresh));
        ObjectNode meta=fresh.deepCopy(); meta.remove("entries");
        send("POST","/api/aorist-verbs",Map.of("datasetId","new-preview","entry",fresh.path("entries").get(0),"metadata",meta),201);
        confirm(confirmation(notYet,notYet.entries()),409);
        assertThat(count("aorist_verbs")).isEqualTo(41);
    }

    @Test void databaseFailureAfterEarlierWritesRollsBackContentMetadataAndReceipt() throws Exception {
        var small=document(); ((ArrayNode)small.path("entries")).remove(39);
        var p=preview(json.writeValueAsBytes(small)); confirm(confirmation(p,p.entries()),200);
        seedProgress(first().path("id").asLong()); var before=snapshot();
        var changed=document(); changed.put("datasetRevision",2); ((ObjectNode)changed.path("entries").get(0)).put("note","Must roll back");
        var update=preview(json.writeValueAsBytes(changed));
        jdbc.execute("ALTER TABLE aorist_verbs ADD CONSTRAINT test_reject_last CHECK(external_id <> 'bgaor-040')");
        try { confirm(confirmation(update,update.entries()),409); assertThat(snapshot()).isEqualTo(before); }
        finally { jdbc.execute("ALTER TABLE aorist_verbs DROP CONSTRAINT test_reject_last"); }
        var retried=confirm(confirmation(update,update.entries()),200); assertThat(retried.path("created").asInt()).isEqualTo(1);
    }

    @Test void revisionWarningsExplicitDowngradeAndReceiptRetrySurvivePreviewLoss() throws Exception {
        importAll(); var doc=document(); doc.put("datasetRevision",3);
        var p=preview(json.writeValueAsBytes(doc)); confirm(confirmation(p,p.entries()),200);
        var lower=preview(Files.readAllBytes(SOURCE)); assertThat(lower.warnings()).extracting(Warning::code).contains("REVISION_DOWNGRADE");
        var request=confirmation(lower,lower.entries()); var before=snapshot();
        assertThat(confirm(request,409).path("code").asText()).isEqualTo("REVISION_DOWNGRADE_CONFIRMATION_REQUIRED"); assertThat(snapshot()).isEqualTo(before);
        request=new ConfirmRequest(request.importId(),request.requestId(),request.entries(),true); var summary=confirm(request,200);
        for(int i=0;i<6;i++) preview(Files.readAllBytes(SOURCE)); // Evict confirmed preview.
        assertThat(confirm(request,200)).isEqualTo(summary);
        var changed=new ConfirmRequest(request.importId(),request.requestId(),List.of(request.entries().getFirst()),true);
        assertThat(confirm(changed,409).path("code").asText()).isEqualTo("IMPORT_REQUEST_CHANGED");
        var uncommitted=preview(Files.readAllBytes(SOURCE));
        for(int i=0;i<6;i++) preview(Files.readAllBytes(SOURCE));
        confirm(confirmation(uncommitted,uncommitted.entries()),410);
    }

    @Test void exportScopesExcludeHistoryAndRoundTripOrderedAlternatives() throws Exception {
        importAll(); var row=first(); seedProgress(row.path("id").asLong());
        ObjectNode entry=(ObjectNode)row.path("entry").deepCopy(); ((ArrayNode)entry.path("forms").get("1sg")).add("ѝ").add("й").add("и");
        row=send("PUT","/api/aorist-verbs/"+row.path("id").asLong(),Map.of("datasetId",DATASET,"version",row.path("version").asLong(),"entry",entry),200);
        archive(row,true);
        assertThat(getJson("/api/aorist-verbs/export?datasetId="+DATASET).path("entries").size()).isEqualTo(39);
        var archived=getJson("/api/aorist-verbs/export?datasetId="+DATASET+"&archived=true");
        assertThat(archived.path("entries").get(0)).isEqualTo(entry);
        var all=getJson("/api/aorist-verbs/export?datasetId="+DATASET+"&archiveScope=all");
        assertThat(all.path("entries").size()).isEqualTo(40); assertThat(all.has("progress")).isFalse();
        assertThat(all.toString()).doesNotContain("totalAttempts","createdAt","archived","nextReviewAt");
        var p=preview(json.writeValueAsBytes(all)); assertThat(p.valid()).isTrue(); assertThat(p.entries()).allMatch(e->e.classification().equals("UNCHANGED"));
        mvc.perform(get("/api/aorist-verbs/export").param("datasetId",DATASET).param("archiveScope","all").param("archived","false")).andExpect(status().isBadRequest());
    }

    @Test void concurrentConfirmationOfSameRequestHasOneReceiptAndNoDuplicates() throws Exception {
        var p=preview(Files.readAllBytes(SOURCE)); var request=confirmation(p,p.entries());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            Callable<Summary> task=()-> { gate.await(); return imports.confirm(request); };
            var a=pool.submit(task); var b=pool.submit(task); gate.countDown();
            assertThat(a.get(20,TimeUnit.SECONDS)).isEqualTo(b.get(20,TimeUnit.SECONDS));
        }
        assertThat(count("aorist_verbs")).isEqualTo(40); assertThat(count("aorist_import_receipts")).isEqualTo(1);
        assertThat(count("aorist_forms")).isEqualTo(240);
    }

    private ObjectNode document() throws Exception { return (ObjectNode)json.readTree(Files.readAllBytes(SOURCE)); }
    private Preview preview(byte[] bytes) throws Exception {
        String response=mvc.perform(multipart("/api/aorist-imports/preview").file(new MockMultipartFile("file","aorist.json","application/json",bytes)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return aoristJson.read(response,Preview.class);
    }
    private ConfirmRequest confirmation(Preview p,List<PreviewEntry> selected) {
        return new ConfirmRequest(p.importId(),UUID.randomUUID(),selected.stream().map(e->new Selection(e.externalId(),e.expectedVersion())).toList(),false);
    }
    private JsonNode confirm(ConfirmRequest request,int status) throws Exception { return send("POST","/api/aorist-imports/confirm",request,status); }
    private void importAll() throws Exception { var p=preview(Files.readAllBytes(SOURCE)); confirm(confirmation(p,p.entries()),200); }
    private JsonNode getJson(String url) throws Exception {
        return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode send(String method,String url,Object body,int expected) throws Exception {
        return json.readTree(mvc.perform(request(org.springframework.http.HttpMethod.valueOf(method),url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray());
    }
    private JsonNode first() throws Exception { return getJson("/api/aorist-verbs?size=100").path("content").get(0); }
    private JsonNode archive(JsonNode row,boolean archived) throws Exception {
        return send("PATCH","/api/aorist-verbs/"+row.path("id").asLong()+"/archive",Map.of("version",row.path("version").asLong(),"archived",archived),200);
    }
    private void seedProgress(long id) { jdbc.update("INSERT INTO aorist_progress(verb_id,person,total_attempts,correct_attempts,successful_review_count,consecutive_correct,repetition_interval,next_review_at) VALUES (?, '1sg',8,6,6,2,3,CURRENT_TIMESTAMP)",id); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    private Map<String,List<Map<String,Object>>> snapshot() {
        Map<String,List<Map<String,Object>>> result=new LinkedHashMap<>();
        // Cast LOBs to text so a value comparison detects changes rather than LOB handle identity.
        result.put("verbs",jdbc.queryForList("SELECT id,dataset_id,external_id,version,content_hash,archived,created_at,updated_at FROM aorist_verbs ORDER BY id"));
        result.put("forms",jdbc.queryForList("SELECT * FROM aorist_forms ORDER BY id"));
        result.put("progress",jdbc.queryForList("SELECT * FROM aorist_progress ORDER BY id"));
        result.put("datasets",jdbc.queryForList("SELECT id,namespace,version,dataset_revision,file_hash,CAST(metadata_json AS VARCHAR) AS metadata,updated_at FROM aorist_datasets ORDER BY id"));
        result.put("receipts",jdbc.queryForList("SELECT request_id,import_id,request_hash,file_hash,CAST(summary_json AS VARCHAR) AS summary FROM aorist_import_receipts ORDER BY created_at"));
        return result;
    }
}
