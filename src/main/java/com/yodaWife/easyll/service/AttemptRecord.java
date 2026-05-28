package com.yodawife.easyll.service;

/**
 * Immutable record of a single match attempt.
 *
 * @param fromWord the dragged (source) word
 * @param toWord   the target word
 * @param languageCode the language code for this attempt
 * @param correct  {@code true} if the pair matched
 */
public record AttemptRecord(String fromWord, String toWord, String languageCode, boolean correct) {}
