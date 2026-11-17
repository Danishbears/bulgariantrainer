package com.example.bulgariantrainer;

import com.example.bulgariantrainer.dto.AoristDtos.*;
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

class AoristContentPersistenceTests {
    @TempDir Path directory;
    @Test void populatedV8UpgradeAndStoppedFileRestorePreserveOldContentAndNewReceiptsAndProgress() throws Exception {
        Path database=directory.resolve("upgrade"); String url=url(database);
        Flyway.configure().dataSource(url,"sa","").target("8").cleanDisabled(true).load().migrate();
        try(var db=DriverManager.getConnection(url,"sa",""); var s=db.createStatement()) {
            s.executeUpdate("INSERT INTO words(id,bulgarian_text,normalized_text,created_at,updated_at) VALUES (1,'чета','чета',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO study_progress(word_id,training_mode,total_attempts,correct_attempts) VALUES (1,'FLASHCARD',2,1)");
            s.executeUpdate("INSERT INTO aspect_datasets(id,namespace,metadata_json,created_at,updated_at) VALUES (1,'old-pairs','{}',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO aspect_pairs(id,dataset_id,external_id,imperfective,perfective,russian_imperfective,russian_perfective,topic,study_stage,relation_type,note,provenance_json,search_text,content_hash,created_at,updated_at) VALUES (1,1,'old-1','чета','прочета','читать','прочитать','Книги',1,'core','','{}','чета','old-hash',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
            s.executeUpdate("INSERT INTO aspect_progress(pair_id,direction,total_attempts,correct_attempts) VALUES (1,'IMPERFECTIVE_TO_PERFECTIVE',4,2)");
        }
        var legacy=snapshot(url,List.of("words","study_progress","aspect_pairs","aspect_progress"));
        Path oldBackup=directory.resolve("v8-backup"); Files.copy(Path.of(database+".mv.db"),Path.of(oldBackup+".mv.db"));
        var migration=Flyway.configure().dataSource(url,"sa","").cleanDisabled(true).load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1); migration.validate();
        assertThat(migration.migrate().migrationsExecuted).isZero();
        ConfirmRequest request; Summary summary; String exported;
        try(var context=open(url)) {
            assertThat(snapshot(url,List.of("words","study_progress","aspect_pairs","aspect_progress"))).isEqualTo(legacy);
            assertThat(snapshot(url,List.of("aorist_verbs")).get("aorist_verbs")).isEmpty(); // No startup import.
            var imports=context.getBean(AoristImportService.class);
            byte[] bytes=Files.readAllBytes(Path.of("learning-materials/aorist/bulgarian_aorist_40.json"));
            var p=imports.preview(new MockMultipartFile("file","aorist.json","application/json",bytes));
            request=new ConfirmRequest(p.importId(),UUID.randomUUID(),p.entries().stream().map(e->new Selection(e.externalId(),e.expectedVersion())).toList(),false);
            summary=imports.confirm(request); assertThat(summary.created()).isEqualTo(40);
            exported=context.getBean(AoristVerbService.class).export("bg-aorist-core-40",null,null).json();
            try(var db=DriverManager.getConnection(url,"sa",""); var s=db.createStatement()) {
                s.executeUpdate("INSERT INTO aorist_progress(verb_id,person,total_attempts,correct_attempts) SELECT MIN(id),'1sg',3,2 FROM aorist_verbs");
                assertThatThrownBy(()->s.executeUpdate("DELETE FROM aorist_verbs WHERE id=(SELECT MIN(id) FROM aorist_verbs)")).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.executeUpdate("INSERT INTO aorist_progress(verb_id,person) SELECT MIN(id),'1sg' FROM aorist_verbs")).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.executeUpdate("INSERT INTO aorist_progress(verb_id,person) SELECT MIN(id),'gender' FROM aorist_verbs")).isInstanceOf(SQLException.class);
            }
        }
        var expected=snapshot(url,List.of("aorist_datasets","aorist_verbs","aorist_forms","aorist_progress","aorist_import_receipts"));
        Path restored=directory.resolve("restored"); Files.copy(Path.of(database+".mv.db"),Path.of(restored+".mv.db"));
        try(var context=open(url(restored))) {
            assertThat(context.getBean(AoristImportService.class).confirm(request)).isEqualTo(summary);
            assertThat(context.getBean(AoristVerbService.class).export("bg-aorist-core-40",null,null).json()).isEqualTo(exported);
            assertThat(snapshot(url(restored),List.of("aorist_datasets","aorist_verbs","aorist_forms","aorist_progress","aorist_import_receipts"))).isEqualTo(expected);
            assertThat(snapshot(url(restored),List.of("words","study_progress","aspect_pairs","aspect_progress"))).isEqualTo(legacy);
        }
        var restoredOld=Flyway.configure().dataSource(url(oldBackup),"sa","").cleanDisabled(true).load();
        assertThat(restoredOld.migrate().migrationsExecuted).isEqualTo(1); restoredOld.validate();
        assertThat(snapshot(url(oldBackup),List.of("words","study_progress","aspect_pairs","aspect_progress"))).isEqualTo(legacy);
        assertThat(snapshot(url(oldBackup),List.of("aorist_verbs")).get("aorist_verbs")).isEmpty();
    }
    private String url(Path path) { return "jdbc:h2:file:"+path.toAbsolutePath().toString().replace('\\','/')+";WRITE_DELAY=0"; }
    private ConfigurableApplicationContext open(String url) {
        return new SpringApplicationBuilder(BulgariantrainerApplication.class).profiles("test").web(WebApplicationType.NONE)
                .run("--spring.datasource.url="+url,"--spring.main.banner-mode=off");
    }
    private Map<String,List<List<String>>> snapshot(String url,List<String> tables) throws Exception {
        Map<String,List<List<String>>> result=new LinkedHashMap<>();
        try(var db=DriverManager.getConnection(url,"sa",""); var s=db.createStatement()) {
            for(String table:tables) try(var r=s.executeQuery("SELECT * FROM "+table+" ORDER BY 1")) {
                List<List<String>> rows=new ArrayList<>();
                while(r.next()) { List<String> row=new ArrayList<>(); for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.add(r.getString(i)); rows.add(row); }
                result.put(table,rows);
            }
        }
        return result;
    }
}
