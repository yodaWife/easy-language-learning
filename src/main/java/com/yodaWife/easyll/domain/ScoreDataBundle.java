package com.yodawife.easyll.domain;

import java.util.Map;

public record ScoreDataBundle(Map<UserWordKey, UserWordHistory> histories) {
    public ScoreDataBundle {
        histories = Map.copyOf(histories);
    }

    public static ScoreDataBundle empty() {
        return new ScoreDataBundle(Map.of());
    }
}
