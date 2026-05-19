package com.yodawife.easyll.domain;

import java.util.List;

public record WordDataBundle(LanguageMetadata metadata, List<WordEntry> words) {
    public WordDataBundle {
        words = List.copyOf(words);
    }
}
