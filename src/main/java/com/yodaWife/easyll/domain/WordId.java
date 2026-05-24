package com.yodawife.easyll.domain;

/**
 * Strongly-typed identifier for a {@link Word}.
 *
 * @param value the trimmed, non-blank string identifier
 */
public record WordId(String value) {

    public WordId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WordId value must not be blank");
        }
        value = value.trim();
    }
}
