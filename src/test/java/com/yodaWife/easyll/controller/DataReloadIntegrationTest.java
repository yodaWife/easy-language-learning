package com.yodawife.easyll.controller;

import com.yodawife.easyll.repository.ScoreReadRepository;
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
import java.util.Objects;

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
    private static final Path HUN_DIR;
    private static final Path WORDS_FILE;
    private static final Path MODE_ELIGIBILITY_FILE;
    private static final Path SCORES_FILE;

    // Stable test user IDs (UUID format, required by new ScoreMigrationService detection)
    static final String ALICE_USER_ID   = "00000000-0000-0000-0000-000000000001";
    static final String NEWUSER_USER_ID = "00000000-0000-0000-0000-000000000002";

    static {
        try {
            TEMP_DIR = Files.createTempDirectory("easyll-reload-it-");
            HUN_DIR = TEMP_DIR.resolve("hun");
            WORDS_FILE = HUN_DIR.resolve("words.csv");
            MODE_ELIGIBILITY_FILE = HUN_DIR.resolve("mode-eligibility.csv");
            SCORES_FILE = TEMP_DIR.resolve("scores.csv");

            Files.createDirectories(HUN_DIR);
            writeValidDictionary();
            // Use new-format score data: userId;pairId;mode;history
            // pairId="w1" matches the wordId in the test dictionary
            Files.writeString(SCORES_FILE, ALICE_USER_ID + ";w1;match;S\n");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize test files", e);
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.dictionaries.root-path", () -> TEMP_DIR.toString());
        registry.add("app.dictionaries.primary-language-code", () -> "hun");
        registry.add("app.scores.file-path",  () -> SCORES_FILE.toString());
        registry.add("app.scores.write-path", () -> SCORES_FILE.toString());
        // Isolate account storage per test run so CsvAccountRepository does not touch the
        // shared dev users.csv file.
        registry.add("app.accounts.file-path", () -> TEMP_DIR.resolve("users.csv").toString());
    }

    @Autowired
    WebApplicationContext context;

    @Autowired
    ScoreReadRepository scoreRepository;

    MockMvc mockMvc;

    @BeforeEach
    void resetToValidData() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        writeValidDictionary();
        // New-format score data: userId;pairId;mode;history
        Files.writeString(SCORES_FILE, ALICE_USER_ID + ";w1;match;S\n");
        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin"))).andReturn();
    }

    @Test
    @DisplayName("knownUsers reflects latest score CSV data after a successful reload")
    void knownUsersUpdatesAfterSuccessfulReload() throws Exception {
        // knownUsers() returns stable userId UUIDs (not display names) in the new model
        assertThat(scoreRepository.knownUsers()).contains(ALICE_USER_ID);

        // Replace with a different user's score entry (new-format)
        Files.writeString(SCORES_FILE, NEWUSER_USER_ID + ";w2;match;F\n");

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        assertThat(scoreRepository.knownUsers()).contains(NEWUSER_USER_ID);
        assertThat(scoreRepository.knownUsers()).doesNotContain(ALICE_USER_ID);
    }

    @Test
    void degradedDataCanBeFixedAndReloadedToHealthy() throws Exception {
        Files.writeString(WORDS_FILE, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                w1;Letter;Betű;;true
                w1;Letter duplicate;Betű;;true
                """);

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        var resultWithErrors = mockMvc.perform(get("/")).andReturn();
        var errorsModel = Objects.requireNonNull(resultWithErrors.getModelAndView()).getModel();
        assertThat(errorsModel.get("wordsHealthy")).isEqualTo(false);
        assertThat((java.util.List<?>) errorsModel.get("wordErrors")).isNotEmpty();

        writeValidDictionary();

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", true))
                .andExpect(model().attribute("wordErrors", java.util.List.of()));
    }

    @Test
    void reloadWithStillInvalidDataRemainsDegradedWithUpdatedErrors() throws Exception {
        Files.writeString(WORDS_FILE, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                w1;;Betű;;true
                """);

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        var firstResult = mockMvc.perform(get("/health/data")).andReturn();
        var firstModel = Objects.requireNonNull(firstResult.getModelAndView()).getModel();
        assertThat(firstModel.get("wordsHealthy")).isEqualTo(false);
        var firstErrors = (java.util.List<?>) firstModel.get("wordErrors");
        assertThat(firstErrors).isNotEmpty();

        Files.writeString(WORDS_FILE, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                w1;Letter;;;true
                """);

        mockMvc.perform(post("/admin/data/reload").with(httpBasic("admin", "admin")))
                .andExpect(status().is3xxRedirection());

        var secondResult = mockMvc.perform(get("/health/data")).andReturn();
        var secondModel = Objects.requireNonNull(secondResult.getModelAndView()).getModel();
        assertThat(secondModel.get("wordsHealthy")).isEqualTo(false);
        var secondErrors = (java.util.List<?>) secondModel.get("wordErrors");
        assertThat(secondErrors).isNotEmpty();
        assertThat(secondErrors).isNotEqualTo(firstErrors);
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

    private static void writeValidDictionary() throws IOException {
        Files.writeString(WORDS_FILE, """
                WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED
                w1;Letter;Betű;;true
                w2;Stone;Kő;;true
                """);
        Files.writeString(MODE_ELIGIBILITY_FILE, """
                WORD_ID;MODE;ENABLED
                w1;flashcards;true
                w1;match;true
                w2;flashcards;true
                w2;match;true
                """);
    }
}
