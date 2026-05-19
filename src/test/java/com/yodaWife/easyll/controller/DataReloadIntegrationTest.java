package com.yodawife.easyll.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
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
    }

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void resetToValidData() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nStone;Kő;\n");
        Files.writeString(SCORES_FILE, "alice;Letter;Betű;S\n");
        mockMvc.perform(post("/admin/data/reload")).andReturn();
    }

    @Test
    void degradedDataCanBeFixedAndReloadedToHealthy() throws Exception {
        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nLetter;Betű;\n");

        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("healthy", false));

        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter;Betű;\nCloud;Felhő;\n");

        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("healthy", true));
    }

    @Test
    void reloadWithStillInvalidDataRemainsDegradedWithUpdatedErrors() throws Exception {
        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\n ;Betű;\n");

        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("healthy", false));

        Files.writeString(WORDS_FILE, "ENGLISH;HUNGARIAN;EXAMPLE\nLetter; ;\n");

        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("healthy", false));
    }
}
