package com.yodawife.easyll.repository.contract;

import com.yodawife.easyll.repository.ScoreReadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Shared contract tests that every ScoreReadRepository implementation must pass.
 */
public abstract class ScoreReadRepositoryContractTest {

    protected ScoreReadRepository repository;

    @BeforeEach
    void setUp() {
        repository = createRepository();
    }

    protected abstract ScoreReadRepository createRepository();

    /** Subclass hook to populate data that getHistoriesForUser can read. */
    protected abstract void populateHistory(String userId, String pairId, String mode, List<String> entries);

    @Test
    @DisplayName("getHistoriesForUser returns history map when data exists")
    void getHistoriesForUser_returnsHistories() {
        populateHistory("user-a", "pair-1", "match", List.of("S", "F", "S"));

        var histories = repository.getHistoriesForUser("user-a");

        assertThat(histories).containsKey("pair-1");
        assertThat(histories.get("pair-1")).contains("S", "F");
    }

    @Test
    @DisplayName("getHistoriesForUser returns empty map when user not found")
    void getHistoriesForUser_returnsEmptyMap_whenUserNotFound() {
        assertThat(repository.getHistoriesForUser("unknown-user")).isEmpty();
    }

    @Test
    @DisplayName("knownUsers returns user IDs when data exists")
    void knownUsers_returnsUserIds() {
        populateHistory("user-1", "pair-x", "match", List.of("S"));
        populateHistory("user-2", "pair-y", "match", List.of("F"));

        assertThat(repository.knownUsers()).contains("user-1", "user-2");
    }

    @Test
    @DisplayName("knownUsers returns empty set when no data")
    void knownUsers_returnsEmptySet_whenNoData() {
        assertThat(repository.knownUsers()).isEmpty();
    }
}
