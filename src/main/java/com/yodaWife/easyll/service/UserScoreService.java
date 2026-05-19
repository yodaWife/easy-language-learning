package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.domain.UserWordKey;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserScoreService {

    /**
     * Append a single S/F result for a (user, fromWord, toWord) key.
     * If the key doesn't exist in the map yet, a new UserWordHistory is created.
     * FIFO max-10 is enforced inside UserWordHistory.append().
     *
     * @param histories mutable map to update in-place
     * @param user      nickname
     * @param fromWord  the FROM word of the pair
     * @param toWord    the TO word of the pair
     * @param result    "S" or "F"
     */
    public void append(Map<UserWordKey, UserWordHistory> histories,
                       String user, String fromWord, String toWord, String result) {
        UserWordKey key = new UserWordKey(user, fromWord, toWord);
        histories.computeIfAbsent(key, k -> new UserWordHistory())
                 .append(result);
    }
}
