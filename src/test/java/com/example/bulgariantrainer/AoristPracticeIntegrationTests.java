package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AoristDtos;
import com.example.bulgariantrainer.dto.AoristPracticeDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.exception.AoristException;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.*;
import tools.jackson.databind.node.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:aorist-practice;DB_CLOSE_DELAY=-1","training.aorist.random-seed=42"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(AoristPracticeIntegrationTests.TimeConfiguration.class)
class AoristPracticeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AoristJson aoristJson;
    @Autowired AoristVerbService content;
    @Autowired AoristImportService imports;
    @Autowired AoristPracticeService practice;
    @Autowired AoristQuestionRepository questions;
    @Autowired AoristProgressRepository progress;
    @Autowired SpacedRepetitionScheduler scheduler;
    @Autowired TestClock clock;
    private static final String BASE="/api/aorist-practice";
    private static final Instant NOW=Instant.parse("2026-11-17T12:00:00Z");
    private ObjectNode document;
    private final Map<String,ObjectNode> fixture=new HashMap<>();

    @TestConfiguration static class TimeConfiguration { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    static class TestClock extends Clock {
        private final AtomicReference<Instant> at=new AtomicReference<>(NOW);
        void set(Instant value) { at.set(value); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        @Override public Instant instant() { return at.get(); }
    }
    @BeforeEach void setup() throws Exception {
        for(String table:List.of("aorist_attempts","aorist_questions","aorist_sessions","aorist_import_receipts","aorist_progress","aorist_forms","aorist_verbs","aorist_datasets")) jdbc.update("DELETE FROM "+table);
        clock.set(NOW); document=(ObjectNode)json.readTree(Files.readAllBytes(Path.of("learning-materials/aorist/bulgarian_aorist_40.json")));
        for(var e:document.path("entries")) fixture.put(e.path("lemma").asText(),(ObjectNode)e);
    }

    @ParameterizedTest @CsvSource(delimiter='|',value={
        "чета|1pl|четохме|CORRECT", "прочета|1pl|прочетохме|CORRECT", "чета|1pl|прочетохме|INCORRECT",
        "взема|1sg|взех|CORRECT", "вземам|1sg|вземах|CORRECT", "видя|1pl|видяхме|CORRECT",
        "изляза|3sg|излезе|CORRECT", "облека се|1sg|облякох се|CORRECT",
        "облека се|1sg|облякох|INCORRECT", "облека се|1sg|Аз облякох се|INCORRECT",
        "облека се|1sg|Аз се облякох|INCORRECT", "чета|1pl|четох|INCORRECT",
        "чета|1pl|четохме!|INCORRECT", "чета|1pl|четохм|INCORRECT", "чета|1pl|чeтохме|INCORRECT",
        "изляза|3sg|излязе|INCORRECT", "взема|1sg|вземах|INCORRECT"})
    void validatesExplicitVerbAndPersonAnswersOnly(String lemma,String person,String answer,String outcome) throws Exception {
        create(lemma); var state=start(AoristExerciseType.TYPED,List.of(person),1,false,StudyScope.MIXED);
        var result=answer(state,answer,200);
        assertThat(result.at("/currentQuestion/feedback/outcome").asText()).isEqualTo(outcome);
        assertThat(result.at("/currentQuestion/feedback/acceptedAnswers")).isEqualTo(fixture.get(lemma).path("forms").get(person));
        assertThat(count("aorist_attempts")).isEqualTo(1); assertThat(progress.findAll()).hasSize(1);
        assertThat(result.path("correctAnswers").asInt()).isEqualTo(outcome.equals("CORRECT")?1:0);
        if(outcome.equals("INCORRECT")) assertThat(result.at("/currentQuestion/feedback/message").asText()).isEqualTo("Не совпало с ответами этой карточки");
    }

    @Test void defaultIsTypedAzAndPromptSeparatesPersonFromReflexiveAnswer() throws Exception {
        create("облека се");
        var state=post(BASE+"/sessions",Map.of("requestId",UUID.randomUUID(),"size",1),201);
        assertThat(state.at("/settings/exerciseType").asText()).isEqualTo("TYPED");
        assertThat(state.at("/settings/persons")).isEqualTo(json.readTree("[\"1sg\"]"));
        assertThat(state.at("/currentQuestion/prompt/lemma").asText()).isEqualTo("облека се");
        assertThat(state.at("/currentQuestion/prompt/russianMeaning").asText()).isEqualTo("одеться");
        assertThat(state.at("/currentQuestion/prompt/tense").asText()).isEqualTo("aorist");
        assertThat(state.at("/currentQuestion/prompt/tenseLabel").asText()).isEqualTo("Минало свършено време");
        assertThat(state.at("/currentQuestion/prompt/personLabel").asText()).isEqualTo("аз");
        assertThat(state.at("/currentQuestion/prompt/answerScope").asText()).contains("без подлежащего");
        assertThat(state.toString()).doesNotContain("облякох", "Аз облякох се", "Аз се облякох");
    }

    @Test void everySuppliedPersonAnswerIsAcceptedAcrossNewQueuesWithoutPadding() throws Exception {
        importAll(); int total=0;
        while(getJson(BASE+"/availability?persons=1sg,2sg,3sg,1pl,2pl,3pl&scope=NEW").path("eligibleQuestions").asInt()>0) {
            var state=start(AoristExerciseType.TYPED,AoristValidation.PERSONS,100,true,StudyScope.NEW);
            while(!state.path("currentQuestion").isNull()) {
                String lemma=state.at("/currentQuestion/prompt/lemma").asText(), person=state.at("/currentQuestion/prompt/person").asText();
                String answer=fixture.get(lemma).path("forms").get(person).get(0).asText();
                var graded=answer(state,answer,200); assertThat(graded.at("/currentQuestion/feedback/outcome").asText()).isEqualTo("CORRECT"); total++;
                state=post(BASE+"/sessions/"+state.path("id").asLong()+"/next",Map.of("questionId",qid(state)),200);
            }
        }
        assertThat(total).isEqualTo(240); assertThat(count("aorist_attempts")).isEqualTo(240); assertThat(progress.count()).isEqualTo(240);
        assertThat(progress.findAll()).allMatch(p->p.getTotalAttempts()==1 && p.getCorrectAttempts()==1 && p.getExerciseType()==AoristExerciseType.TYPED);
    }

    @Test void startRetriesCanonicalizePersonsAndReturnCurrentStateButRejectChangedSettings() throws Exception {
        create("чета"); UUID requestId=UUID.randomUUID();
        var request=new StartRequest(requestId,2,AoristExerciseType.TYPED,List.of("1pl","1sg"),null,null,StudyScope.NEW,"practice",false);
        var first=post(BASE+"/sessions",request,201);
        var reordered=new StartRequest(requestId,2,AoristExerciseType.TYPED,List.of("1sg","1pl"),null,null,StudyScope.NEW,"practice",false);
        assertThat(post(BASE+"/sessions",reordered,201)).isEqualTo(first);
        var changed=new StartRequest(requestId,2,AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg","1pl"),null,null,StudyScope.NEW,"practice",false);
        post(BASE+"/sessions",changed,409); assertThat(count("aorist_sessions")).isEqualTo(1);
        var future=questions.findBySessionIdOrderByPositionAsc(first.path("id").asLong()).get(1);
        post(BASE+"/sessions/"+first.path("id").asLong()+"/answers",new AnswerRequest(future.getId(),"четох",null),409);
        var graded=answer(first,"не совпало",200);
        var next=post(BASE+"/sessions/"+first.path("id").asLong()+"/next",Map.of("questionId",qid(first)),200);
        assertThat(post(BASE+"/sessions",reordered,201)).isEqualTo(next);
        assertThat(answer(first,"не совпало",200)).isEqualTo(next); assertThat(graded.path("currentPosition").asInt()).isEqualTo(1);
    }

    @Test void normalizationAllowsStressCaseAndWhitespaceButPreservesLettersParticlesAndAlternatives() throws Exception {
        create("чета");
        assertThat(answer(start(AoristExerciseType.TYPED,List.of("1pl"),1,false,StudyScope.MIXED)," \tЧЕТО́ХМЕ\u00a0\n",200).path("correctAnswers").asInt()).isEqualTo(1);
        assertThat(answer(start(AoristExerciseType.TYPED,List.of("1pl"),1,false,StudyScope.MIXED),"ЧЕТО̀ХМЕ",200).path("correctAnswers").asInt()).isEqualTo(1);
        for(String target:List.of("и","й","ѝ")) {
            ObjectNode entry=fixture.get("чета").deepCopy(); entry.put("id","letter-"+(int)target.charAt(0)); entry.put("studyGroup","letter-"+(int)target.charAt(0));
            ((ObjectNode)entry.path("forms")).putArray("1sg").add(target);
            createEntry(entry);
            for(String given:List.of("и","й","ѝ","и\u0306","и\u0300")) {
                var state=rawStart(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.MIXED,entry.path("studyGroup").asText(),null,201);
                var r=answer(state,given,200);
                boolean expected=java.text.Normalizer.normalize(given,java.text.Normalizer.Form.NFC).equals(target);
                assertThat(r.path("correctAnswers").asInt()).as(target+" vs "+given).isEqualTo(expected?1:0);
            }
        }
        ObjectNode reflexive=fixture.get("облека се").deepCopy(); reflexive.put("id","synthetic-si"); reflexive.put("lemma","облека си"); reflexive.put("reflexiveParticle","си"); reflexive.put("studyGroup","si-fixture");
        for(String person:AoristValidation.PERSONS) {
            String original=reflexive.path("forms").get(person).get(0).asText();
            ((ObjectNode)reflexive.path("forms")).putArray(person).add(original.replace(" се"," си"));
        }
        createEntry(reflexive);
        var si=rawStart(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.MIXED,"si-fixture",null,201);
        assertThat(answer(si,"облякох си",200).path("correctAnswers").asInt()).isEqualTo(1);
        si=rawStart(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.MIXED,"si-fixture",null,201);
        assertThat(answer(si,"облякох",200).path("correctAnswers").asInt()).isZero();
        ObjectNode alternatives=fixture.get("чета").deepCopy(); alternatives.put("id","alternative-fixture"); alternatives.put("studyGroup","alternatives");
        ((ArrayNode)alternatives.path("forms").get("1sg")).add("алтернативен отговор"); createEntry(alternatives);
        var alt=rawStart(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.MIXED,"alternatives",null,201);
        assertThat(answer(alt,"алтернативен отговор",200).path("correctAnswers").asInt()).isEqualTo(1);
    }

    @Test void availabilityFiltersAllPersonsShortagesAndUniqueVerbPersonQuestions() throws Exception {
        importAll();
        assertThat(getJson(BASE+"/availability?size=100").path("eligibleQuestions").asInt()).isEqualTo(40);
        assertThat(getJson(BASE+"/availability?persons=1pl,3sg&aspect=imperfective").path("eligibleQuestions").asInt()).isEqualTo(22);
        assertThat(getJson(BASE+"/availability?persons=1sg,2sg,3sg,1pl,2pl,3pl").path("eligibleQuestions").asInt()).isEqualTo(240);
        assertThat(getJson(BASE+"/availability?studyGroup=2_ih").path("eligibleQuestions").asInt()).isEqualTo(9);
        assertThat(getJson(BASE+"/availability?datasetId=absent").path("eligibleQuestions").asInt()).isZero();
        var rows=content.list(null,null,null,null,null,false,0,100).content();
        var archived=rows.getFirst(); content.archive(archived.id(),new AoristDtos.ArchiveRequest(archived.version(),true));
        assertThat(getJson(BASE+"/availability").path("eligibleQuestions").asInt()).isEqualTo(39);
        rawStart(AoristExerciseType.TYPED,List.of("1sg"),40,false,StudyScope.MIXED,null,null,409);
        var shortSession=start(AoristExerciseType.TYPED,List.of("1sg"),40,true,StudyScope.MIXED);
        assertThat(shortSession.path("totalQuestions").asInt()).isEqualTo(39);
        var all=start(AoristExerciseType.TYPED,AoristValidation.PERSONS,100,false,StudyScope.MIXED);
        var queue=questions.findBySessionIdOrderByPositionAsc(all.path("id").asLong());
        assertThat(queue).hasSize(100);
        assertThat(queue.stream().map(q->q.getVerb().getId()+"/"+q.getPerson()).distinct().count()).isEqualTo(100);
        assertThat(queue.stream().map(q->q.getVerb().getId()).distinct().count()).isLessThan(100);
        assertThat(queue).noneMatch(q->q.getVerb().getId().equals(archived.id()));
        assertThat(getJson(BASE+"/sessions?size=1").path("totalElements").asInt()).isEqualTo(2);
    }

    @ParameterizedTest @ValueSource(strings={"1sg","2sg","3sg","1pl","2pl","3pl"})
    void choiceOptionsUseStoredFormsAndHaveExactlyOneAcceptedOption(String person) throws Exception {
        create("чета"); var state=start(AoristExerciseType.MULTIPLE_CHOICE,List.of(person),1,false,StudyScope.NEW);
        var options=state.at("/currentQuestion/options"); assertThat(options.size()).isEqualTo(4);
        var accepted=fixture.get("чета").path("forms").get(person);
        Set<String> texts=new HashSet<>(),stored=new HashSet<>();
        fixture.get("чета").path("forms").forEach(a->a.forEach(t->stored.add(t.asText())));
        int correct=0; JsonNode correctOption=null,wrong=null;
        for(var option:options) {
            assertThat(option.properties()).hasSize(2);
            assertThat(texts.add(option.path("text").asText())).isTrue(); assertThat(stored).contains(option.path("text").asText());
            assertThatCode(()->UUID.fromString(option.path("optionId").asText())).doesNotThrowAnyException();
            boolean isCorrect=false; for(var a:accepted) if(a.asText().equals(option.path("text").asText())) isCorrect=true;
            if(isCorrect) { correct++; correctOption=option; } else wrong=option;
        }
        assertThat(correct).isEqualTo(1);
        assertThat(state.at("/currentQuestion/feedback").isNull()).isTrue();
        var answered=choose(state,correctOption.path("optionId").asText(),200);
        assertThat(answered.path("correctAnswers").asInt()).isEqualTo(1);
        assertThat(answered.path("independentRecallCorrect").asInt()).isZero();
        assertThat(choose(state,correctOption.path("optionId").asText(),200)).isEqualTo(answered);
        choose(state,wrong.path("optionId").asText(),409);
        assertThat(progress.findAll().getFirst().getExerciseType()).isEqualTo(AoristExerciseType.MULTIPLE_CHOICE);
    }

    @Test void choiceExcludesAllAcceptedVariantsAndHandlesReducedOrUnavailableOptionsExplicitly() throws Exception {
        var entry=fixture.get("чета").deepCopy(); entry.put("id","choice-alt"); entry.put("studyGroup","choice-alt");
        ((ArrayNode)entry.path("forms").get("2sg")).add("прочете");
        ((ArrayNode)entry.path("forms").get("3sg")).add("прочете"); createEntry(entry);
        var state=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("2sg"),1,false,StudyScope.MIXED);
        var options=state.at("/currentQuestion/options"); assertThat(options.toString()).doesNotContain("прочете");
        assertThat(options).filteredOn(o->o.path("text").asText().equals("чете")).hasSize(1);
        var reduced=fixture.get("чета").deepCopy(); reduced.put("id","reduced"); reduced.put("studyGroup","reduced");
        for(String p:AoristValidation.PERSONS) ((ObjectNode)reduced.path("forms")).putArray(p).add(p.equals("1sg")?"четох":"чете");
        ((ObjectNode)reduced.path("forms")).putArray("2sg").add(" \u00a0ЧЕ́ТЕ ");
        createEntry(reduced);
        state=rawStart(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.NEW,"reduced",null,201);
        assertThat(state.at("/currentQuestion/options").size()).isEqualTo(2);
        assertThat(state.at("/currentQuestion/choiceNotice").asText()).contains("2","ввод ответа");
        assertThat(getJson(BASE+"/availability?exerciseType=MULTIPLE_CHOICE&studyGroup=reduced").path("reducedOptionQuestions").asInt()).isEqualTo(1);
        var only=fixture.get("чета").deepCopy(); only.put("id","only"); only.put("studyGroup","only");
        for(String p:AoristValidation.PERSONS) ((ObjectNode)only.path("forms")).putArray(p).add("чете"); createEntry(only);
        var availability=getJson(BASE+"/availability?exerciseType=MULTIPLE_CHOICE&studyGroup=only");
        assertThat(availability.path("eligibleQuestions").asInt()).isZero();
        assertThat(availability.path("choiceUnavailableQuestions").asInt()).isEqualTo(1);
        assertThat(availability.path("typedAlternativeQuestions").asInt()).isEqualTo(1);
        assertThat(rawStart(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.MIXED,"only",null,409).path("code").asText()).isEqualTo("CHOICE_OPTIONS_UNAVAILABLE");
        rawStart(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.NEW,"only",null,201);
    }

    @Test void wrongChoiceIsWrongForThisQuestionAndRevealOnlyHasNoAccuracyOrRecallCredit() throws Exception {
        create("чета");
        var choice=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("2sg"),1,false,StudyScope.NEW);
        String wrong=null; for(var o:choice.at("/currentQuestion/options")) if(!o.path("text").asText().equals("чете")) wrong=o.path("optionId").asText();
        var graded=choose(choice,wrong,200);
        assertThat(graded.at("/currentQuestion/feedback/outcome").asText()).isEqualTo("INCORRECT");
        assertThat(graded.at("/currentQuestion/feedback/explanation").asText()).contains("этому вопросу","существующей болгарской формой");
        assertThat(graded.path("correctAnswers").asInt()).isZero(); assertThat(graded.path("independentRecallCorrect").asInt()).isZero();
        assertThat(progress.findAll()).allMatch(p->p.getExerciseType()==AoristExerciseType.MULTIPLE_CHOICE && p.getCorrectAttempts()==0);
        for(var type:AoristExerciseType.values()) {
            var state=start(type,List.of("1sg"),1,false,StudyScope.NEW);
            var revealed=post(BASE+"/sessions/"+state.path("id").asLong()+"/reveal",Map.of("questionId",qid(state)),200);
            assertThat(revealed.path("answeredQuestions").asInt()).isZero(); assertThat(revealed.path("correctAnswers").asInt()).isZero();
            assertThat(revealed.path("independentRecallCorrect").asInt()).isZero();
            var results=getJson(BASE+"/sessions/"+state.path("id").asLong()+"/results"); assertThat(results.path("accuracyPercent").isNull()).isTrue();
        }
    }

    @Test void practiceApisNeverSerializePrivateContentOrFuturePersonAnswers() throws Exception {
        var created=create("чета"); var state=start(AoristExerciseType.TYPED,List.of("1pl","2pl"),2,false,StudyScope.NEW);
        assertHidden(state); assertHidden(getJson(BASE+"/sessions/"+state.path("id").asLong()+"/question"));
        var summaries=getJson(BASE+"/sessions"); assertThat(summaries.toString()).doesNotContain("четох", "forms", "snapshot", "acceptedAnswers", "source");
        post(BASE+"/sessions/"+state.path("id").asLong()+"/next",Map.of("questionId",qid(state)),409);
        mvc.perform(get(BASE+"/sessions/"+state.path("id").asLong()+"/results")).andExpect(status().isConflict());
        String person=state.at("/currentQuestion/prompt/person").asText(); String expected=created.entry().forms().get(person).getFirst();
        var answered=answer(state,expected,200);
        assertThat(answered.at("/currentQuestion/feedback/acceptedAnswers").size()).isEqualTo(1);
        assertThat(answered.toString()).doesNotContain("note", "source", "forms", "snapshot", "checkedOn");
        var second=post(BASE+"/sessions/"+state.path("id").asLong()+"/next",Map.of("questionId",qid(state)),200); assertHidden(second);
        post(BASE+"/sessions/"+state.path("id").asLong()+"/complete",Map.of(),200);
        var results=getJson(BASE+"/sessions/"+state.path("id").asLong()+"/results");
        assertThat(results.at("/items/1/outcome").asText()).isEqualTo("SKIPPED"); assertThat(results.at("/items/1/feedback").isNull()).isTrue();
    }

    @Test void exactRetriesRevealAndNextAreTerminalAndIdempotentWithSeparateResults() throws Exception {
        create("чета"); var state=start(AoristExerciseType.TYPED,List.of("1sg","1pl","2pl"),3,false,StudyScope.NEW); long id=state.path("id").asLong();
        var wrong=answer(state,"не совпало",200); assertThat(wrong.path("currentPosition").asInt()).isEqualTo(1);
        assertThat(answer(state,"не совпало",200)).isEqualTo(wrong); answer(state,"друго",409);
        var next=post(BASE+"/sessions/"+id+"/next",Map.of("questionId",qid(state)),200);
        assertThat(post(BASE+"/sessions/"+id+"/next",Map.of("questionId",qid(state)),200)).isEqualTo(next);
        var reveal=post(BASE+"/sessions/"+id+"/reveal",Map.of("questionId",qid(next)),200);
        assertThat(reveal.path("correctAnswers").asInt()).isZero(); assertThat(reveal.path("answeredQuestions").asInt()).isEqualTo(1);
        assertThat(reveal.path("revealedQuestions").asInt()).isEqualTo(1);
        assertThat(post(BASE+"/sessions/"+id+"/reveal",Map.of("questionId",qid(next)),200)).isEqualTo(reveal);
        String revealedAnswer=reveal.at("/currentQuestion/feedback/acceptedAnswers/0").asText(); answer(next,revealedAnswer,409);
        post(BASE+"/sessions/"+id+"/complete",Map.of(),200); post(BASE+"/sessions/"+id+"/complete",Map.of(),200);
        var results=getJson(BASE+"/sessions/"+id+"/results");
        assertThat(results.at("/session/status").asText()).isEqualTo("ABANDONED");
        assertThat(results.path("mistakes").size()).isEqualTo(1); assertThat(results.path("accuracyPercent").asDouble()).isZero();
        assertThat(count("aorist_attempts")).isEqualTo(2); assertThat(progress.findAll()).hasSize(2);
        for(var p:progress.findAll()) {
            assertThat(p.getCorrectAttempts()).isZero(); assertThat(p.getConsecutiveCorrect()).isZero();
            assertThat(p.getNextReviewAt()).isEqualTo(NOW.plus(Duration.ofDays(1))); assertThat(p.getRepetitionInterval()).isEqualTo(1);
        }
        answer(state,"не совпало",200); post(BASE+"/sessions/"+id+"/reveal",Map.of("questionId",qid(next)),200);
        assertThat(count("aorist_attempts")).isEqualTo(2);
    }

    @Test void schedulesAreIndependentByVerbPersonAndTypeAndUseExistingIntervalsAndDueBoundary() throws Exception {
        var verb=create("чета"); var legacy=getJson("/api/statistics"); var aspect=getJson("/api/aspect-pair-practice/statistics").path("answered");
        var state=start(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.NEW); answer(state,"четох",200);
        var typed=progress.findByVerbIdAndPersonAndExerciseType(verb.id(),"1sg",AoristExerciseType.TYPED).orElseThrow();
        assertThat(typed.getRepetitionInterval()).isEqualTo(1);
        assertThat(getJson(BASE+"/availability?scope=NEW").path("eligibleQuestions").asInt()).isZero();
        assertThat(getJson(BASE+"/availability?exerciseType=MULTIPLE_CHOICE&scope=NEW").path("eligibleQuestions").asInt()).isEqualTo(1);
        for(int i=0;i<5;i++) {
            var choice=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.MIXED);
            choose(choice,optionFor(choice,"четох"),200);
        }
        var recognition=progress.findByVerbIdAndPersonAndExerciseType(verb.id(),"1sg",AoristExerciseType.MULTIPLE_CHOICE).orElseThrow();
        assertThat(recognition.getRepetitionInterval()).isEqualTo(28); assertThat(scheduler.category(recognition,NOW)).isEqualTo(LearningCategory.MASTERED);
        typed=progress.findByVerbIdAndPersonAndExerciseType(verb.id(),"1sg",AoristExerciseType.TYPED).orElseThrow();
        assertThat(typed.getTotalAttempts()).isEqualTo(1); assertThat(scheduler.category(typed,NOW)).isEqualTo(LearningCategory.LEARNING);
        assertThat(getJson(BASE+"/availability?scope=NEW&persons=2sg").path("eligibleQuestions").asInt()).isEqualTo(1);
        clock.set(NOW.plus(Duration.ofDays(1)).minusNanos(1));
        assertThat(getJson(BASE+"/availability?scope=DUE").path("eligibleQuestions").asInt()).isZero();
        clock.set(NOW.plus(Duration.ofDays(1)));
        assertThat(getJson(BASE+"/availability?scope=DUE").path("eligibleQuestions").asInt()).isEqualTo(1);
        assertThat(getJson(BASE+"/availability?scope=DUE&exerciseType=MULTIPLE_CHOICE").path("eligibleQuestions").asInt()).isZero();
        answer(start(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.DUE),"четох",200);
        typed=progress.findByVerbIdAndPersonAndExerciseType(verb.id(),"1sg",AoristExerciseType.TYPED).orElseThrow(); assertThat(typed.getRepetitionInterval()).isEqualTo(3);
        var revealed=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.MIXED);
        post(BASE+"/sessions/"+revealed.path("id").asLong()+"/reveal",Map.of("questionId",qid(revealed)),200);
        recognition=progress.findByVerbIdAndPersonAndExerciseType(verb.id(),"1sg",AoristExerciseType.MULTIPLE_CHOICE).orElseThrow();
        assertThat(recognition.getCorrectAttempts()).isEqualTo(5); assertThat(recognition.getRepetitionInterval()).isEqualTo(1); assertThat(recognition.getConsecutiveCorrect()).isZero();
        assertThat(getJson(BASE+"/progress?exerciseType=TYPED&person=1sg").size()).isEqualTo(1);
        clock.set(NOW); assertThat(getJson("/api/statistics")).isEqualTo(legacy); assertThat(getJson("/api/aspect-pair-practice/statistics").path("answered")).isEqualTo(aspect);
    }

    @Test void editingArchivingAndReimportPreserveSnapshotsAndExistingSchedules() throws Exception {
        var verb=create("чета"); var state=start(AoristExerciseType.TYPED,List.of("1sg","1pl"),2,false,StudyScope.NEW);
        String person=state.at("/currentQuestion/prompt/person").asText(); String original=verb.entry().forms().get(person).getFirst();
        ObjectNode changed=fixture.get("чета").deepCopy(); changed.put("note","Future answers: четохме"); ((ObjectNode)changed.path("forms")).putArray(person).add("нов отговор");
        verb=content.update(verb.id(),new AoristDtos.Request("practice",verb.version(),changed,null)); content.archive(verb.id(),new AoristDtos.ArchiveRequest(verb.version(),true));
        assertThat(answer(state,original,200).path("correctAnswers").asInt()).isEqualTo(1);
        assertThat(getJson(BASE+"/availability").path("eligibleQuestions").asInt()).isZero();
        assertThat(progress.findAll()).hasSize(1);
        var next=post(BASE+"/sessions/"+state.path("id").asLong()+"/next",Map.of("questionId",qid(state)),200);
        assertThat(next.at("/currentQuestion/prompt/lemma").asText()).isEqualTo("чета"); assertHidden(next);
    }

    @Test void rollbackAfterAttemptInsertRestoresScheduleQuestionAndSessionCounters() throws Exception {
        create("чета"); var state=start(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.NEW);
        jdbc.execute("ALTER TABLE aorist_attempts ADD CONSTRAINT test_reject_answer CHECK(user_answer <> 'четох')");
        try {
            answer(state,"четох",409);
            assertThat(count("aorist_attempts")).isZero(); assertThat(progress.count()).isZero();
            assertThat(getJson(BASE+"/sessions/"+state.path("id").asLong()+"/question")).isEqualTo(state);
        } finally { jdbc.execute("ALTER TABLE aorist_attempts DROP CONSTRAINT test_reject_answer"); }
        assertThat(answer(state,"четох",200).path("correctAnswers").asInt()).isEqualTo(1);
    }

    @Test void concurrentStartsAnswersRevealsAndCrossSessionUpdatesCannotDoubleCredit() throws Exception {
        create("чета"); var request=new StartRequest(UUID.randomUUID(),1,AoristExerciseType.TYPED,List.of("1sg"),null,null,StudyScope.MIXED,"practice",false);
        var starts=race(()->practice.start(request),()->practice.start(request)); assertThat(starts.get(0)).isEqualTo(starts.get(1)); assertThat(count("aorist_sessions")).isEqualTo(1);
        var state=starts.getFirst(); var response=race(()->practice.answer(state.id(),new AnswerRequest(state.currentQuestion().id(),"четох",null)),
                ()->practice.answer(state.id(),new AnswerRequest(state.currentQuestion().id(),"четох",null)));
        assertThat(response.get(0)).isEqualTo(response.get(1)); assertThat(progress.findAll().getFirst().getTotalAttempts()).isEqualTo(1);
        var a=practice.start(new StartRequest(UUID.randomUUID(),1,AoristExerciseType.TYPED,List.of("1sg"),null,null,StudyScope.MIXED,"practice",false));
        var b=practice.start(new StartRequest(UUID.randomUUID(),1,AoristExerciseType.TYPED,List.of("1sg"),null,null,StudyScope.MIXED,"practice",false));
        race(()->practice.answer(a.id(),new AnswerRequest(a.currentQuestion().id(),"четох",null)),()->practice.answer(b.id(),new AnswerRequest(b.currentQuestion().id(),"четох",null)));
        assertThat(progress.findAll().getFirst().getTotalAttempts()).isEqualTo(3); assertThat(progress.findAll().getFirst().getRepetitionInterval()).isEqualTo(7);
        var c=practice.start(new StartRequest(UUID.randomUUID(),1,AoristExerciseType.TYPED,List.of("1sg"),null,null,StudyScope.MIXED,"practice",false));
        var outcomes=race(()->attemptAction(()->practice.answer(c.id(),new AnswerRequest(c.currentQuestion().id(),"четох",null))),
                ()->attemptAction(()->practice.reveal(c.id(),new QuestionRequest(c.currentQuestion().id()))));
        assertThat(outcomes).containsExactlyInAnyOrder(200,409); assertThat(count("aorist_attempts")).isEqualTo(4); assertThat(progress.findAll().getFirst().getTotalAttempts()).isEqualTo(4);
        var d=practice.start(new StartRequest(UUID.randomUUID(),1,AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),null,null,StudyScope.MIXED,"practice",false));
        UUID option=d.currentQuestion().options().stream().filter(o->o.text().equals("четох")).findFirst().orElseThrow().optionId();
        race(()->practice.answer(d.id(),new AnswerRequest(d.currentQuestion().id(),null,option)),()->practice.answer(d.id(),new AnswerRequest(d.currentQuestion().id(),null,option)));
        var e=practice.start(new StartRequest(UUID.randomUUID(),1,AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),null,null,StudyScope.MIXED,"practice",false));
        race(()->practice.reveal(e.id(),new QuestionRequest(e.currentQuestion().id())),()->practice.reveal(e.id(),new QuestionRequest(e.currentQuestion().id())));
        assertThat(progress.findAll()).hasSize(2); assertThat(count("aorist_attempts")).isEqualTo(6);
        assertThat(progress.findAll().stream().filter(p->p.getExerciseType()==AoristExerciseType.MULTIPLE_CHOICE).findFirst().orElseThrow().getTotalAttempts()).isEqualTo(2);
    }

    @Test void invalidSettingsPayloadsForeignOptionsQuestionsAndClosedSessionsDoNotWrite() throws Exception {
        create("чета");
        for(Object persons:List.of(List.of(),List.of("1sg","1sg"),List.of("аз"),List.of("3sg_f"))) post(BASE+"/sessions",Map.of("requestId",UUID.randomUUID(),"persons",persons),400);
        for(String query:List.of("persons=invalid","aspect=both","size=101","size=0","exerciseType=CHOICE","scope=UNKNOWN","datasetId=Bad","studyGroup="))
            mvc.perform(get(BASE+"/availability?"+query)).andExpect(status().isBadRequest());
        var state=start(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.NEW);
        post(BASE+"/sessions/"+state.path("id").asLong()+"/answers",Map.of("questionId",qid(state),"optionId",UUID.randomUUID()),400);
        answer(state,"\u00a0 \t",400);
        var choice=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.NEW);
        answer(choice,"четох",400); choose(choice,UUID.randomUUID().toString(),400);
        post(BASE+"/sessions/"+choice.path("id").asLong()+"/answers",Map.of("questionId",qid(state),"optionId",choice.at("/currentQuestion/options/0/optionId").asText()),409);
        var other=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.NEW);
        choose(choice,other.at("/currentQuestion/options/0/optionId").asText(),400);
        assertThat(count("aorist_attempts")).isZero(); assertThat(progress.count()).isZero();
        post(BASE+"/sessions/"+state.path("id").asLong()+"/complete",Map.of(),200); answer(state,"четох",409);
        mvc.perform(get(BASE+"/sessions/999999/question")).andExpect(status().isNotFound());
        var result=getJson(BASE+"/sessions/"+state.path("id").asLong()+"/results"); assertThat(result.path("accuracyPercent").isNull()).isTrue();
    }

    @Test void fullExplanationsAreSnapshotBasedAndCannotDiscloseUnansweredOrSkippedPersonForms() throws Exception {
        var verb=create("чета");
        var one=start(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.NEW);
        String firstUrl=BASE+"/sessions/"+one.path("id").asLong()+"/questions/"+qid(one)+"/explanation";
        mvc.perform(get(firstUrl)).andExpect(status().isConflict());
        answer(one,"четох",200);
        var detail=getJson(firstUrl); assertThat(detail.path("available").asBoolean()).isTrue();
        assertThat(detail.path("forms")).isEqualTo(fixture.get("чета").path("forms"));
        assertThat(detail.path("note").asText()).isEqualTo(fixture.get("чета").path("note").asText());
        var two=start(AoristExerciseType.TYPED,List.of("1sg","1pl"),2,false,StudyScope.MIXED);
        String person=two.at("/currentQuestion/prompt/person").asText(); answer(two,verb.entry().forms().get(person).getFirst(),200);
        String guarded=BASE+"/sessions/"+two.path("id").asLong()+"/questions/"+qid(two)+"/explanation";
        var withheld=getJson(guarded); assertThat(withheld.path("available").asBoolean()).isFalse();
        assertThat(withheld.path("forms").isNull()).isTrue(); assertThat(withheld.path("note").isNull()).isTrue(); assertThat(withheld.path("source").isNull()).isTrue();
        post(BASE+"/sessions/"+two.path("id").asLong()+"/complete",Map.of(),200);
        assertThat(getJson(guarded)).isEqualTo(withheld);
        var all=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg","1pl"),2,false,StudyScope.MIXED);
        var changed=fixture.get("чета").deepCopy(); changed.put("note","Changed after snapshot");
        content.update(verb.id(),new AoristDtos.Request("practice",verb.version(),changed,null));
        post(BASE+"/sessions/"+all.path("id").asLong()+"/reveal",Map.of("questionId",qid(all)),200);
        var next=post(BASE+"/sessions/"+all.path("id").asLong()+"/next",Map.of("questionId",qid(all)),200);
        post(BASE+"/sessions/"+all.path("id").asLong()+"/reveal",Map.of("questionId",qid(next)),200);
        var before=getJson(BASE+"/progress");
        detail=getJson(BASE+"/sessions/"+all.path("id").asLong()+"/questions/"+qid(all)+"/explanation");
        assertThat(detail.path("available").asBoolean()).isTrue(); assertThat(detail.path("note").asText()).isEqualTo(verb.entry().note());
        assertThat(getJson(BASE+"/progress")).isEqualTo(before);
    }

    @Test void statisticsSeparatePersonsTypesRevealsAndRetainArchivedHistory() throws Exception {
        var legacy=getJson("/api/statistics"); var aspect=getJson("/api/aspect-pair-practice/statistics");
        var verb=create("чета");
        var typed=start(AoristExerciseType.TYPED,List.of("1sg"),1,false,StudyScope.MIXED);
        answer(typed,"четох",200); answer(typed,"четох",200);
        var wrong=start(AoristExerciseType.TYPED,List.of("1pl"),1,false,StudyScope.MIXED); answer(wrong,"прочетохме",200);
        var revealed=start(AoristExerciseType.TYPED,List.of("2sg"),1,false,StudyScope.MIXED);
        String revealUrl=BASE+"/sessions/"+revealed.path("id").asLong()+"/reveal";
        post(revealUrl,new QuestionRequest(qid(revealed)),200); post(revealUrl,new QuestionRequest(qid(revealed)),200);
        var choice=start(AoristExerciseType.MULTIPLE_CHOICE,List.of("1sg"),1,false,StudyScope.MIXED);
        choose(choice,optionFor(choice,"четох"),200);
        var skipped=start(AoristExerciseType.TYPED,List.of("3pl"),1,false,StudyScope.MIXED);
        post(BASE+"/sessions/"+skipped.path("id").asLong()+"/complete",Map.of(),200);
        var stats=getJson(BASE+"/statistics?datasetId=practice");
        assertThat(stats.at("/exercises/0/answered").asLong()).isEqualTo(2);
        assertThat(stats.at("/exercises/0/correct").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/0/incorrect").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/0/revealed").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/0/persons")).hasSize(6);
        assertThat(stats.at("/exercises/0/persons/0/correct").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/0/persons/1/revealed").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/0/persons/3/incorrect").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/1/answered").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/1/correct").asLong()).isEqualTo(1);
        assertThat(stats.at("/exercises/0/dueForReview").asLong()).isZero();
        clock.set(NOW.plusSeconds(86400).minusNanos(1));
        assertThat(getJson(BASE+"/statistics").at("/exercises/0/dueForReview").asLong()).isZero();
        clock.set(NOW.plusSeconds(86400));
        var due=getJson(BASE+"/statistics");
        assertThat(due.at("/exercises/0/dueForReview").asLong()).isEqualTo(3);
        assertThat(due.at("/exercises/0/dueVerbs").asLong()).isEqualTo(1);
        assertThat(due.at("/exercises/1/dueForReview").asLong()).isEqualTo(1);
        var changed=fixture.get("чета").deepCopy(); changed.put("note","Edited learning note");
        verb=content.update(verb.id(),new AoristDtos.Request("practice",verb.version(),changed,null));
        var archived=content.archive(verb.id(),new AoristDtos.ArchiveRequest(verb.version(),true));
        var history=getJson(BASE+"/statistics");
        assertThat(history.at("/exercises/0/answered")).isEqualTo(stats.at("/exercises/0/answered"));
        assertThat(history.at("/exercises/0/dueForReview").asLong()).isZero();
        content.archive(verb.id(),new AoristDtos.ArchiveRequest(archived.version(),false));
        assertThat(getJson(BASE+"/statistics")).isEqualTo(due);
        assertThat(count("aorist_attempts")).isEqualTo(4);
        assertThat(getJson(BASE+"/statistics?datasetId=another").at("/exercises/0/answered").asLong()).isZero();
        clock.set(NOW);
        assertThat(getJson("/api/statistics")).isEqualTo(legacy);
        assertThat(getJson("/api/aspect-pair-practice/statistics").path("answered")).isEqualTo(aspect.path("answered"));
    }

    @Test void statisticsValidateFiltersAndReturnBothEmptyExerciseTypes() throws Exception {
        var stats=getJson(BASE+"/statistics");
        assertThat(stats.path("exercises")).hasSize(2);
        for(var exercise:stats.path("exercises")) {
            for(String key:List.of("answered","correct","incorrect","revealed","dueForReview","dueVerbs")) assertThat(exercise.path(key).asLong()).isZero();
            assertThat(exercise.path("persons")).hasSize(6);
        }
        mvc.perform(get(BASE+"/statistics").param("datasetId","INVALID ID")).andExpect(status().isBadRequest());
    }

    private AoristDtos.Response create(String lemma) { return createEntry(fixture.get(lemma).deepCopy()); }
    private AoristDtos.Response createEntry(ObjectNode entry) {
        ObjectNode meta=document.deepCopy(); meta.remove("entries"); meta.put("datasetId","practice");
        return content.create(new AoristDtos.Request("practice",null,entry,meta));
    }
    private void importAll() throws Exception {
        var p=imports.preview(new MockMultipartFile("file","aorist.json","application/json",Files.readAllBytes(Path.of("learning-materials/aorist/bulgarian_aorist_40.json"))));
        imports.confirm(new AoristDtos.ConfirmRequest(p.importId(),UUID.randomUUID(),p.entries().stream().map(e->new AoristDtos.Selection(e.externalId(),e.expectedVersion())).toList(),false));
    }
    private JsonNode start(AoristExerciseType type,List<String> persons,int size,boolean shorter,StudyScope scope) throws Exception { return rawStart(type,persons,size,shorter,scope,null,null,201); }
    private JsonNode rawStart(AoristExerciseType type,List<String> persons,int size,boolean shorter,StudyScope scope,String group,String aspect,int expected) throws Exception {
        return post(BASE+"/sessions",new StartRequest(UUID.randomUUID(),size,type,persons,aspect,group,scope,null,shorter),expected);
    }
    private JsonNode answer(JsonNode state,String answer,int expected) throws Exception { return post(BASE+"/sessions/"+state.path("id").asLong()+"/answers",new AnswerRequest(qid(state),answer,null),expected); }
    private JsonNode choose(JsonNode state,String option,int expected) throws Exception { return post(BASE+"/sessions/"+state.path("id").asLong()+"/answers",new AnswerRequest(qid(state),null,UUID.fromString(option)),expected); }
    private long qid(JsonNode state) { return state.at("/currentQuestion/id").asLong(); }
    private String optionFor(JsonNode state,String text) { for(var option:state.at("/currentQuestion/options")) if(option.path("text").asText().equals(text)) return option.path("optionId").asText(); throw new AssertionError("Missing option"); }
    private JsonNode getJson(String url) throws Exception { return json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()); }
    private JsonNode post(String url,Object value,int expected) throws Exception { return json.readTree(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(value)))
            .andExpect(status().is(expected)).andReturn().getResponse().getContentAsByteArray()); }
    private void assertHidden(JsonNode state) { assertThat(state.toString()).doesNotContain("четох", "чете", "forms", "source", "acceptedAnswers", "snapshot", "checkedOn", "contentHash"); assertThat(state.at("/currentQuestion/feedback").isNull()).isTrue(); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Long.class); }
    private int attemptAction(Callable<?> action) throws Exception { try { action.call(); return 200; } catch(AoristException e) { return e.getStatus(); } }
    private <T> List<T> race(Callable<T> a,Callable<T> b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch gate=new CountDownLatch(1); var first=pool.submit(()-> { gate.await(); return a.call(); }); var second=pool.submit(()-> { gate.await(); return b.call(); });
            gate.countDown(); return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
    }
}
