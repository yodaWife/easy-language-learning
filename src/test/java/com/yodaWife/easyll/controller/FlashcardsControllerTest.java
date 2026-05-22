package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
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
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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
    @DisplayName("GET /flashcards/card returns card fragment when flashcards session is active")
    void cardPartialReturns200() throws Exception {
        var session = sessionStore.create(null, "flashcards");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/flashcards/card").session(httpSession))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /flashcards/card redirects to / when no session is present (browser request)")
    void cardPartialRedirectsToHomeWithNoSession() throws Exception {
        mockMvc.perform(get("/flashcards/card"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("GET /flashcards/card returns HX-Redirect when no session is present (HTMX request)")
    void cardPartialReturnsHxRedirectWithNoSession() throws Exception {
        mockMvc.perform(get("/flashcards/card").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/"));
    }

    @Test
    @DisplayName("GET /flashcards/card redirects to / when session mode is match (browser request)")
    void cardPartialRedirectsWhenSessionModeIsMatch() throws Exception {
        var session = sessionStore.create(null, "match");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/flashcards/card").session(httpSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("GET /flashcards/card returns HX-Redirect when session mode is match (HTMX request)")
    void cardPartialReturnsHxRedirectWhenSessionModeIsMatch() throws Exception {
        var session = sessionStore.create(null, "match");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/flashcards/card").session(httpSession).header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/"));
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
