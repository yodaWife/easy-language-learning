package com.yodawife.easyll.service;

import com.yodawife.easyll.config.MatchGameProperties;
import com.yodawife.easyll.domain.MatchSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);

    private final MatchGameProperties matchGameProperties;
    private final Clock clock;
    private final ConcurrentHashMap<String, MatchSession> sessions = new ConcurrentHashMap<>();

    @Autowired
    public SessionStore(MatchGameProperties matchGameProperties) {
        this(matchGameProperties, Clock.systemUTC());
    }

    SessionStore(MatchGameProperties matchGameProperties, Clock clock) {
        this.matchGameProperties = matchGameProperties;
        this.clock = clock;
    }

    public MatchSession create(@Nullable String nickname, String mode) {
        var session = new MatchSession(nickname, mode, matchGameProperties.getMaxAttempts());
        sessions.put(session.getSessionId(), session);
        return session;
    }

    public Optional<MatchSession> get(@Nullable String sessionId) {
        if (sessionId == null) return Optional.empty();
        var session = sessions.get(sessionId);
        if (session == null) return Optional.empty();
        if (isExpired(session)) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        session.touch(clock.instant());
        return Optional.of(session);
    }

    public void remove(@Nullable String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    void cleanupExpiredSessions() {
        var ttl = Duration.ofMinutes(matchGameProperties.getSessionTtlMinutes());
        var now = clock.instant();
        sessions.entrySet().removeIf(entry -> entry.getValue().lastAccessedAt().plus(ttl).isBefore(now));
        log.debug("Expired session cleanup complete");
    }

    private boolean isExpired(MatchSession session) {
        var ttl = Duration.ofMinutes(matchGameProperties.getSessionTtlMinutes());
        return session.lastAccessedAt().plus(ttl).isBefore(clock.instant());
    }
}
