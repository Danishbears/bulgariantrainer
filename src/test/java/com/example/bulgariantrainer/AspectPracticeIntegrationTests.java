package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AspectPairDtos;
import com.example.bulgariantrainer.dto.AspectPracticeDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:aspect-practice-tests;DB_CLOSE_DELAY=-1","training.aspect-pairs.random-seed=42"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(AspectPracticeIntegrationTests.TimeConfiguration.class)
class AspectPracticeIntegrationTests {
    private static final String BASE="/api/aspect-pair-practice";
    private static final Instant NOW=Instant.parse("2026-10-31T12:00:00Z");
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock aspectTestClock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AspectPairService pairService;
    @Autowired AspectPracticeService practice;
    @Autowired AspectQuestionRepository questions;
    @Autowired AspectProgressRepository progress;
    @Autowired AspectSessionRepository sessions;
    private Map<String,AspectPairDtos.Entry> fixture;

    @BeforeEach void clean() throws Exception {
        for(String table:List.of("aspect_attempts","aspect_questions","aspect_sessions","aspect_progress","aspect_import_receipts","aspect_pair_answers","aspect_pairs","aspect_datasets"))jdbc.update("DELETE FROM "+table);
        fixture=new HashMap<>();
        for(JsonNode entry:json.readTree(Files.readAllBytes(Path.of("learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json"))).path("entries")){
            var e=json.treeToValue(entry,AspectPairDtos.Entry.class);fixture.put(e.id(),e);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter='|',value={
            "bgasp-057|IMPERFECTIVE_TO_PERFECTIVE|купя|CORRECT",
            "bgasp-057|PERFECTIVE_TO_IMPERFECTIVE|купувам|CORRECT",
            "bgasp-043|PERFECTIVE_TO_IMPERFECTIVE|чета|CORRECT",
            "bgasp-043|PERFECTIVE_TO_IMPERFECTIVE|прочитам|CORRECT",
            "bgasp-010|PERFECTIVE_TO_IMPERFECTIVE|изпявам|CORRECT",
            "bgasp-021|IMPERFECTIVE_TO_PERFECTIVE|опитам|INCORRECT",
            "bgasp-021|IMPERFECTIVE_TO_PERFECTIVE|опитам се|CORRECT",
            "bgasp-047|PERFECTIVE_TO_IMPERFECTIVE|седя|INCORRECT",
            "bgasp-050|PERFECTIVE_TO_IMPERFECTIVE|стоя|INCORRECT",
            "bgasp-066|IMPERFECTIVE_TO_PERFECTIVE|спомня си|CORRECT",
            "bgasp-066|IMPERFECTIVE_TO_PERFECTIVE|спомня|INCORRECT",
            "bgasp-057|IMPERFECTIVE_TO_PERFECTIVE|куп|INCORRECT",
            "bgasp-057|IMPERFECTIVE_TO_PERFECTIVE|kупя|INCORRECT",
            "bgasp-057|IMPERFECTIVE_TO_PERFECTIVE|купя!|INCORRECT",
            "bgasp-057|IMPERFECTIVE_TO_PERFECTIVE|купувам|INCORRECT"})
    void validatesOnlyTheSnapshotAcceptedArray(String id,AspectDirection direction,String answer,String outcome)throws Exception{
        var pair=create(id);var state=start(1,List.of(pair.entry().studyStage()),direction,StudyScope.MIXED,false,null);
        var result=answer(state,answer,200);
        assertThat(result.at("/currentQuestion/feedback/outcome").asText()).isEqualTo(outcome);
        assertThat(result.at("/currentQuestion/feedback/content")).isEqualTo(json.valueToTree(pair.entry()));
        if(outcome.equals("INCORRECT"))assertThat(result.at("/currentQuestion/feedback/message").asText()).isEqualTo("Не совпало с ответами этой карточки");
        assertThat(count("aspect_attempts")).isEqualTo(1);
    }

    @Test void normalizesStressCaseAndUnicodeWhitespaceButPreservesDistinctLetters()throws Exception{
        create("bgasp-057");var state=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        assertThat(answer(state," \tКУ\u0301ПЯ\u00a0\n",200).path("correctAnswers").asInt()).isEqualTo(1);
        for(String target:List.of("ѝ","й","и")){
            var base=fixture.get("bgasp-057");
            pairService.create(new AspectPairDtos.Request("letters-"+(int)target.charAt(0),null,
                    new AspectPairDtos.Entry("letter","стимул",target,"стимул","буква",List.of("стимул"),List.of(target),"Буквы",1,"core","",new AspectPairDtos.Provenance("added",null,null),null)));
            for(String submitted:List.of("и","й","ѝ","и\u0300","и\u0306")){
                var request=new StartRequest(UUID.randomUUID(),1,null,"Буквы",AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,"letters-"+(int)target.charAt(0),false);
                var s=json.valueToTree(practice.start(request));
                boolean correct=submitted.equals(target)||(target.equals("ѝ")&&submitted.equals("и\u0300"))||(target.equals("й")&&submitted.equals("и\u0306"));
                assertThat(answer(s,submitted,200).path("correctAnswers").asInt()).isEqualTo(correct?1:0);
            }
        }
    }

    @Test void defaultStageArchiveAndFiltersOfferShorterSessionsWithoutDuplicates()throws Exception{
        var a=create("bgasp-057");create("bgasp-043");create("bgasp-028");var archived=create("bgasp-058");
        pairService.archive(archived.id(),new AspectPairDtos.ArchiveRequest(true,archived.version()));
        JsonNode available=get(BASE+"/availability");
        assertThat(available.path("eligiblePairs").asInt()).isEqualTo(1);
        assertThat(available.path("shorterRequired").asBoolean()).isTrue();
        post(BASE+"/sessions",Map.of("requestId",UUID.randomUUID()),409,"SHORTER_SESSION_REQUIRED");
        var state=start(20,null,AspectDirection.MIXED,StudyScope.MIXED,true,null);
        assertThat(state.path("totalQuestions").asInt()).isEqualTo(1);
        assertThat(state.at("/settings/studyStages/0").asInt()).isEqualTo(1);
        assertThat(questions.findBySessionIdOrderByPositionAsc(state.path("id").asLong()).getFirst().getPair().getId()).isEqualTo(a.id());
        var all=start(10,List.of(1,2,3),AspectDirection.MIXED,StudyScope.MIXED,true,null);
        assertThat(all.path("totalQuestions").asInt()).isEqualTo(3);
        var filtered=start(10,List.of(1,2,3),AspectDirection.MIXED,StudyScope.MIXED,true,"Учёба");
        assertThat(filtered.path("totalQuestions").asInt()).isEqualTo(1);
        assertThat(filtered.at("/currentQuestion/prompt/bulgarian").asText()).isIn("чета","прочета");
    }

    @Test void mixedModeIsBalancedAndEachPairOccursOnlyOnce()throws Exception{
        for(int i=1;i<=12;i++)create(String.format("bgasp-%03d",i));
        var state=start(10,List.of(1,2,3),AspectDirection.MIXED,StudyScope.MIXED,false,null);
        var queue=questions.findBySessionIdOrderByPositionAsc(state.path("id").asLong());
        assertThat(queue).hasSize(10);
        assertThat(queue.stream().map(q->q.getPair().getId())).doesNotHaveDuplicates();
        assertThat(queue.stream().filter(q->q.getDirection()==AspectDirection.IMPERFECTIVE_TO_PERFECTIVE).count()).isEqualTo(5);
    }

    @Test void sessionResponsesNeverLeakUnsubmittedAnswersIncludingSkippedResults()throws Exception{
        create("bgasp-057");var state=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        String serialized=json.writeValueAsString(state);
        assertThat(serialized).doesNotContain("\"купя\"","acceptedAnswers","acceptedPerfective","examples","note","provenance","snapshot","bgasp-057");
        assertThat(state.at("/currentQuestion/feedback").isNull()).isTrue();
        assertThat(get(path(state,"question"))).isEqualTo(state);
        post(path(state,"results"),Map.of(),405,null);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path(state,"results"))).andExpect(status().isConflict());
        var ended=post(path(state,"complete"),Map.of(),200,null);
        assertThat(ended.path("status").asText()).isEqualTo("ABANDONED");
        var results=get(path(state,"results"));
        assertThat(results.toString()).doesNotContain("\"купя\"","acceptedAnswers","examples","note");
        assertThat(results.at("/items/0/outcome").asText()).isEqualTo("SKIPPED");
        assertThat(results.at("/items/0/feedback").isNull()).isTrue();
        assertThat(count("aspect_attempts")).isZero();assertThat(progress.count()).isZero();
    }

    @Test void submissionsAreExactlyOnceFeedbackWaitsForNextAndSnapshotsSurviveEdits()throws Exception{
        var pair=create("bgasp-057");var state=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        var old=pair.entry();
        pairService.update(pair.id(),new AspectPairDtos.Request(pair.namespace(),pair.version(),
                new AspectPairDtos.Entry(old.id(),old.imperfective(),"пазарувам",old.russianImperfective(),old.russianPerfective(),old.acceptedImperfective(),List.of("пазарувам"),old.topic(),1,"core","Changed",old.provenance(),null)));
        pair=pairService.get(pair.id());pairService.archive(pair.id(),new AspectPairDtos.ArchiveRequest(true,pair.version()));
        var answered=answer(state,"купя",200);
        assertThat(answer(state,"купя",200)).isEqualTo(answered);
        assertThat(answered.at("/currentQuestion/feedback/content/note").asText()).isEqualTo(old.note());
        assertThat(answered.at("/currentQuestion/feedback/content/examples")).isEqualTo(json.valueToTree(old.examples()));
        assertThat(answered.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(answered.at("/currentQuestion/id")).isEqualTo(state.at("/currentQuestion/id"));
        assertThat(get(path(state,"question"))).isEqualTo(answered);
        answer(state,"КУПЯ",409);
        var moved=next(answered,200);
        assertThat(moved.path("currentQuestion").isNull()).isTrue();
        assertThat(next(answered,200)).isEqualTo(moved);
        assertThat(get(path(state,"results")).at("/items/0/feedback/content/perfective").asText()).isEqualTo("купя");
        assertThat(count("aspect_attempts")).isEqualTo(1);
        assertThat(progress.findAll().getFirst().getTotalAttempts()).isEqualTo(1);
    }

    @Test void revealIsTerminalUncreditedAndSchedulesFailureWithoutTouchingReverseProgress()throws Exception{
        var pair=create("bgasp-057");String legacy=get("/api/statistics").toString();
        var known=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);answer(known,"купя",200);
        var s=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        var revealed=reveal(s,200);assertThat(reveal(s,200)).isEqualTo(revealed);
        assertThat(revealed.path("correctAnswers").asInt()).isZero();
        assertThat(revealed.path("answeredQuestions").asInt()).isZero();assertThat(revealed.path("revealedQuestions").asInt()).isEqualTo(1);
        assertThat(revealed.at("/currentQuestion/revealed").asBoolean()).isTrue();
        answer(s,"купя",409);
        var record=progress.findByPairIdAndDirection(pair.id(),AspectDirection.IMPERFECTIVE_TO_PERFECTIVE).orElseThrow();
        assertThat(record.getTotalAttempts()).isEqualTo(2);assertThat(record.getCorrectAttempts()).isEqualTo(1);
        assertThat(record.getConsecutiveCorrect()).isZero();assertThat(record.getSuccessfulReviewCount()).isEqualTo(1);
        assertThat(record.getNextReviewAt()).isEqualTo(NOW.plus(Duration.ofDays(1)));
        assertThat(progress.findByPairIdAndDirection(pair.id(),AspectDirection.PERFECTIVE_TO_IMPERFECTIVE)).isEmpty();
        assertThat(get(path(s,"results")).path("accuracyPercent").isNull()).isTrue();
        assertThat(get("/api/statistics").toString()).isEqualTo(legacy);
        assertThat(count("study_attempts")).isZero();assertThat(count("study_progress")).isZero();assertThat(count("sentence_progress")).isZero();
    }

    @Test void newDueAndMixedQueuesUseIndependentDirectionSchedules()throws Exception{
        var pair=create("bgasp-057");var s=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.NEW,false,null);
        answer(s,"купя",200);
        assertThat(get(BASE+"/availability?scope=NEW&direction=IMPERFECTIVE_TO_PERFECTIVE").path("eligiblePairs").asInt()).isZero();
        var reverse=start(1,null,AspectDirection.MIXED,StudyScope.NEW,false,null);
        assertThat(reverse.at("/currentQuestion/direction").asText()).isEqualTo("PERFECTIVE_TO_IMPERFECTIVE");
        assertThat(get(BASE+"/availability?scope=DUE").path("eligiblePairs").asInt()).isZero();
        jdbc.update("UPDATE aspect_progress SET next_review_at=? WHERE pair_id=?",java.sql.Timestamp.from(NOW),pair.id());
        var due=start(1,null,AspectDirection.MIXED,StudyScope.DUE,false,null);
        assertThat(due.at("/currentQuestion/direction").asText()).isEqualTo("IMPERFECTIVE_TO_PERFECTIVE");
        answer(due,"куп",200);
        var record=progress.findAll().getFirst();assertThat(record.getCorrectAttempts()).isEqualTo(1);
        assertThat(record.getTotalAttempts()).isEqualTo(2);assertThat(record.getConsecutiveCorrect()).isZero();
        assertThat(record.getRepetitionInterval()).isEqualTo(1);
    }

    @Test void earlyCompletionKeepsAttemptsAndRejectsFutureForeignAndClosedQuestions()throws Exception{
        create("bgasp-057");create("bgasp-058");
        var s=start(2,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        var queue=questions.findBySessionIdOrderByPositionAsc(s.path("id").asLong());
        post(path(s,"answers"),Map.of("questionId",queue.get(1).getId(),"answer","купя"),409,"STALE_QUESTION");
        next(s,409);answer(s," \u0301 ",400);
        var other=start(2,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        post(path(s,"answers"),Map.of("questionId",other.at("/currentQuestion/id").asLong(),"answer","купя"),409,"STALE_QUESTION");
        var revealed=reveal(s,200);var current=next(revealed,200);
        assertThat(current.path("currentPosition").asInt()).isEqualTo(2);
        var ended=post(path(s,"complete"),Map.of(),200,null);
        assertThat(post(path(s,"complete"),Map.of(),200,null)).isEqualTo(ended);
        assertThat(ended.path("status").asText()).isEqualTo("ABANDONED");
        answer(current,"продам",409);reveal(current,409);
        var results=get(path(s,"results"));assertThat(results.at("/items/1/feedback").isNull()).isTrue();
        assertThat(count("aspect_attempts")).isEqualTo(1);assertThat(progress.count()).isEqualTo(1);
    }

    @Test void startRetriesRestoreQueueAndNormalizeSettingsButRejectChangedPayloads()throws Exception{
        create("bgasp-057");create("bgasp-043");UUID id=UUID.randomUUID();
        var req=new StartRequest(id,2,List.of(2,1),null,AspectDirection.MIXED,StudyScope.MIXED,"test",false);
        SessionResponse first=practice.start(req);
        var queue=questions.findBySessionIdOrderByPositionAsc(first.id()).stream().map(AspectQuestion::getId).toList();
        assertThat(practice.start(new StartRequest(id,2,List.of(1,2),null,AspectDirection.MIXED,StudyScope.MIXED,"test",false))).isEqualTo(first);
        assertThat(questions.findBySessionIdOrderByPositionAsc(first.id()).stream().map(AspectQuestion::getId).toList()).isEqualTo(queue);
        post(BASE+"/sessions",new StartRequest(id,1,List.of(1),null,AspectDirection.MIXED,StudyScope.MIXED,"test",false),409,"START_REQUEST_CHANGED");
        for(List<Integer> stages:List.of(List.<Integer>of(),List.of(1,1),List.of(4)))
            post(BASE+"/sessions",new StartRequest(UUID.randomUUID(),1,stages,null,null,null,null,false),400,"INVALID_REQUEST");
    }

    @Test void concurrentStartsAndAnswersCommitOnlyOnceAndDifferentSessionsDoNotLoseProgress()throws Exception{
        create("bgasp-057");UUID startId=UUID.randomUUID();
        StartRequest request=new StartRequest(startId,1,null,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,"test",false);
        List<SessionResponse> started=race(()->practice.start(request));assertThat(started.get(0)).isEqualTo(started.get(1));
        assertThat(sessions.count()).isEqualTo(1);
        long id=started.getFirst().id(),question=started.getFirst().currentQuestion().id();
        var same=race(()->practice.answer(id,new AnswerRequest(question,"купя")));
        assertThat(same.get(0)).isEqualTo(same.get(1));assertThat(count("aspect_attempts")).isEqualTo(1);
        SessionResponse a=practice.start(new StartRequest(UUID.randomUUID(),1,null,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,null,"test",false));
        SessionResponse b=practice.start(new StartRequest(UUID.randomUUID(),1,null,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,null,"test",false));
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try{
            var f=executor.submit(()->practice.answer(a.id(),new AnswerRequest(a.currentQuestion().id(),"купя")));
            var g=executor.submit(()->practice.answer(b.id(),new AnswerRequest(b.currentQuestion().id(),"купя")));
            f.get(15,TimeUnit.SECONDS);g.get(15,TimeUnit.SECONDS);
        }finally{executor.shutdownNow();}
        assertThat(count("aspect_attempts")).isEqualTo(3);
        var row=progress.findAll().getFirst();assertThat(row.getTotalAttempts()).isEqualTo(3);
        assertThat(row.getRepetitionInterval()).isEqualTo(7);
    }

    @Test void racingRevealAndAnswerHasOneTerminalOutcomeAndOneScheduleUpdate()throws Exception{
        create("bgasp-057");
        var state=practice.start(new StartRequest(UUID.randomUUID(),1,null,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,null,"test",false));
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Callable<String> answer=()->{gate.await();try{return practice.answer(state.id(),new AnswerRequest(state.currentQuestion().id(),"купя")).currentQuestion().feedback().outcome().name();}
                catch(com.example.bulgariantrainer.exception.AspectPairException e){assertThat(e.getStatus()).isEqualTo(409);return e.getCode();}};
            Callable<String> reveal=()->{gate.await();try{return practice.reveal(state.id(),new QuestionRequest(state.currentQuestion().id())).currentQuestion().feedback().outcome().name();}
                catch(com.example.bulgariantrainer.exception.AspectPairException e){assertThat(e.getStatus()).isEqualTo(409);return e.getCode();}};
            var a=executor.submit(answer);var b=executor.submit(reveal);gate.countDown();
            List<String> outcomes=List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
            if(outcomes.contains("CORRECT"))assertThat(outcomes).containsExactlyInAnyOrder("CORRECT","ANSWER_ALREADY_SUBMITTED");
            else assertThat(outcomes).containsExactlyInAnyOrder("REVEALED","ANSWER_REVEALED");
        }finally{executor.shutdownNow();}
        assertThat(count("aspect_attempts")).isEqualTo(1);
        var p=progress.findAll().getFirst();assertThat(p.getTotalAttempts()).isEqualTo(1);
        assertThat(p.getCorrectAttempts()).isEqualTo(practice.current(state.id()).correctAnswers());
    }

    @Test void separateStatisticsExcludeRevealsAndSkippedAnswersAndCountActiveDuePairsOnce()throws Exception{
        String legacy=get("/api/statistics").toString();
        JsonNode empty=get(BASE+"/statistics");
        for(String field:List.of("answered","correct","incorrect","revealed","dueForReview","dueDirections"))assertThat(empty.path(field).asLong()).isZero();
        assertThat(empty.path("directions").size()).isEqualTo(2);
        var pair=create("bgasp-057");create("bgasp-043");
        var forward=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        answer(forward,"купя",200);answer(forward,"купя",200);
        var reverse=start(1,null,AspectDirection.PERFECTIVE_TO_IMPERFECTIVE,StudyScope.MIXED,false,null);
        answer(reverse,"неверная форма",200);
        var revealed=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        reveal(revealed,200);reveal(revealed,200);
        var second=start(1,List.of(2),AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        answer(second,"прочета",200);
        var skipped=start(1,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,false,null);
        post(path(skipped,"complete"),Map.of(),200,null);
        pairService.create(new AspectPairDtos.Request("other",null,fixture.get("bgasp-057")));
        var other=practice.start(new StartRequest(UUID.randomUUID(),1,null,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,"other",false));
        practice.answer(other.id(),new AnswerRequest(other.currentQuestion().id(),"купя"));
        // Both directions of the same active pair are due at this exact instant.
        jdbc.update("UPDATE aspect_progress SET next_review_at=? WHERE pair_id=?",java.sql.Timestamp.from(NOW),pair.id());
        JsonNode stats=get(BASE+"/statistics");
        assertThat(stats.path("answered").asInt()).isEqualTo(4);
        assertThat(stats.path("correct").asInt()).isEqualTo(3);
        assertThat(stats.path("incorrect").asInt()).isEqualTo(1);
        assertThat(stats.path("revealed").asInt()).isEqualTo(1);
        assertThat(stats.path("dueForReview").asInt()).isEqualTo(1);
        assertThat(stats.path("dueDirections").asInt()).isEqualTo(2);
        assertThat(stats.path("asOf").asText()).isEqualTo(NOW.toString());
        var own=get(BASE+"/statistics?namespace=test");
        assertThat(own.path("answered").asInt()).isEqualTo(3);
        assertThat(own.at("/directions/0/answered").asInt()).isEqualTo(2);
        assertThat(own.at("/directions/1/incorrect").asInt()).isEqualTo(1);
        var otherStats=get(BASE+"/statistics?namespace=other");
        assertThat(otherStats.path("answered").asInt()).isEqualTo(1);
        assertThat(otherStats.path("dueForReview").asInt()).isZero();
        pairService.archive(pair.id(),new AspectPairDtos.ArchiveRequest(true,pairService.get(pair.id()).version()));
        var archived=get(BASE+"/statistics");
        assertThat(archived.path("answered")).isEqualTo(stats.path("answered"));
        assertThat(archived.path("correct")).isEqualTo(stats.path("correct"));
        assertThat(archived.path("dueForReview").asInt()).isZero();
        assertThat(archived.path("dueDirections").asInt()).isZero();
        pairService.archive(pair.id(),new AspectPairDtos.ArchiveRequest(false,pairService.get(pair.id()).version()));
        assertThat(get(BASE+"/statistics").path("dueDirections").asInt()).isEqualTo(2);
        assertThat(get("/api/statistics").toString()).isEqualTo(legacy);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(BASE+"/statistics").param("namespace","INVALID NAMESPACE")).andExpect(status().isBadRequest());
    }

    private AspectPairDtos.Response create(String id){return pairService.create(new AspectPairDtos.Request("test",null,fixture.get(id)));}
    private JsonNode start(int size,List<Integer> stages,AspectDirection direction,StudyScope scope,boolean shorter,String topic)throws Exception{
        return post(BASE+"/sessions",new StartRequest(UUID.randomUUID(),size,stages,topic,direction,scope,"test",shorter),201,null);
    }
    private JsonNode answer(JsonNode state,String answer,int status)throws Exception{return post(path(state,"answers"),Map.of("questionId",state.at("/currentQuestion/id").asLong(),"answer",answer),status,null);}
    private JsonNode reveal(JsonNode state,int status)throws Exception{return post(path(state,"reveal"),Map.of("questionId",state.at("/currentQuestion/id").asLong()),status,null);}
    private JsonNode next(JsonNode state,int status)throws Exception{return post(path(state,"next"),Map.of("questionId",state.at("/currentQuestion/id").asLong()),status,null);}
    private String path(JsonNode state,String action){return BASE+"/sessions/"+state.path("id").asLong()+"/"+action;}
    private JsonNode get(String path)throws Exception{return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private JsonNode post(String path,Object body,int expected,String code)throws Exception{
        var result=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().is(expected));
        if(code!=null)result.andExpect(jsonPath("$.code").value(code));
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private long count(String table){return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class);}
    private <T> List<T> race(Callable<T> operation)throws Exception{
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try{
            Callable<T> run=()->{gate.await();return operation.call();};
            Future<T> a=executor.submit(run),b=executor.submit(run);gate.countDown();
            return List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();}
    }
}
