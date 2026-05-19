package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = "app.scores.file-path=./scores-test.csv")
class FlashcardsControllerTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    SessionStore sessionStore;

    @Autowired
    DataHealthService dataHealthService;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void flashcardsPageRedirectsToHomeWithNoSession() throws Exception {
        mockMvc.perform(get("/flashcards"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void flashcardsPageLoadsWithValidSession() throws Exception {
        MatchSession session = sessionStore.create(null, "flashcards");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/flashcards").session(httpSession))
                .andExpect(status().isOk())
                .andExpect(view().name("flashcards"))
                .andExpect(model().attributeExists("card", "fromLang", "toLang"));
    }

    @Test
    void flashcardsPageRedirectsWhenSessionModeIsMatch() throws Exception {
        MatchSession session = sessionStore.create(null, "match");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/flashcards").session(httpSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void cardPartialReturns200() throws Exception {
        mockMvc.perform(get("/flashcards/card"))
                .andExpect(status().isOk());
    }

    @Test
    void flashcardsPageRedirectsWhenDataIsDegraded() throws Exception {
        MatchSession session = sessionStore.create(null, "flashcards");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        dataHealthService.reportRuntimeError("forced degraded state for test");

        mockMvc.perform(get("/flashcards").session(httpSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        dataHealthService.reload();
    }
}
