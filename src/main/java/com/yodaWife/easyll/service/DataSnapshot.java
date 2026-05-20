package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.WordDataBundle;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DataSnapshot(
        boolean healthy,
        List<String> errors,
    @Nullable WordDataBundle wordData,
    @Nullable ScoreDataBundle scoreData
) {
    public DataSnapshot {
        errors = List.copyOf(errors);
    }

    public static DataSnapshot healthy(WordDataBundle wordData, ScoreDataBundle scoreData) {
        return new DataSnapshot(true, List.of(), wordData, scoreData);
    }

    public static DataSnapshot degraded(List<String> errors) {
        return new DataSnapshot(false, errors, null, null);
    }
}
