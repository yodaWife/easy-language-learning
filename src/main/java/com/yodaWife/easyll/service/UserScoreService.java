package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserScoreService {

    /**
     * Append a single S/F result for the given score key.
     *
     * @param histories mutable map to update in-place
     * @param key       score key (userId + pairId + mode)
     * @param result    "S" or "F"
     */
    public void append(Map<ScoreKey, UserWordHistory> histories, ScoreKey key, String result) {
        histories.computeIfAbsent(key, k -> new UserWordHistory())
                 .append(result);
    }
}
