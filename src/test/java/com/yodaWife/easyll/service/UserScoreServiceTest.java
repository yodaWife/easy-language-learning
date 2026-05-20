package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.domain.UserWordKey;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class UserScoreServiceTest {

    UserScoreService service = new UserScoreService();

    @Test
    void appendCreatesNewHistoryForNewKey() {
        Map<UserWordKey, UserWordHistory> histories = new HashMap<>();
        service.append(histories, "alice", "Letter", "Betű", "S");

        UserWordKey key = new UserWordKey("alice", "Letter", "Betű");
        assertThat(histories).containsKey(key);
        assertThat(Objects.requireNonNull(histories.get(key)).entries()).containsExactly("S");
    }

    @Test
    void appendAddsToExistingHistory() {
        Map<UserWordKey, UserWordHistory> histories = new HashMap<>();
        service.append(histories, "alice", "Letter", "Betű", "S");
        service.append(histories, "alice", "Letter", "Betű", "F");

        UserWordKey key = new UserWordKey("alice", "Letter", "Betű");
    assertThat(Objects.requireNonNull(histories.get(key)).entries()).containsExactly("S", "F");
    }

    @Test
    void appendIsolatesByKey() {
        Map<UserWordKey, UserWordHistory> histories = new HashMap<>();
        service.append(histories, "alice", "Letter", "Betű", "S");
        service.append(histories, "bob",   "Letter", "Betű", "F");

        assertThat(histories).hasSize(2);
    assertThat(Objects.requireNonNull(histories.get(new UserWordKey("alice", "Letter", "Betű"))).entries())
        .containsExactly("S");
    assertThat(Objects.requireNonNull(histories.get(new UserWordKey("bob", "Letter", "Betű"))).entries())
        .containsExactly("F");
    }

    @Test
    void fifoIsEnforcedAcrossMultipleAppends() {
        Map<UserWordKey, UserWordHistory> histories = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            service.append(histories, "alice", "A", "B", i % 2 == 0 ? "S" : "F");
        }
        UserWordKey key = new UserWordKey("alice", "A", "B");
        assertThat(Objects.requireNonNull(histories.get(key)).entries()).hasSize(10);
    }
}
