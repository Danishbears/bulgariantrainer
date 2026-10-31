package com.example.bulgariantrainer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class AspectPairPersistenceTests {
    @TempDir Path directory;

    @Test void upgradesPopulatedV6AndReopensAspectDataWithHistoryAndConstraints() throws Exception {
        Path file=directory.resolve("aspect-history");String url="jdbc:h2:file:"+file.toString().replace('\\','/')+";WRITE_DELAY=0";
        Flyway.configure().dataSource(url,"sa","").target("6").cleanDisabled(true).load().migrate();
        try(Connection db=DriverManager.getConnection(url,"sa","");Statement s=db.createStatement()){
            s.executeUpdate("INSERT INTO words(id,bulgarian_text,normalized_text,created_at,updated_at) VALUES (1,'къща','къща',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO study_progress(word_id,training_mode,total_attempts,correct_attempts) VALUES (1,'TYPING_BG_TO_RU',4,2)");
        }
        Flyway latest=Flyway.configure().dataSource(url,"sa","").cleanDisabled(true).load();
        assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);latest.validate();
        try(Connection db=DriverManager.getConnection(url,"sa","");Statement s=db.createStatement()){
            s.executeUpdate("INSERT INTO aspect_datasets(id,namespace,metadata_json,content_revision,created_at,updated_at) VALUES (1,'test','{\"formatVersion\":1}','revision',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO aspect_pairs(id,dataset_id,external_id,imperfective,perfective,russian_imperfective,russian_perfective,topic,study_stage,relation_type,note,provenance_json,search_text,content_hash,created_at,updated_at) VALUES (1,1,'bgasp-057','купувам','купя','покупать','купить','Покупки',1,'core','','{\"kind\":\"added\"}','купувам купя','hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO aspect_pair_answers(pair_id,side,position,answer_text,normalized_key) VALUES (1,'PERFECTIVE',1,'купя','купя')");
            s.executeUpdate("INSERT INTO aspect_progress(pair_id,direction,total_attempts,correct_attempts) VALUES (1,'IMPERFECTIVE_TO_PERFECTIVE',3,2)");
            assertThatThrownBy(()->s.executeUpdate("UPDATE aspect_pairs SET study_stage=4 WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO aspect_pair_answers(pair_id,side,position,answer_text,normalized_key) VALUES (1,'PERFECTIVE',2,'купя','купя')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("DELETE FROM aspect_pairs WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("UPDATE aspect_progress SET correct_attempts=4 WHERE pair_id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("UPDATE aspect_pairs SET dataset_id=999 WHERE id=1")).isInstanceOf(SQLException.class);
        }
        assertThat(latest.migrate().migrationsExecuted).isZero();
        Path copy=directory.resolve("restored");Files.copy(Path.of(file+".mv.db"),Path.of(copy+".mv.db"));
        String restored="jdbc:h2:file:"+copy.toString().replace('\\','/');
        try(Connection db=DriverManager.getConnection(restored,"sa","");Statement s=db.createStatement()){
            try(ResultSet r=s.executeQuery("SELECT p.imperfective,p.perfective,a.answer_text,h.total_attempts,h.correct_attempts FROM aspect_pairs p JOIN aspect_pair_answers a ON a.pair_id=p.id JOIN aspect_progress h ON h.pair_id=p.id")){
                assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("купувам");assertThat(r.getString(2)).isEqualTo("купя");
                assertThat(r.getString(3)).isEqualTo("купя");assertThat(r.getInt(4)).isEqualTo(3);assertThat(r.getInt(5)).isEqualTo(2);
            }
            try(ResultSet r=s.executeQuery("SELECT total_attempts,correct_attempts FROM study_progress WHERE word_id=1")){
                assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(4);assertThat(r.getInt(2)).isEqualTo(2);
            }
        }
    }
}
