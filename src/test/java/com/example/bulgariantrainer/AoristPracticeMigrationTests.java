package com.example.bulgariantrainer;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AoristPracticeMigrationTests {
    @TempDir Path directory;
    @Test void v9ProgressIdsCountersAndDatesSurviveAddingExerciseDimension() throws Exception {
        String url="jdbc:h2:file:"+directory.resolve("v9").toString().replace('\\','/')+";WRITE_DELAY=0";
        Flyway.configure().dataSource(url,"sa","").target("9").cleanDisabled(true).load().migrate();
        List<String> original;
        try(var db=DriverManager.getConnection(url,"sa",""); var s=db.createStatement()) {
            s.executeUpdate("INSERT INTO aorist_datasets(id,namespace,metadata_json,dataset_revision,created_at,updated_at) VALUES (5,'existing','{}',7,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO aorist_verbs(id,dataset_id,external_id,lemma,russian,aspect,tense,study_group,note,source_json,search_text,content_hash,created_at,updated_at) VALUES (9,5,'old-1','чета','читать','imperfective','aorist','old-group','','{}','чета','old-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO aorist_progress(id,verb_id,person,total_attempts,correct_attempts,successful_review_count,consecutive_correct,repetition_interval,last_reviewed_at,next_review_at) VALUES (42,9,'1sg',9,7,7,2,3,TIMESTAMP WITH TIME ZONE '2026-11-10 12:00:00+00',TIMESTAMP WITH TIME ZONE '2026-11-13 12:00:00+00')");
            original=row(s);
        }
        var flyway=Flyway.configure().dataSource(url,"sa","").cleanDisabled(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1); flyway.validate();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        try(var db=DriverManager.getConnection(url,"sa",""); var s=db.createStatement()) {
            assertThat(row(s)).isEqualTo(original);
            try(var r=s.executeQuery("SELECT exercise_type FROM aorist_progress WHERE id=42")) { assertThat(r.next()).isTrue(); assertThat(r.getString(1)).isEqualTo("TYPED"); }
            s.executeUpdate("INSERT INTO aorist_progress(verb_id,person,exercise_type,total_attempts,correct_attempts) VALUES (9,'1sg','MULTIPLE_CHOICE',1,1)");
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO aorist_progress(verb_id,person,exercise_type) VALUES (9,'1sg','TYPED')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("INSERT INTO aorist_progress(verb_id,person,exercise_type) VALUES (9,'1sg','UNKNOWN')")).isInstanceOf(SQLException.class);
            assertThatThrownBy(()->s.executeUpdate("DELETE FROM aorist_verbs WHERE id=9")).isInstanceOf(SQLException.class);
            assertThat(row(s)).isEqualTo(original);
            try(var r=s.executeQuery("SELECT COUNT(*) FROM aorist_sessions")) { assertThat(r.next()).isTrue(); assertThat(r.getInt(1)).isZero(); }
        }
    }
    private List<String> row(Statement s) throws Exception {
        try(var r=s.executeQuery("SELECT id,verb_id,person,total_attempts,correct_attempts,successful_review_count,consecutive_correct,repetition_interval,last_reviewed_at,next_review_at FROM aorist_progress WHERE id=42")) {
            assertThat(r.next()).isTrue(); List<String> values=new ArrayList<>(); for(int i=1;i<=10;i++) values.add(r.getString(i)); return values;
        }
    }
}
