package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.AttemptResult;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchCard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.domain.WordEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MatchSessionServiceTest {

    @Mock
    MatchBoardGenerator boardGenerator;

    @InjectMocks
    MatchSessionService matchSessionService;

    private MatchBoard boardWithPairs(List<WordEntry> pairs) {
        List<MatchCard> left = pairs.stream()
                .map(e -> new MatchCard(e.fromWord(), e.fromWord() + "|" + e.toWord(), true, "from"))
                .toList();
        List<MatchCard> right = pairs.stream()
                .map(e -> new MatchCard(e.toWord(), e.fromWord() + "|" + e.toWord(), false, "to"))
                .toList();
        return new MatchBoard(left, right, pairs, java.util.Set.of());
    }

    @Test
    void correctAttemptRecordsSuccess() {
        MatchSession session = new MatchSession(null, "match");
        WordEntry pair = new WordEntry("Letter", "Betű", "");
        MatchBoard board = boardWithPairs(List.of(pair));

        AttemptResult result = matchSessionService.processAttempt(session, board, "Letter", "Betű");

        assertThat(result.correct()).isTrue();
        assertThat(session.getSuccesses()).isEqualTo(1);
        assertThat(session.getAttempts()).isEqualTo(1);
    }

    @Test
    void incorrectAttemptRecordsFailure() {
        MatchSession session = new MatchSession(null, "match");
        WordEntry pair = new WordEntry("Letter", "Betű", "");
        MatchBoard board = boardWithPairs(List.of(pair));

        AttemptResult result = matchSessionService.processAttempt(session, board, "Letter", "Wrong");

        assertThat(result.correct()).isFalse();
        assertThat(session.getFailures()).isEqualTo(1);
        assertThat(session.getAttempts()).isEqualTo(1);
    }

    @Test
    void sessionCompleteAfter10SuccessfulAttempts() {
        MatchSession session = new MatchSession(null, "match");
        WordEntry pair = new WordEntry("Letter", "Betű", "");
        MatchBoard board = boardWithPairs(List.of(pair));

        for (int i = 0; i < 9; i++) {
            AttemptResult r = matchSessionService.processAttempt(session, board, "Letter", "Betű");
            assertThat(r.sessionComplete()).isFalse();
        }
        AttemptResult last = matchSessionService.processAttempt(session, board, "Letter", "Betű");
        assertThat(last.sessionComplete()).isTrue();
    }

    @Test
    void sessionDoesNotCompleteAfterIncorrectAttemptsOnly() {
        MatchSession session = new MatchSession(null, "match");
        WordEntry pair = new WordEntry("Letter", "Betű", "");
        MatchBoard board = boardWithPairs(List.of(pair));

        for (int i = 0; i < 10; i++) {
            AttemptResult r = matchSessionService.processAttempt(session, board, "Letter", "Wrong");
            assertThat(r.sessionComplete()).isFalse();
        }
    }

    @Test
    void resultMessageFor100Percent() {
        MatchSession session = new MatchSession(null, "match");
        for (int i = 0; i < 10; i++) session.recordSuccess();
        assertThat(matchSessionService.resultMessage(session)).isEqualTo("You did it!");
    }

    @Test
    void resultMessageFor85to99Percent() {
        MatchSession session = new MatchSession(null, "match");
        for (int i = 0; i < 9; i++) session.recordSuccess(); // 90%
        session.recordFailure();
        assertThat(matchSessionService.resultMessage(session)).isEqualTo("Almost!");
    }

    @Test
    void resultMessageBelow85Percent() {
        MatchSession session = new MatchSession(null, "match");
        for (int i = 0; i < 8; i++) session.recordSuccess(); // 80%
        for (int i = 0; i < 2; i++) session.recordFailure();
        assertThat(matchSessionService.resultMessage(session)).isEqualTo("Let's practice some more!");
    }

    @Test
    void everyAttemptCountsRegardlessOfCorrectness() {
        MatchSession session = new MatchSession(null, "match");
        WordEntry pair = new WordEntry("A", "B", "");
        MatchBoard board = boardWithPairs(List.of(pair));

        matchSessionService.processAttempt(session, board, "A", "Wrong");  // incorrect
        matchSessionService.processAttempt(session, board, "A", "B");      // correct

        assertThat(session.getAttempts()).isEqualTo(2);
    }
}
