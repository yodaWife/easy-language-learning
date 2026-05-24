package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.WordDataBundle;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Immutable snapshot of the data loading state, tracking word and score health independently.
 * Word health controls gameplay availability; score health controls score persistence.
 */
public record DataSnapshot(
        boolean wordsHealthy,
        boolean scoresHealthy,
        List<String> wordErrors,
        List<String> scoreErrors,
        @Nullable WordDataBundle wordData,
        @Nullable ScoreDataBundle scoreData,
        @Nullable MultiLanguageDataBundle multiLanguageData
) {
    public DataSnapshot {
        wordErrors = List.copyOf(wordErrors);
        scoreErrors = List.copyOf(scoreErrors);
    }

    /** @return {@code true} when both word and score data are healthy. */
    public boolean healthy() {
        return wordsHealthy && scoresHealthy;
    }

    public static DataSnapshot healthy(WordDataBundle wordData, ScoreDataBundle scoreData) {
        return new DataSnapshot(true, true, List.of(), List.of(), wordData, scoreData, null);
    }

    public static DataSnapshot degraded(List<String> errors) {
        return new DataSnapshot(false, false, errors, List.of(), null, null, null);
    }

    public Optional<LanguageBundle> getLanguageBundle(String languageCode) {
        return multiLanguageData == null ? Optional.empty() : multiLanguageData.getBundle(languageCode);
    }
}
