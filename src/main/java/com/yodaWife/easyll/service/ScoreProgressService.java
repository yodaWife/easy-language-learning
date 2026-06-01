package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.repository.ScoreReadRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes per-user learning progress from the score history.
 */
@Service
public class ScoreProgressService {

    private final ScoreReadRepository scoreRepository;

    public ScoreProgressService(ScoreReadRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    /**
     * Return a map of pairId → success percentage (0–100) for the given user.
     * Pairs with no history are absent from the map.
     *
     * @param userId the user identifier
     * @return pairId → successPercent (0–100)
     */
    public Map<String, Integer> getProgressForUser(String userId) {
        var histories = scoreRepository.getHistoriesForUser(userId);
        var result = new LinkedHashMap<String, Integer>();
        for (var entry : histories.entrySet()) {
            List<String> entries = entry.getValue();
            if (entries.isEmpty()) continue;
            long successes = entries.stream().filter("S"::equals).count();
            int percent = (int) Math.round(successes * 100.0 / UserWordHistory.MAX_HISTORY);
            result.put(entry.getKey(), percent);
        }
        return result;
    }
}
