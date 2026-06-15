package com.yodawife.easyll.controller;

import com.yodawife.easyll.service.SessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HomeControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    SessionStore sessionStore;

    @Test
    void homePageLoads() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("wordsHealthy", "scoresHealthy", "wordErrors", "scoreErrors", "activeUser", "users"));
    }

    @Test
    void startSessionWithMatchModeRedirectsToMatch() throws Exception {
        mockMvc.perform(post("/session/start")
                        .param("mode", "match"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/match"));
    }

    @Test
    void startSessionWithFlashcardsModeRedirectsToFlashcards() throws Exception {
        mockMvc.perform(post("/session/start")
                        .param("mode", "flashcards"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flashcards"));
    }

    @Test
    void startSessionWithInvalidModeRedirectsToHome() throws Exception {
        mockMvc.perform(post("/session/start")
                        .param("mode", "invalid"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void startSessionWithNicknameIncludesItInSession() throws Exception {
        var mvcResult = mockMvc.perform(post("/session/start")
                        .param("mode", "match"))
                .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/match"))
            .andReturn();

        MockHttpSession httpSession = (MockHttpSession) Objects.requireNonNull(mvcResult.getRequest().getSession(false));
        String sessionId = (String) Objects.requireNonNull(httpSession.getAttribute("sessionId"));
        assertThat(sessionId).isNotBlank();
        assertThat(sessionStore.get(sessionId)).isPresent();
        assertThat(sessionStore.get(sessionId).orElseThrow().getUserId()).isNull();
    }

    @Test
    void startSessionWithoutNicknameCreatesAnonymousSession() throws Exception {
        var mvcResult = mockMvc.perform(post("/session/start")
                .param("mode", "flashcards"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/flashcards"))
            .andReturn();

        MockHttpSession httpSession = (MockHttpSession) Objects.requireNonNull(mvcResult.getRequest().getSession(false));
        String sessionId = (String) Objects.requireNonNull(httpSession.getAttribute("sessionId"));
        assertThat(sessionStore.get(sessionId)).isPresent();
        assertThat(sessionStore.get(sessionId).orElseThrow().getUserId()).isNull();
    }

    @Test
    void homePageIncludesLanguagesInModel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("languages"));
    }

    @Test
    void homePageIncludesPrimaryLanguageInModel() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("primaryLanguage"));
    }

    @Test
    void startSessionWithValidLanguageCodeStoresItInSession() throws Exception {
        var mvcResult = mockMvc.perform(post("/session/start")
                        .param("mode", "flashcards")
                        .param("languageCode", "hun"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flashcards"))
                .andReturn();

        var httpSession = (MockHttpSession) Objects.requireNonNull(mvcResult.getRequest().getSession(false));
        var storedLanguage = (String) httpSession.getAttribute("languageCode");
        assertThat(storedLanguage).isEqualTo("hun");
    }

    @Test
    void startSessionWithInvalidLanguageCodeFallsBackToPrimary() throws Exception {
        var mvcResult = mockMvc.perform(post("/session/start")
                        .param("mode", "flashcards")
                        .param("languageCode", "xx"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/flashcards"))
                .andReturn();

        var httpSession = (MockHttpSession) Objects.requireNonNull(mvcResult.getRequest().getSession(false));
        var storedLanguage = (String) httpSession.getAttribute("languageCode");
        assertThat(storedLanguage).isEqualTo("hun");
    }
}
