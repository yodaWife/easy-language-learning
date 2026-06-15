package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.service.SessionStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MatchControllerTest extends AbstractControllerIntegrationTest {

    @Autowired
    SessionStore sessionStore;

    @Test
    @DisplayName("Match page redirects to home when no session exists")
    void matchPageRedirectsToHomeWithNoSession() throws Exception {
        mockMvc.perform(get("/match"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Match page loads with a valid match session")
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
    @DisplayName("Match page redirects when session mode is flashcards")
    void matchPageRedirectsWhenSessionModeIsFlashcards() throws Exception {
        MatchSession session = sessionStore.create(null, "flashcards");
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Attempt POST increments attempt count")
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
    @DisplayName("Correct attempt marks one pair as matched")
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
    @DisplayName("Incorrect attempt keeps board unmatched")
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
    @DisplayName("Session completes after ten successful attempts and redirects to result")
    void sessionCompletesAfterTenSuccessfulAttemptsAndRedirectsToResult() throws Exception {
        var session = sessionStore.create("alice", "match");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());
        mockMvc.perform(get("/match").session(httpSession)).andReturn();

        // board-size=5; 10 successes require matching all pairs on 2 consecutive boards
        var board1 = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        for (var pair : board1.pairs()) {
            mockMvc.perform(post("/match/attempt").session(httpSession)
                            .param("fromWord", pair.fromWord())
                            .param("toWord", pair.toWord()))
                    .andExpect(status().isOk());
        }

        var board2 = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        var pairs2 = board2.pairs();
        for (int i = 0; i < pairs2.size() - 1; i++) {
            mockMvc.perform(post("/match/attempt").session(httpSession)
                            .param("fromWord", pairs2.get(i).fromWord())
                            .param("toWord", pairs2.get(i).toWord()))
                    .andExpect(status().isOk());
        }

        var lastPair = pairs2.getLast();
        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", lastPair.fromWord())
                        .param("toWord", lastPair.toWord()))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Redirect", "/match/result"));

        assertThat(sessionStore.get(session.getSessionId())).isPresent();
        assertThat(sessionStore.get(session.getSessionId()).orElseThrow().getAttempts()).isEqualTo(10);
    }

    @Test
    @DisplayName("Replaying an already-matched pair records a failure")
    void replayAlreadyMatchedPairRecordsFailure() throws Exception {
        var session = sessionStore.create(null, "match");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());

        mockMvc.perform(get("/match").session(httpSession)).andReturn();
        var board = (MatchBoard) Objects.requireNonNull(httpSession.getAttribute("currentBoard"));
        var correctPair = board.pairs().getFirst();

        // First attempt - correct match
        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", correctPair.fromWord())
                        .param("toWord", correctPair.toWord()))
                .andExpect(status().isOk());

        // Second attempt - replay the same already-matched pair
        mockMvc.perform(post("/match/attempt").session(httpSession)
                        .param("fromWord", correctPair.fromWord())
                        .param("toWord", correctPair.toWord()))
                .andExpect(status().isOk());

        var updated = sessionStore.get(session.getSessionId()).orElseThrow();
        assertThat(updated.getSuccesses()).isEqualTo(1);
        assertThat(updated.getFailures()).isEqualTo(1);
        assertThat(updated.getAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("Result page redirects when no result session is present")
    void resultPageRedirectsWhenNoResultSession() throws Exception {
        mockMvc.perform(get("/match/result"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("Session is removed from store after result page is served")
    void sessionRemovedFromStoreAfterResultPage() throws Exception {
        var session = sessionStore.create("alice", "match");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());
        httpSession.setAttribute("resultSession", session);
        httpSession.setAttribute("resultMessage", "You did it!");

        mockMvc.perform(get("/match/result").session(httpSession))
                .andExpect(status().isOk())
                .andExpect(view().name("match/result"));

        assertThat(sessionStore.get(session.getSessionId())).isEmpty();
    }

    @Test
    @DisplayName("GET /match with languageCode in session loads board using that language")
    void matchPageUsesLanguageCodeFromSession() throws Exception {
        var session = sessionStore.create(null, "match");
        var httpSession = new MockHttpSession();
        httpSession.setAttribute("sessionId", session.getSessionId());
        httpSession.setAttribute("languageCode", "en");

        mockMvc.perform(get("/match").session(httpSession))
                .andExpect(status().isOk())
                .andExpect(view().name("match"))
                .andExpect(model().attributeExists("board", "attempts", "maxAttempts"));
    }
}
