package com.yodawife.easyll.domain;

public record LanguageMetadata(String fromLanguageName, String toLanguageName) {

    public LanguageMetadata {
        if (fromLanguageName == null || fromLanguageName.isBlank()) {
            throw new IllegalArgumentException("fromLanguageName must not be blank");
        }
        if (toLanguageName == null || toLanguageName.isBlank()) {
            throw new IllegalArgumentException("toLanguageName must not be blank");
        }
        fromLanguageName = fromLanguageName.trim();
        toLanguageName = toLanguageName.trim();
    }
}
