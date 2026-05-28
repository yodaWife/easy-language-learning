package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class UserScoreServiceTest {

    UserScoreService service = new UserScoreService();

    @Test
    void appendCreatesNewHistoryForNewKey() {
        Map<ScoreKey, UserWordHistory> histories = new HashMap<>();
        var key = new ScoreKey("user-1", "pair-1", "match");
        service.append(histories, key, "S");

        assertThat(histories).containsKey(key);
        assertThat(Objects.requireNonNull(histories.get(key)).entries()).containsExactly("S");
    }

    @Test
    void appendAddsToExistingHistory() {
        Map<ScoreKey, UserWordHistory> histories = new HashMap<>();
        var key = new ScoreKey("user-1", "pair-1", "match");
        service.append(histories, key, "S");
        service.append(histories, key, "F");

        assertThat(Objects.requireNonNull(histories.get(key)).entries()).containsExactly("S", "F");
    }

    @Test
    void appendIsolatesByKey() {
        Map<ScoreKey, UserWordHistory> histories = new HashMap<>();
        var aliceKey = new ScoreKey("user-1", "pair-1", "match");
        var bobKey = new ScoreKey("user-2", "pair-1", "match");
        service.append(histories, aliceKey, "S");
        service.append(histories, bobKey, "F");

        assertThat(histories).hasSize(2);
        assertThat(Objects.requireNonNull(histories.get(aliceKey)).entries()).containsExactly("S");
        assertThat(Objects.requireNonNull(histories.get(bobKey)).entries()).containsExactly("F");
    }

    @Test
    void fifoIsEnforcedAcrossMultipleAppends() {
        Map<ScoreKey, UserWordHistory> histories = new HashMap<>();
        var key = new ScoreKey("user-1", "pair-1", "match");
        for (int i = 0; i < 14; i++) {
            service.append(histories, key, i % 2 == 0 ? "S" : "F");
        }
        assertThat(Objects.requireNonNull(histories.get(key)).entries()).hasSize(12);
    }
}
