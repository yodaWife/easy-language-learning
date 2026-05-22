package com.yodawife.easyll.controller;

import com.yodawife.easyll.repository.ScoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class DataReloadIntegrationTest {

    private static final Path TEMP_DIR;
    private static final Path WORDS_FILE;
    private static final Path SCORES_FILE;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("easyll-reload-it-");
            WORDS_FILE = TEMP_DIR.resolve("words.csv");
            SCORES_FILE = TEMP_DIR.resolve("scores.csv");
            Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nStone;Kő;\n");
            Files.writeString(SCORES_FILE, "alice;Letter;Betű;S\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize test files", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.words.source", () -> WORDS_FILE.toString());
        registry.add("app.scores.file-path", () -> SCORES_FILE.toString());
        registry.add("app.scores.write-path", () -> SCORES_FILE.toString());
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    ScoreRepository scoreRepository;

    MockMvc mockMvc;

    @BeforeEach
    void resetToValidData() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nStone;Kő;\n");
        Files.writeString(SCORES_FILE, "alice;Letter;Betű;S\n");
        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin"))).andReturn();
    }

    @Test
    @DisplayName("knownUsers reflects latest score CSV data after a successful reload")
    void knownUsersUpdatesAfterSuccessfulReload() throws Exception {
        // @BeforeEach loaded: alice;Letter;Betű;S
        assertThat(scoreRepository.knownUsers()).contains("alice");

        // Replace score CSV with a different user
        Files.writeString(SCORES_FILE, "newuser;Stone;Kő;F\n");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        // After reload, knownUsers must reflect new snapshot — newuser in, alice out
        assertThat(scoreRepository.knownUsers()).contains("newuser");
        assertThat(scoreRepository.knownUsers()).doesNotContain("alice");
    }

    @Test
    void degradedDataCanBeFixedAndReloadedToHealthy() throws Exception {
        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nLetter;Betű;\n");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", false));

        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nCloud;Felhő;\n");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", true));
    }

    @Test
    void reloadWithStillInvalidDataRemainsDegradedWithUpdatedErrors() throws Exception {
        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\n ;Betű;\n");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", false));

        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter; ;\n");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", false));
    }

    @Test
    @DisplayName("When scores CSV is invalid but words CSV is valid, wordsHealthy=true and scoresHealthy=false")
    void wordsHealthy_scoresDegraded_showsCorrectHealthDimensions() throws Exception {
        Files.writeString(SCORES_FILE, "INVALID_GARBAGE;;;");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", true))
                .andExpect(model().attribute("scoresHealthy", false));

        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", true))
                .andExpect(model().attribute("scoresHealthy", false));
    }

    @Test
    @DisplayName("POST /session/start is allowed when words are healthy even if scores are degraded")
    void wordsHealthy_scoresDegraded_allowsSessionStart() throws Exception {
        Files.writeString(SCORES_FILE, "INVALID_GARBAGE;;;");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/session/start").param("mode", "match"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/match"));
    }
}
