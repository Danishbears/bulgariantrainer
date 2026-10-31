package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AspectPairDtos;
import com.example.bulgariantrainer.dto.AspectPracticeDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.nio.file.Path;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AspectPracticeRecoveryTests {
    @TempDir Path directory;

    @Test void realContextRestartsRestoreQueueSnapshotsFeedbackRevealsAndExactlyOnceProgress(){
        String url="jdbc:h2:file:"+directory.resolve("practice").toString().replace('\\','/')+";WRITE_DELAY=0";
        StartRequest request=new StartRequest(UUID.randomUUID(),2,null,null,AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,"recovery",false);
        SessionResponse submitted;
        List<Long> questionIds;
        try(var context=open(url)){
            var pairs=context.getBean(AspectPairService.class);
            for(String id:List.of("one","two"))pairs.create(new AspectPairDtos.Request("recovery",null,entry(id,"купя","Original note: купя")));
            var practice=context.getBean(AspectPracticeService.class);
            var state=practice.start(request);
            var queue=context.getBean(AspectQuestionRepository.class).findBySessionIdOrderByPositionAsc(state.id());
            questionIds=queue.stream().map(AspectQuestion::getId).toList();
            for(var q:queue){
                var live=pairs.get(q.getPair().getId());
                pairs.update(live.id(),new AspectPairDtos.Request("recovery",live.version(),entry(live.entry().id(),"нова","Edited after snapshot")));
                live=pairs.get(live.id());pairs.archive(live.id(),new AspectPairDtos.ArchiveRequest(true,live.version()));
            }
            submitted=practice.answer(state.id(),new AnswerRequest(state.currentQuestion().id(),"купя"));
            assertThat(submitted.status()).isEqualTo(SessionStatus.IN_PROGRESS);
            assertThat(submitted.currentPosition()).isEqualTo(1);
            assertThat(submitted.currentQuestion().feedback().content().note()).isEqualTo("Original note: купя");
        }
        SessionResponse revealed;
        try(var context=open(url)){
            var practice=context.getBean(AspectPracticeService.class);
            assertThat(practice.current(submitted.id())).isEqualTo(submitted);
            assertThat(practice.start(request)).isEqualTo(submitted);
            assertThat(context.getBean(AspectQuestionRepository.class).findBySessionIdOrderByPositionAsc(submitted.id()).stream().map(AspectQuestion::getId).toList()).isEqualTo(questionIds);
            assertThat(practice.answer(submitted.id(),new AnswerRequest(submitted.currentQuestion().id(),"купя"))).isEqualTo(submitted);
            var second=practice.next(submitted.id(),new QuestionRequest(submitted.currentQuestion().id()));
            assertThat(second.currentPosition()).isEqualTo(2);
            assertThat(second.currentQuestion().feedback()).isNull();
            revealed=practice.reveal(second.id(),new QuestionRequest(second.currentQuestion().id()));
            assertThat(revealed.status()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(revealed.correctAnswers()).isEqualTo(1);
            assertThat(revealed.currentQuestion().feedback().content().perfective()).isEqualTo("купя");
        }
        try(var context=open(url)){
            var practice=context.getBean(AspectPracticeService.class);
            assertThat(practice.current(revealed.id())).isEqualTo(revealed);
            assertThat(practice.reveal(revealed.id(),new QuestionRequest(revealed.currentQuestion().id()))).isEqualTo(revealed);
            var result=practice.results(revealed.id());
            assertThat(result.items()).hasSize(2);assertThat(result.accuracyPercent()).isEqualTo(100.0);
            assertThat(result.items().get(1).outcome()).isEqualTo("REVEALED");
            assertThat(practice.next(revealed.id(),new QuestionRequest(revealed.currentQuestion().id())).currentQuestion()).isNull();
            assertThat(context.getBean(AspectAttemptRepository.class).count()).isEqualTo(2);
            var progress=context.getBean(AspectProgressRepository.class).findAll();
            assertThat(progress).hasSize(2);assertThat(progress.stream().mapToInt(AspectProgress::getTotalAttempts).sum()).isEqualTo(2);
            assertThat(progress.stream().mapToInt(AspectProgress::getCorrectAttempts).sum()).isEqualTo(1);
        }
    }

    private ConfigurableApplicationContext open(String url){
        return new SpringApplicationBuilder(BulgariantrainerApplication.class).web(WebApplicationType.NONE)
                .profiles("test").run("--spring.datasource.url="+url,"--spring.main.banner-mode=off","--training.aspect-pairs.random-seed=42");
    }
    private AspectPairDtos.Entry entry(String id,String perfective,String note){
        return new AspectPairDtos.Entry(id,"купувам",perfective,"покупать","купить",List.of("купувам"),List.of(perfective),
                "Покупки",1,"core",note,new AspectPairDtos.Provenance("added",null,null),
                new AspectPairDtos.Examples(new AspectPairDtos.Example("Купувам хляб.","Покупаю хлеб."),new AspectPairDtos.Example("Ще купя хляб.","Куплю хлеб.")));
    }
}
