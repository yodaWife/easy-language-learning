package com.yodawife.easyll.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSessionTest {

    @Test
    @DisplayName("sessionId is assigned at creation")
    void sessionIdIsAssignedAtCreation() {
        MatchSession session = new MatchSession("alice", "match");
        assertThat(session.getSessionId()).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("createdAt is set to approximately now at construction time")
    void createdAtIsSetAtConstruction() {
        var before = Instant.now();
        var session = new MatchSession("alice", "match");
        var after = Instant.now();
        assertThat(session.createdAt()).isBetween(before, after);
    }

    @Test
    void blankNicknameIsStoredAsNull() {
        MatchSession session = new MatchSession("  ", "flashcards");
        assertThat(session.getUserId()).isNull();
        assertThat(session.hasUserId()).isFalse();
    }

    @Test
    void nullNicknameIsStoredAsNull() {
        MatchSession session = new MatchSession(null, "flashcards");
        assertThat(session.getUserId()).isNull();
    }

    @Test
    void nicknameIsTrimmed() {
        MatchSession session = new MatchSession("  bob  ", "match");
        assertThat(session.getUserId()).isEqualTo("bob");
        assertThat(session.hasUserId()).isTrue();
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
    void isCompleteAfterReachingConfiguredMaxAttempts() {
        MatchSession session = new MatchSession(null, "match", 10);
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
