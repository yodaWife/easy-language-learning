package com.yodawife.easyll.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public class MatchSession {

    public static final int DEFAULT_MAX_ATTEMPTS = 30;

    private final String sessionId;
    private final @Nullable String nickname;
    private final String mode;
    private final int maxAttempts;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;
    private int attempts;
    private int successes;
    private int failures;

    public MatchSession(@Nullable String nickname, String mode) {
        this(nickname, mode, DEFAULT_MAX_ATTEMPTS);
    }

    public MatchSession(@Nullable String nickname, String mode, int maxAttempts) {
        this.sessionId = UUID.randomUUID().toString();
        this.nickname = (nickname == null || nickname.isBlank()) ? null : nickname.trim();
        this.mode = mode;
        this.maxAttempts = maxAttempts;
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.attempts = 0;
        this.successes = 0;
        this.failures = 0;
    }

    public String getSessionId() { return sessionId; }
    public @Nullable String getNickname() { return nickname; }
    public String getMode() { return mode; }
    public int getMaxAttempts() { return maxAttempts; }
    public Instant createdAt() { return createdAt; }
    public Instant lastAccessedAt() { return lastAccessedAt; }
    public int getAttempts() { return attempts; }
    public int getSuccesses() { return successes; }
    public int getFailures() { return failures; }

    public void touch(Instant now) { this.lastAccessedAt = now; }

    public boolean hasNickname() { return nickname != null; }
    public boolean isComplete() { return successes >= maxAttempts; }

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
