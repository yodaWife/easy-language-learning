package com.yodawife.easyll.service;

import com.yodawife.easyll.config.MatchGameProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SessionStoreTest {

    private MatchGameProperties defaultProperties() {
        return new MatchGameProperties();
    }

    @Test
    @DisplayName("Session is retrievable immediately after creation")
    void sessionIsRetrievableAfterCreation() {
        var store = new SessionStore(defaultProperties());
        var session = store.create(null, "match");

        assertThat(store.get(session.getSessionId())).isPresent();
    }

    @Test
    @DisplayName("Session is evicted when TTL has elapsed")
    void sessionIsEvictedAfterTtlExpires() {
        var futureClock = Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC);
        var store = new SessionStore(defaultProperties(), futureClock);
        var session = store.create(null, "match");

        assertThat(store.get(session.getSessionId())).isEmpty();
    }

    @Test
    @DisplayName("Expired session is removed from the store on lazy eviction")
    void expiredSessionIsRemovedFromStoreOnGet() {
        var futureClock = Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC);
        var store = new SessionStore(defaultProperties(), futureClock);
        var session = store.create(null, "match");
        var sessionId = session.getSessionId();

        store.get(sessionId); // triggers lazy removal

        assertThat(store.get(sessionId)).isEmpty();
    }

    @Test
    @DisplayName("Session within TTL is still available")
    void sessionWithinTtlIsStillAvailable() {
        var halfwayThroughTtl = Clock.fixed(Instant.now().plus(Duration.ofMinutes(30)), ZoneOffset.UTC);
        var store = new SessionStore(defaultProperties(), halfwayThroughTtl);
        var session = store.create(null, "match");

        assertThat(store.get(session.getSessionId())).isPresent();
    }

    @Test
    @DisplayName("get returns empty for null sessionId")
    void getReturnsEmptyForNullSessionId() {
        var store = new SessionStore(defaultProperties());

        assertThat(store.get(null)).isEmpty();
    }

    @Test
    @DisplayName("remove deletes session from store")
    void removeDeletesSession() {
        var store = new SessionStore(defaultProperties());
        var session = store.create(null, "match");

        store.remove(session.getSessionId());

        assertThat(store.get(session.getSessionId())).isEmpty();
    }

    @Test
    @DisplayName("remove is a no-op for null sessionId")
    void removeIsNoOpForNullSessionId() {
        var store = new SessionStore(defaultProperties());

        store.remove(null); // should not throw
        assertThat(store.get(null)).isEmpty();
    }

    @Test
    @DisplayName("lastAccessedAt is updated to clock instant when session is retrieved")
    void lastAccessedAtUpdatedOnGet() {
        var touchTime = Instant.now().plus(Duration.ofMinutes(30));
        var fixedClock = Clock.fixed(touchTime, ZoneOffset.UTC);
        var store = new SessionStore(defaultProperties(), fixedClock);
        var session = store.create(null, "match");

        store.get(session.getSessionId());

        assertThat(session.lastAccessedAt()).isEqualTo(touchTime);
    }

    @Test
    @DisplayName("Scheduled cleanup evicts sessions inactive for longer than TTL")
    void scheduledCleanupEvictsExpiredSessions() {
        var futureClock = Clock.fixed(Instant.now().plus(Duration.ofHours(2)), ZoneOffset.UTC);
        var store = new SessionStore(defaultProperties(), futureClock);
        var session = store.create(null, "match");

        store.cleanupExpiredSessions();

        assertThat(store.get(session.getSessionId())).isEmpty();
    }

    @Test
    @DisplayName("Scheduled cleanup does not evict sessions accessed within TTL")
    void scheduledCleanupDoesNotEvictRecentlyAccessedSessions() {
        var clockAt30Min = Clock.fixed(Instant.now().plus(Duration.ofMinutes(30)), ZoneOffset.UTC);
        var store = new SessionStore(defaultProperties(), clockAt30Min);
        var session = store.create(null, "match");

        store.get(session.getSessionId()); // touches lastAccessedAt to T+30min; new TTL expires at T+90min
        store.cleanupExpiredSessions();    // clock is T+30min < T+90min — session should survive

        assertThat(store.get(session.getSessionId())).isPresent();
    }
}
