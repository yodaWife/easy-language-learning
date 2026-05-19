package com.yodawife.easyll.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class UserWordHistory {

    private static final int MAX_HISTORY = 10;

    private final Deque<String> history;

    public UserWordHistory() {
        this.history = new ArrayDeque<>();
    }

    public UserWordHistory(List<String> initialEntries) {
        this.history = new ArrayDeque<>(initialEntries);
    }

    public void append(String result) {
        if (!"S".equals(result) && !"F".equals(result)) {
            throw new IllegalArgumentException("Result must be 'S' or 'F', got: " + result);
        }
        if (history.size() >= MAX_HISTORY) {
            history.pollFirst(); // remove oldest
        }
        history.addLast(result);
    }

    public List<String> entries() {
        return List.copyOf(history);
    }

    public String encoded() {
        return String.join(",", history);
    }
}
