package com.yodawife.easyll.domain;

import jakarta.validation.constraints.NotBlank;

public record WordEntry(@NotBlank String fromWord, @NotBlank String toWord, String example) {

    public WordEntry {
        if (fromWord == null || fromWord.isBlank()) {
            throw new IllegalArgumentException("fromWord must not be blank");
        }
        if (toWord == null || toWord.isBlank()) {
            throw new IllegalArgumentException("toWord must not be blank");
        }
        // example may be null or empty
        example = (example == null) ? "" : example.trim();
        fromWord = fromWord.trim();
        toWord = toWord.trim();
    }
}
