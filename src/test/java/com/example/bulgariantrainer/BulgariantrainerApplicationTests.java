package com.example.bulgariantrainer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulgariantrainerApplicationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void healthReturnsJson() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("bulgariantrainer"));
    }

    @Test
    void homepageAndModuleAreServed() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Болгарский тренажёр")));
        mockMvc.perform(get("/js/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/health")));
        mockMvc.perform(get("/css/styles.css"))
                .andExpect(status().isOk());
    }

    @Test
    void databaseSupportsCyrillicAndUsesIsolatedMemoryStorage() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT ?")) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
            statement.setString(1, "Здравей — Привет");
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("Здравей — Привет");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"index", "vocabulary", "import", "flashcards", "typing", "sentences", "statistics"})
    void navigationDestinationsServeRussianPagesWithSharedLayout(String page) throws Exception {
        mockMvc.perform(get("/" + page + ".html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"))
                .andExpect(content().string(containsString("lang=\"ru\"")))
                .andExpect(content().string(containsString("/js/layout.js")))
                .andExpect(content().string(containsString("/css/styles.css")));
        mockMvc.perform(get("/js/layout.js")).andExpect(status().isOk());
    }
}

