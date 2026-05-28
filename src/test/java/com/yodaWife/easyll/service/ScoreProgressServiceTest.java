package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.repository.ScoreRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScoreProgressServiceTest {

    private final ScoreRepository scoreRepo = mock(ScoreRepository.class);
    private final ScoreProgressService service = new ScoreProgressService(scoreRepo);

    @Test
    @DisplayName("Returns empty map when user has no history")
    void noHistory_returnsEmptyMap() {
        when(scoreRepo.getHistoriesForUser("user-1")).thenReturn(Map.of());

        assertThat(service.getProgressForUser("user-1")).isEmpty();
    }

    @Test
    @DisplayName("3 successes out of MAX_HISTORY = 25%")
    void threeSuccesses_returns25Percent() {
        when(scoreRepo.getHistoriesForUser("user-1"))
                .thenReturn(Map.of("pair-1", List.of("S", "S", "S")));

        var result = service.getProgressForUser("user-1");

        assertThat(result).containsEntry("pair-1", 25);
    }

    @Test
    @DisplayName("12 successes = 100%")
    void twelveSuccesses_returns100Percent() {
        var allSuccess = List.of("S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S", "S");
        assertThat(allSuccess).hasSize(UserWordHistory.MAX_HISTORY);
        when(scoreRepo.getHistoriesForUser("user-1")).thenReturn(Map.of("pair-1", allSuccess));

        assertThat(service.getProgressForUser("user-1")).containsEntry("pair-1", 100);
    }

    @Test
    @DisplayName("0 successes = 0%")
    void noSuccesses_returns0Percent() {
        when(scoreRepo.getHistoriesForUser("user-1"))
                .thenReturn(Map.of("pair-1", List.of("F", "F", "F")));

        assertThat(service.getProgressForUser("user-1")).containsEntry("pair-1", 0);
    }

    @Test
    @DisplayName("Mixed results across multiple pairs are computed independently")
    void multiplePairs_computedIndependently() {
        when(scoreRepo.getHistoriesForUser("user-1")).thenReturn(Map.of(
                "pair-a", List.of("S", "S", "S", "S", "S", "S"),   // 6/12 = 50%
                "pair-b", List.of("F", "F", "F", "F")               // 0/12 = 0%
        ));

        var result = service.getProgressForUser("user-1");

        assertThat(result).containsEntry("pair-a", 50);
        assertThat(result).containsEntry("pair-b", 0);
    }

    @Test
    @DisplayName("Empty history list for a pair is excluded from result")
    void emptyHistoryList_excluded() {
        when(scoreRepo.getHistoriesForUser("user-1"))
                .thenReturn(Map.of("pair-1", List.of()));

        assertThat(service.getProgressForUser("user-1")).doesNotContainKey("pair-1");
    }
}
