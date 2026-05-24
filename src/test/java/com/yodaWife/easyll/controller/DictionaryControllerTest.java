package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.service.DictionaryEditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class DictionaryControllerTest {

    @Autowired
    WebApplicationContext context;

    @MockitoBean
    DictionaryEditService dictionaryEditService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("GET /dictionary returns 200 and renders dictionary view")
    void dictionaryPageReturns200AndRendersDictionaryView() throws Exception {
        mockMvc.perform(get("/dictionary"))
                .andExpect(status().isOk())
                .andExpect(view().name("dictionary"));
    }

    @Test
    @DisplayName("GET /dictionary adds languages and words to model")
    void dictionaryPageAddsLanguagesAndWordsToModel() throws Exception {
        mockMvc.perform(get("/dictionary"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("languages", "words"));
    }

    @Test
    @DisplayName("GET /dictionary/rows returns 200 as HTMX table fragment")
    void dictionaryRowsReturns200() throws Exception {
        mockMvc.perform(get("/dictionary/rows"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /dictionary/rows with search param returns 200 with words in model")
    void dictionaryRowsWithSearchParamFiltersWords() throws Exception {
        mockMvc.perform(get("/dictionary/rows").param("search", "hello"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("words"));
    }

    @Test
    @DisplayName("GET /dictionary/rows with sortBy=TO and sortDir=DESC returns 200")
    void dictionaryRowsWithSortByToAndSortDirDescReturns200() throws Exception {
        mockMvc.perform(get("/dictionary/rows")
                        .param("sortBy", "TO")
                        .param("sortDir", "DESC"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /dictionary/rows with pageSize=9999 is capped at 100 without error")
    void dictionaryRowsPageSizeCappedAt100() throws Exception {
        mockMvc.perform(get("/dictionary/rows").param("pageSize", "9999"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("pageSize", 100));
    }

    @Test
    @DisplayName("POST /dictionary/toggle/global with valid data returns 200 on success")
    void toggleGlobalWithValidDataReturns200OnSuccess() throws Exception {
        var updatedWord = new Word(new WordId("1"), "hello", "cześć", "", false);
        when(dictionaryEditService.toggleGlobalEnabled(anyString(), any(WordId.class)))
                .thenReturn(new DictionaryOperationResult.Success<>(updatedWord));

        mockMvc.perform(post("/dictionary/toggle/global")
                        .param("languageCode", "en")
                        .param("wordId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /dictionary/toggle/global returns 200 with error in model when service returns Failure")
    void toggleGlobalReturns200WithErrorWhenServiceReturnsFailure() throws Exception {
        when(dictionaryEditService.toggleGlobalEnabled(anyString(), any(WordId.class)))
                .thenReturn(new DictionaryOperationResult.Failure<>("Word not found: 99"));

        mockMvc.perform(post("/dictionary/toggle/global")
                        .param("languageCode", "en")
                        .param("wordId", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /dictionary/toggle/mode with languageCode, wordId, mode returns 200")
    void toggleModeReturns200() throws Exception {
        var eligibility = new ModeEligibility(new WordId("1"), "flashcards", false);
        when(dictionaryEditService.toggleModeEnabled(anyString(), any(WordId.class), anyString()))
                .thenReturn(new DictionaryOperationResult.Success<>(eligibility));

        mockMvc.perform(post("/dictionary/toggle/mode")
                        .param("languageCode", "hun")
                        .param("wordId", "1")
                        .param("mode", "flashcards"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("All dictionary endpoints return 200 for unauthenticated requests (FR-050)")
    void allDictionaryEndpointsReturn200ForUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/dictionary")).andExpect(status().isOk());
        mockMvc.perform(get("/dictionary/rows")).andExpect(status().isOk());

        var updatedWord = new Word(new WordId("1"), "hello", "cześć", "", false);
        when(dictionaryEditService.toggleGlobalEnabled(anyString(), any(WordId.class)))
                .thenReturn(new DictionaryOperationResult.Success<>(updatedWord));

        mockMvc.perform(post("/dictionary/toggle/global")
                        .param("languageCode", "en")
                        .param("wordId", "1"))
                .andExpect(status().isOk());
    }
}
