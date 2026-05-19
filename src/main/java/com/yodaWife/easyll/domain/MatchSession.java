package com.yodawife.easyll.domain;

import java.util.UUID;

public class MatchSession {

    private final String sessionId;
    private final String nickname;
    private final String mode;
    private int attempts;
    private int successes;
    private int failures;

    public MatchSession(String nickname, String mode) {
        this.sessionId = UUID.randomUUID().toString();
        this.nickname = (nickname == null || nickname.isBlank()) ? null : nickname.trim();
        this.mode = mode;
        this.attempts = 0;
        this.successes = 0;
        this.failures = 0;
    }

    public String getSessionId() { return sessionId; }
    public String getNickname() { return nickname; }
    public String getMode() { return mode; }
    public int getAttempts() { return attempts; }
    public int getSuccesses() { return successes; }
    public int getFailures() { return failures; }

    public boolean hasNickname() { return nickname != null; }
    public boolean isComplete() { return successes >= 30; }

    public void recordSuccess() {
        attempts++;
        successes++;
    }

    public void recordFailure() {
        attempts++;
        failures++;
    }

    public int successRatePercent() {
        if (attempts == 0) return 0;
        return (int) Math.round(successes * 100.0 / attempts);
    }
}
