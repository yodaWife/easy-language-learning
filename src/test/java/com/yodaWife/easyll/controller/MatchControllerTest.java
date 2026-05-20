package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.service.SessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class MatchControllerTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    SessionStore sessionStore;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void matchPageRedirectsToHomeWithNoSession() throws Exception {
        mockMvc.perform(get("/match"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void matchPageLoadsWithValidMatchSession() throws Exception {
        MatchSession session = sessionStore.create(null, "match");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession))
                .andExpect(status().isOk())
                .andExpect(view().name("match"))
                .andExpect(model().attributeExists("board", "attempts", "maxAttempts"));
    }

    @Test
    void matchPageRedirectsWhenSessionModeIsFlashcards() throws Exception {
        MatchSession session = sessionStore.create(null, "flashcards");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void attemptPostIncrementsAttemptCount() throws Exception {
        MatchSession session = sessionStore.create(null, "match");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        // First load to set up the board in HttpSession
        mockMvc.perform(get("/match").session(httpSession)).andReturn();

        // Submit an attempt
        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", "Letter")
                        .param("toWord", "Betű"))
                .andExpect(status().isOk());

        // Verify attempt was counted
        MatchSession updated = sessionStore.get(session.getSessionId()).orElseThrow();
        assertThat(updated.getAttempts()).isEqualTo(1);
    }

    @Test
    void correctAttemptMarksOnePairAsMatched() throws Exception {
        MatchSession session = sessionStore.create(null, "match");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession)).andReturn();
        MatchBoard initialBoard = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));

        var correctPair = initialBoard.pairs().getFirst();

        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", correctPair.fromWord())
                        .param("toWord", correctPair.toWord()))
                .andExpect(status().isOk());

        MatchBoard updatedBoard = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        assertThat(updatedBoard.matchedPairIds()).hasSize(1);
    }

    @Test
    void incorrectAttemptKeepsBoardUnmatched() throws Exception {
        MatchSession session = sessionStore.create(null, "match");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession)).andReturn();
        MatchBoard initialBoard = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        assertThat(initialBoard.pairs()).hasSizeGreaterThan(1);

        var first = initialBoard.pairs().get(0);
        var second = initialBoard.pairs().get(1);

        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", first.fromWord())
                        .param("toWord", second.toWord()))
                .andExpect(status().isOk());

        MatchBoard updatedBoard = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        assertThat(updatedBoard.matchedPairIds()).isEmpty();
    }

    @Test
    void sessionCompletesAfterTenSuccessfulAttemptsAndRedirectsToResult() throws Exception {
        MatchSession session = sessionStore.create("alice", "match");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession)).andReturn();

        for (int i = 0; i < 9; i++) {
            MatchBoard board = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
            var pair = board.pairs().getFirst();
            mockMvc.perform(post("/match/attempt").session(httpSession)
                            .param("fromWord", pair.fromWord())
                            .param("toWord", pair.toWord()))
                    .andExpect(status().isOk());
        }

        MatchBoard board = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        var pair = board.pairs().getFirst();
        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", pair.fromWord())
                        .param("toWord", pair.toWord()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/match/result"));

        assertThat(sessionStore.get(session.getSessionId())).isPresent();
        assertThat(sessionStore.get(session.getSessionId()).orElseThrow().getAttempts()).isEqualTo(10);
    }

    @Test
    void resultPageRedirectsWhenNoResultSession() throws Exception {
        mockMvc.perform(get("/match/result"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
