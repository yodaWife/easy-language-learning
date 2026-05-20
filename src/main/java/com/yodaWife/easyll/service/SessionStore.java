package com.yodawife.easyll.service;

import com.yodawife.easyll.config.MatchGameProperties;
import com.yodawife.easyll.domain.MatchSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionStore {

    private final MatchGameProperties matchGameProperties;
    private final ConcurrentHashMap<String, MatchSession> sessions = new ConcurrentHashMap<>();

    public SessionStore(MatchGameProperties matchGameProperties) {
        this.matchGameProperties = matchGameProperties;
    }

    public MatchSession create(String nickname, String mode) {
        MatchSession session = new MatchSession(nickname, mode, matchGameProperties.getMaxAttempts());
        sessions.put(session.getSessionId(), session);
        return session;
    }

    public Optional<MatchSession> get(String sessionId) {
        if (sessionId == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }
}
