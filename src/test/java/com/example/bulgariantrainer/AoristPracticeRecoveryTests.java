package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AoristDtos;
import com.example.bulgariantrainer.dto.AoristPracticeDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.service.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AoristPracticeRecoveryTests {
    @TempDir Path directory;
    @ParameterizedTest @EnumSource(AoristExerciseType.class)
    void fileDatabaseRestartsRestoreSnapshotsOptionsFeedbackAndExactlyOnceSchedules(AoristExerciseType type) throws Exception {
        String url="jdbc:h2:file:"+directory.resolve(type.name()).toString().replace('\\','/')+";WRITE_DELAY=0";
        var request=new StartRequest(UUID.randomUUID(),2,type,List.of("1sg","1pl"),null,null,StudyScope.NEW,"recovery",false);
        SessionResponse submitted; List<Long> ids; String originalAnswer; long firstQuestion;
        SessionResponse initial;
        try(var context=open(url)) {
            var json=context.getBean(AoristJson.class);
            var document=(ObjectNode)json.tree(Files.readString(Path.of("learning-materials/aorist/bulgarian_aorist_40.json")));
            ObjectNode entry=null;
            for(var node:document.path("entries")) if(node.path("lemma").asText().equals("чета")) entry=(ObjectNode)node.deepCopy();
            assertThat(entry).isNotNull();
            document.remove("entries"); document.put("datasetId","recovery");
            var verbs=context.getBean(AoristVerbService.class);
            var verb=verbs.create(new AoristDtos.Request("recovery",null,entry,document));
            var practice=context.getBean(AoristPracticeService.class);
            initial=practice.start(request);
            var queue=context.getBean(AoristQuestionRepository.class).findBySessionIdOrderByPositionAsc(initial.id());
            ids=queue.stream().map(AoristQuestion::getId).toList(); firstQuestion=initial.currentQuestion().id();
            originalAnswer=verb.entry().forms().get(initial.currentQuestion().prompt().person()).getFirst();
            submitted=submit(practice,initial,originalAnswer);
            assertThat(submitted.currentPosition()).isEqualTo(1); assertThat(submitted.status()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(submitted.currentQuestion().feedback().acceptedAnswers()).containsExactly(originalAnswer);
            entry.put("note","Edited after start: full paradigm must stay private");
            for(String person:AoristValidation.PERSONS) ((ObjectNode)entry.path("forms")).putArray(person).add("променена форма");
            verb=verbs.update(verb.id(),new AoristDtos.Request("recovery",verb.version(),entry,null));
            verbs.archive(verb.id(),new AoristDtos.ArchiveRequest(verb.version(),true));
        }
        SessionResponse revealed;
        try(var context=open(url)) {
            var practice=context.getBean(AoristPracticeService.class);
            assertThat(practice.current(submitted.id())).isEqualTo(submitted); assertThat(practice.start(request)).isEqualTo(submitted);
            assertThat(submit(practice,initial,originalAnswer)).isEqualTo(submitted);
            assertThat(context.getBean(AoristQuestionRepository.class).findBySessionIdOrderByPositionAsc(submitted.id()).stream().map(AoristQuestion::getId).toList()).isEqualTo(ids);
            var second=practice.next(submitted.id(),new QuestionRequest(firstQuestion));
            assertThat(second.currentPosition()).isEqualTo(2); assertThat(second.currentQuestion().feedback()).isNull();
            assertThat(second.currentQuestion().options().stream().map(Option::text)).doesNotContain("променена форма");
            assertThat(practice.current(second.id())).isEqualTo(second);
            revealed=practice.reveal(second.id(),new QuestionRequest(second.currentQuestion().id()));
            assertThat(revealed.status()).isEqualTo(SessionStatus.COMPLETED); assertThat(revealed.correctAnswers()).isEqualTo(1);
            assertThat(revealed.independentRecallCorrect()).isEqualTo(type==AoristExerciseType.TYPED?1:0);
            assertThat(revealed.currentQuestion().feedback().acceptedAnswers()).doesNotContain("променена форма");
        }
        try(var context=open(url)) {
            var practice=context.getBean(AoristPracticeService.class);
            assertThat(practice.current(revealed.id())).isEqualTo(revealed);
            assertThat(practice.reveal(revealed.id(),new QuestionRequest(revealed.currentQuestion().id()))).isEqualTo(revealed);
            var results=practice.results(revealed.id());
            assertThat(results.accuracyPercent()).isEqualTo(100.0); assertThat(results.items().stream().map(ReviewItem::outcome)).containsExactly("CORRECT","REVEALED");
            assertThat(results.mistakes()).isEmpty();
            var rows=context.getBean(AoristProgressRepository.class).findAll(); assertThat(rows).hasSize(2);
            assertThat(rows).allMatch(p->p.getExerciseType()==type && p.getTotalAttempts()==1 && p.getRepetitionInterval()==1);
            assertThat(rows.stream().mapToInt(AoristProgress::getCorrectAttempts).sum()).isEqualTo(1);
            assertThat(context.getBean(AoristAttemptRepository.class).count()).isEqualTo(2);
            assertThat(practice.next(revealed.id(),new QuestionRequest(revealed.currentQuestion().id())).currentQuestion()).isNull();
            assertThat(submit(practice,initial,originalAnswer).currentQuestion()).isNull();
            assertThat(context.getBean(AoristAttemptRepository.class).count()).isEqualTo(2);
        }
        // The supported backup is a copy of the closed H2 file, including all modes' tables.
        Path backup=directory.resolve(type.name()+"-backup.mv.db");
        Files.copy(directory.resolve(type.name()+".mv.db"),backup);
        String restoredUrl="jdbc:h2:file:"+directory.resolve(type.name()+"-backup").toString().replace('\\','/')+";WRITE_DELAY=0";
        try(var context=open(restoredUrl)) {
            var practice=context.getBean(AoristPracticeService.class);
            var restored=practice.current(revealed.id());
            assertThat(restored.settings()).isEqualTo(initial.settings());
            assertThat(restored.currentQuestion()).isNull();
            assertThat(practice.results(revealed.id()).items().stream().map(ReviewItem::outcome)).containsExactly("CORRECT","REVEALED");
            assertThat(context.getBean(AoristAttemptRepository.class).count()).isEqualTo(2);
            assertThat(context.getBean(AoristProgressRepository.class).findAll()).hasSize(2)
                    .allMatch(p->p.getExerciseType()==type && p.getTotalAttempts()==1);
            var stats=context.getBean(AoristStatisticsService.class).overview("recovery");
            var mode=stats.exercises().stream().filter(e->e.exerciseType()==type).findFirst().orElseThrow();
            assertThat(mode.answered()).isEqualTo(1); assertThat(mode.correct()).isEqualTo(1);
            assertThat(mode.revealed()).isEqualTo(1); assertThat(mode.dueForReview()).isZero();
            assertThat(context.getBean(AoristVerbRepository.class).findAll()).hasSize(1).allMatch(AoristVerb::isArchived);
        }
    }
    private SessionResponse submit(AoristPracticeService service,SessionResponse state,String answer) {
        UUID option=state.settings().exerciseType()==AoristExerciseType.TYPED?null:state.currentQuestion().options().stream()
                .filter(o->o.text().equals(answer)).findFirst().orElseThrow().optionId();
        return service.answer(state.id(),new AnswerRequest(state.currentQuestion().id(),option==null?answer:null,option));
    }
    private ConfigurableApplicationContext open(String url) {
        return new SpringApplicationBuilder(BulgariantrainerApplication.class).profiles("test").web(WebApplicationType.NONE)
                .run("--spring.datasource.url="+url,"--spring.main.banner-mode=off","--training.aorist.random-seed=42");
    }
}
