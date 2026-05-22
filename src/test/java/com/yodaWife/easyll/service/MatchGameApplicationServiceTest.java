package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.AttemptResult;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchCard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.domain.WordEntry;
import com.yodawife.easyll.repository.ScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchGameApplicationServiceTest {

    private final MatchSessionService matchSessionService = mock(MatchSessionService.class);
    private final ScoreRepository scoreRepository = mock(ScoreRepository.class);
    private final MatchGameApplicationService service =
            new MatchGameApplicationService(matchSessionService, scoreRepository);

    private MatchBoard boardWithPair(WordEntry entry) {
        var pairId = MatchCard.buildPairId(entry.fromWord(), entry.toWord());
        var left = List.of(new MatchCard(entry.fromWord(), pairId, true, "from"));
        var right = List.of(new MatchCard(entry.toWord(), pairId, false, "to"));
        return new MatchBoard(left, right, List.of(entry), Set.of());
    }

    private MatchBoard boardWithTwoPairs(WordEntry entry1, WordEntry entry2) {
        var pairId1 = MatchCard.buildPairId(entry1.fromWord(), entry1.toWord());
        var pairId2 = MatchCard.buildPairId(entry2.fromWord(), entry2.toWord());
        var left = List.of(
                new MatchCard(entry1.fromWord(), pairId1, true, "from"),
                new MatchCard(entry2.fromWord(), pairId2, true, "from")
        );
        var right = List.of(
                new MatchCard(entry1.toWord(), pairId1, false, "to"),
                new MatchCard(entry2.toWord(), pairId2, false, "to")
        );
        return new MatchBoard(left, right, List.of(entry1, entry2), Set.of());
    }

    @Test
    @DisplayName("generateBoard delegates to MatchSessionService")
    void generateBoardDelegatesToMatchSessionService() {
        var pair = new WordEntry("Letter", "Betű", "");
        var expected = boardWithPair(pair);
        when(matchSessionService.generateBoard()).thenReturn(expected);

        var result = service.generateBoard();

        assertThat(result).isEqualTo(expected);
        verify(matchSessionService).generateBoard();
    }

    @Test
    @DisplayName("processAttempt delegates to MatchSessionService")
    void processAttemptDelegatesToMatchSessionService() {
        var session = new MatchSession(null, "match");
        var pair = new WordEntry("Letter", "Betű", "");
        var board = boardWithPair(pair);
        var expected = new AttemptResult(true, false, 1);
        when(matchSessionService.processAttempt(session, board, "Letter", "Betű")).thenReturn(expected);

        var result = service.processAttempt(session, board, "Letter", "Betű");

        assertThat(result).isEqualTo(expected);
        verify(matchSessionService).processAttempt(session, board, "Letter", "Betű");
    }

    @Test
    @DisplayName("computeNextBoard returns the same board when attempt is incorrect")
    void computeNextBoardReturnsSameBoardOnIncorrect() {
        var pair = new WordEntry("Letter", "Betű", "");
        var board = boardWithPair(pair);
        var incorrectResult = new AttemptResult(false, false, 1);

        var next = service.computeNextBoard(board, incorrectResult, "Letter", "Wrong");

        assertThat(next).isEqualTo(board);
        verify(matchSessionService, never()).generateBoard();
    }

    @Test
    @DisplayName("computeNextBoard marks matched pair when attempt is correct and board not complete")
    void computeNextBoardMarksPairWhenCorrectAndNotAllMatched() {
        var pair1 = new WordEntry("Letter", "Betű", "");
        var pair2 = new WordEntry("Stone", "Kő", "");
        var board = boardWithTwoPairs(pair1, pair2);
        var correctResult = new AttemptResult(true, false, 1);
        var expectedPairId = MatchCard.buildPairId("Letter", "Betű");

        var next = service.computeNextBoard(board, correctResult, "Letter", "Betű");

        assertThat(next.matchedPairIds()).containsExactly(expectedPairId);
        verify(matchSessionService, never()).generateBoard();
    }

    @Test
    @DisplayName("computeNextBoard generates a fresh board when all pairs are matched")
    void computeNextBoardGeneratesFreshBoardWhenAllMatched() {
        var pair = new WordEntry("Letter", "Betű", "");
        var board = boardWithPair(pair);
        var correctResult = new AttemptResult(true, false, 1);
        var freshBoard = boardWithPair(new WordEntry("Stone", "Kő", ""));
        when(matchSessionService.generateBoard()).thenReturn(freshBoard);

        var next = service.computeNextBoard(board, correctResult, "Letter", "Betű");

        assertThat(next).isEqualTo(freshBoard);
        verify(matchSessionService).generateBoard();
    }

    @Test
    @DisplayName("recordAttempt buffers attempts internally per sessionId")
    void recordAttemptBuffersAttemptsPerSessionId() {
        var session = new MatchSession("alice", "match");
        when(matchSessionService.resultMessage(session)).thenReturn("Done!");

        service.recordAttempt(session.getSessionId(), "Letter", "Betű", new AttemptResult(true, false, 1));
        service.recordAttempt(session.getSessionId(), "Stone", "Kő", new AttemptResult(false, false, 2));
        service.finaliseSession(session);

        verify(scoreRepository).appendAttempt("alice", "Letter", "Betű", "S");
        verify(scoreRepository).appendAttempt("alice", "Stone", "Kő", "F");
        verify(scoreRepository).flush();
    }

    @Test
    @DisplayName("finaliseSession persists scores and returns message when nickname is present")
    void finaliseSessionPersistsScoresAndReturnsMessageWhenNicknamePresent() {
        var session = new MatchSession("alice", "match");
        for (int i = 0; i < 10; i++) session.recordSuccess();
        service.recordAttempt(session.getSessionId(), "Letter", "Betű", new AttemptResult(true, false, 1));
        service.recordAttempt(session.getSessionId(), "Stone", "Kő", new AttemptResult(false, false, 2));
        when(matchSessionService.resultMessage(session)).thenReturn("You did it!");

        var message = service.finaliseSession(session);

        assertThat(message).isEqualTo("You did it!");
        verify(scoreRepository).appendAttempt("alice", "Letter", "Betű", "S");
        verify(scoreRepository).appendAttempt("alice", "Stone", "Kő", "F");
        verify(scoreRepository).flush();
    }

    @Test
    @DisplayName("finaliseSession skips score persistence when nickname is absent")
    void finaliseSessionSkipsScorePersistenceWhenNoNickname() {
        var session = new MatchSession(null, "match");
        for (int i = 0; i < 10; i++) session.recordSuccess();
        service.recordAttempt(session.getSessionId(), "Letter", "Betű", new AttemptResult(true, false, 1));
        when(matchSessionService.resultMessage(session)).thenReturn("You did it!");

        var message = service.finaliseSession(session);

        assertThat(message).isEqualTo("You did it!");
        verify(scoreRepository, never()).appendAttempt(any(), any(), any(), any());
        verify(scoreRepository, never()).flush();
    }
}
