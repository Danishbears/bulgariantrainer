package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AspectPairDtos;
import com.example.bulgariantrainer.dto.AspectPracticeDtos.*;
import com.example.bulgariantrainer.entity.*;
import com.example.bulgariantrainer.repository.*;
import com.example.bulgariantrainer.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AspectFeatureUpgradeTests {
    @TempDir Path directory;
    private static final List<String> LEGACY=List.of("words","translations","sentences","sentence_words",
            "study_progress","study_sessions","study_attempts","flashcard_questions","typing_questions",
            "sentence_alternatives","sentence_progress","sentence_questions","sentence_attempt_words");
    private static final List<String> ASPECT=List.of("aspect_datasets","aspect_pairs","aspect_pair_answers",
            "aspect_progress","aspect_sessions","aspect_questions","aspect_attempts","aspect_import_receipts");

    @Test void populatedV6UpgradeAndStoppedFileCopiesPreserveBothOldAndNewLearningData() throws Exception {
        Path database=directory.resolve("before-upgrade");String url=url(database);
        Flyway.configure().dataSource(url,"sa","").target("6").cleanDisabled(true).load().migrate();
        fixture(url);
        var original=snapshot(url,LEGACY);
        Path oldBackup=directory.resolve("old-backup");
        Files.copy(Path.of(database+".mv.db"),Path.of(oldBackup+".mv.db"));
        AspectPairDtos.ConfirmRequest confirmation;
        AspectPairDtos.Summary imported;
        Results result;
        Map<String,List<List<String>>> aspectData;
        try(var context=open(url)) {
            assertThat(snapshot(url,LEGACY)).isEqualTo(original);
            assertThat(context.getBean(WordService.class).get(1).bulgarianText()).isEqualTo("купувам");
            assertThat(context.getBean(FlashcardService.class).current(1).currentQuestion().prompt()).contains("купувам");
            assertThat(context.getBean(TypingService.class).current(2).currentQuestion().feedback().result()).isEqualTo(AttemptResult.CORRECT);
            assertThat(context.getBean(SentencePracticeService.class).current(3).currentQuestion().russianText()).isEqualTo("Я покупаю хлеб.");
            assertThat(context.getBean(AspectPairRepository.class).count()).isZero();
            var imports=context.getBean(AspectPairImportService.class);
            byte[] bytes=Files.readAllBytes(Path.of("learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json"));
            var preview=imports.preview("upgrade",new MockMultipartFile("file","pairs.json","application/json",bytes));
            assertThat(preview.valid()).isTrue();assertThat(preview.entries()).hasSize(100);
            confirmation=new AspectPairDtos.ConfirmRequest(preview.importId(),UUID.randomUUID(),preview.entries().stream()
                    .map(row->new AspectPairDtos.Selection(row.externalId(),row.expectedVersion())).toList());
            imported=imports.confirm(confirmation);assertThat(imported.created()).isEqualTo(100);
            var practice=context.getBean(AspectPracticeService.class);
            var state=practice.start(new StartRequest(UUID.randomUUID(),2,List.of(1),null,
                    AspectDirection.IMPERFECTIVE_TO_PERFECTIVE,StudyScope.MIXED,"upgrade",false));
            var queue=context.getBean(AspectQuestionRepository.class).findBySessionIdOrderByPositionAsc(state.id());
            var entry=context.getBean(AspectPairJson.class).read(queue.getFirst().getSnapshotJson(),AspectPairDtos.Entry.class);
            state=practice.answer(state.id(),new AnswerRequest(state.currentQuestion().id(),entry.acceptedPerfective().getFirst()));
            state=practice.next(state.id(),new QuestionRequest(state.currentQuestion().id()));
            state=practice.reveal(state.id(),new QuestionRequest(state.currentQuestion().id()));
            result=practice.results(state.id());
            var pairs=context.getBean(AspectPairService.class);var pair=pairs.get(queue.getFirst().getPair().getId());var e=pair.entry();
            pairs.update(pair.id(),new AspectPairDtos.Request("upgrade",pair.version(),new AspectPairDtos.Entry(e.id(),e.imperfective(),e.perfective(),
                    e.russianImperfective(),e.russianPerfective(),e.acceptedImperfective(),e.acceptedPerfective(),e.topic(),e.studyStage(),
                    e.relationType(),"Edited after practice",e.provenance(),e.examples())));
            pair=pairs.get(pair.id());pairs.archive(pair.id(),new AspectPairDtos.ArchiveRequest(true,pair.version()));
            assertThat(practice.results(state.id())).isEqualTo(result);
            assertThat(snapshot(url,LEGACY)).isEqualTo(original);
            aspectData=snapshot(url,ASPECT);
        }
        // This tests the documented offline copy procedure, not a new backup API.
        Path restored=directory.resolve("restored-current");
        Files.copy(Path.of(database+".mv.db"),Path.of(restored+".mv.db"));
        try(var context=open(url(restored))) {
            assertThat(snapshot(url(restored),LEGACY)).isEqualTo(original);
            assertThat(snapshot(url(restored),ASPECT)).isEqualTo(aspectData);
            assertThat(context.getBean(AspectPairRepository.class).count()).isEqualTo(100);
            assertThat(context.getBean(AspectAttemptRepository.class).count()).isEqualTo(2);
            assertThat(context.getBean(AspectProgressRepository.class).count()).isEqualTo(2);
            assertThat(context.getBean(AspectPracticeService.class).results(result.session().id())).isEqualTo(result);
            assertThat(context.getBean(AspectPairImportService.class).confirm(confirmation)).isEqualTo(imported);
            var statistics=context.getBean(AspectStatisticsService.class).overview("upgrade");
            assertThat(statistics.answered()).isEqualTo(1);assertThat(statistics.correct()).isEqualTo(1);
            assertThat(statistics.incorrect()).isZero();assertThat(statistics.revealed()).isEqualTo(1);
        }
        // An older stopped-file backup can be restored and upgraded by the new app.
        var migration=Flyway.configure().dataSource(url(oldBackup),"sa","").cleanDisabled(true).load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(2);migration.validate();
        assertThat(snapshot(url(oldBackup),LEGACY)).isEqualTo(original);
        for(var rows:snapshot(url(oldBackup),ASPECT).values())assertThat(rows).isEmpty();
    }

    private void fixture(String url) throws Exception {
        try(Connection db=DriverManager.getConnection(url,"sa","");Statement s=db.createStatement()) {
            s.executeUpdate("INSERT INTO words(id,bulgarian_text,normalized_text,created_at,updated_at) VALUES (1,'купувам','купувам',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO translations(word_id,russian_text,normalized_text,is_primary) VALUES (1,'покупать','покупать',TRUE)");
            s.executeUpdate("INSERT INTO study_progress(word_id,training_mode,total_attempts,correct_attempts,successful_review_count,consecutive_correct,next_review_at,repetition_interval) VALUES (1,'TYPING_RU_TO_BG',1,1,1,1,TIMESTAMP WITH TIME ZONE '2030-01-01 00:00:00+00',1)");
            s.executeUpdate("INSERT INTO sentences(id,bulgarian_text,russian_text,normalized_russian,topic,approved) VALUES (1,'Купувам хляб.','Я покупаю хлеб.','я покупаю хлеб.','Еда',TRUE)");
            s.executeUpdate("INSERT INTO sentence_words(sentence_id,word_id) VALUES (1,1)");
            s.executeUpdate("INSERT INTO sentence_alternatives(sentence_id,bulgarian_text,normalized_text) VALUES (1,'Купувам си хляб.','купувам си хляб.')");
            s.executeUpdate("INSERT INTO sentence_progress(sentence_id,total_attempts,correct_attempts,partial_attempts,incorrect_attempts,successful_review_count,consecutive_correct,next_review_at,repetition_interval) VALUES (1,3,1,1,1,1,0,TIMESTAMP WITH TIME ZONE '2020-01-01 00:00:00+00',1)");
            s.executeUpdate("INSERT INTO study_sessions(id,training_mode,started_at,total_questions,status,flashcard_request_id,flashcard_direction,requested_size) VALUES (1,'FLASHCARD',CURRENT_TIMESTAMP,1,'IN_PROGRESS',RANDOM_UUID(),'BG_TO_RU',1)");
            s.executeUpdate("INSERT INTO flashcard_questions(session_id,word_id,position,direction,bulgarian_text,russian_texts) VALUES (1,1,1,'BG_TO_RU','купувам','покупать')");
            s.executeUpdate("INSERT INTO study_sessions(id,training_mode,started_at,completed_at,total_questions,correct_answers,status,typing_request_id,shorter_accepted) VALUES (2,'TYPING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,1,1,'COMPLETED',RANDOM_UUID(),TRUE)");
            s.executeUpdate("INSERT INTO study_attempts(id,session_id,word_id,direction,user_answer,result,created_at) VALUES (1,2,1,'RU_TO_BG','купувам','CORRECT',CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO typing_questions(session_id,word_id,position,direction,bulgarian_text,russian_texts,attempt_id) VALUES (2,1,1,'RU_TO_BG','купувам','покупать',1)");
            s.executeUpdate("INSERT INTO study_sessions(id,training_mode,started_at,total_questions,status,sentence_request_id,sentence_requested_size) VALUES (3,'SENTENCE',CURRENT_TIMESTAMP,1,'IN_PROGRESS',RANDOM_UUID(),1)");
            s.executeUpdate("INSERT INTO sentence_questions(session_id,sentence_id,position,russian_text,bulgarian_text,alternatives_json,explanation) VALUES (3,1,1,'Я покупаю хлеб.','Купувам хляб.','[\"Купувам си хляб.\"]','Уточнение')");
        }
    }
    private String url(Path file){return "jdbc:h2:file:"+file.toString().replace('\\','/')+";WRITE_DELAY=0";}
    private ConfigurableApplicationContext open(String url) {
        return new SpringApplicationBuilder(BulgariantrainerApplication.class).web(WebApplicationType.NONE).profiles("test")
                .run("--spring.datasource.url="+url,"--spring.main.banner-mode=off","--training.aspect-pairs.random-seed=42");
    }
    private Map<String,List<List<String>>> snapshot(String url,List<String> tables) throws Exception {
        Map<String,List<List<String>>> result=new TreeMap<>();
        try(Connection db=DriverManager.getConnection(url,"sa","");Statement s=db.createStatement()) {
            for(String table:tables) {
                List<List<String>> rows=new ArrayList<>();
                try(ResultSet r=s.executeQuery("SELECT * FROM "+table)) {
                    while(r.next()) {List<String> row=new ArrayList<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.add(r.getString(i));rows.add(row);}
                }
                rows.sort(Comparator.comparing(row->row.toString()));result.put(table,rows);
            }
        }
        return result;
    }
}
