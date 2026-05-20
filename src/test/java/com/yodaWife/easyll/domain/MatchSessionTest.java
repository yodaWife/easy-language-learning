package com.yodawife.easyll.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSessionTest {

    @Test
    void sessionIdIsAssignedAtCreation() {
        MatchSession session = new MatchSession("alice", "match");
        assertThat(session.getSessionId()).isNotNull().isNotBlank();
    }

    @Test
    void blankNicknameIsStoredAsNull() {
        MatchSession session = new MatchSession("  ", "flashcards");
        assertThat(session.getNickname()).isNull();
        assertThat(session.hasNickname()).isFalse();
    }

    @Test
    void nullNicknameIsStoredAsNull() {
        MatchSession session = new MatchSession(null, "flashcards");
        assertThat(session.getNickname()).isNull();
    }

    @Test
    void nicknameIsTrimmed() {
        MatchSession session = new MatchSession("  bob  ", "match");
        assertThat(session.getNickname()).isEqualTo("bob");
        assertThat(session.hasNickname()).isTrue();
    }

    @Test
    void recordSuccessIncrementsAttemptsAndSuccesses() {
        MatchSession session = new MatchSession(null, "match");
        session.recordSuccess();
        assertThat(session.getAttempts()).isEqualTo(1);
        assertThat(session.getSuccesses()).isEqualTo(1);
        assertThat(session.getFailures()).isEqualTo(0);
    }

    @Test
    void recordFailureIncrementsAttemptsAndFailures() {
        MatchSession session = new MatchSession(null, "match");
        session.recordFailure();
        assertThat(session.getAttempts()).isEqualTo(1);
        assertThat(session.getFailures()).isEqualTo(1);
        assertThat(session.getSuccesses()).isEqualTo(0);
    }

    @Test
    void isCompleteAfter10SuccessfulAttempts() {
        MatchSession session = new MatchSession(null, "match");
        for (int i = 0; i < 9; i++) session.recordSuccess();
        assertThat(session.isComplete()).isFalse();
        session.recordSuccess();
        assertThat(session.isComplete()).isTrue();
    }

    @Test
    void isNotCompleteAfter10FailuresOnly() {
        MatchSession session = new MatchSession(null, "match");
        for (int i = 0; i < 10; i++) session.recordFailure();
        assertThat(session.isComplete()).isFalse();
    }

    @Test
    void successRateIsCorrect() {
        MatchSession session = new MatchSession(null, "match");
        for (int i = 0; i < 9; i++) session.recordSuccess();
        session.recordFailure();
        assertThat(session.successRatePercent()).isEqualTo(90);
    }

    @Test
    void successRateIsZeroWhenNoAttempts() {
        MatchSession session = new MatchSession(null, "match");
        assertThat(session.successRatePercent()).isEqualTo(0);
    }
}
