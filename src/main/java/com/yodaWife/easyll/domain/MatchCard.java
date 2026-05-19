package com.yodawife.easyll.domain;

public record MatchCard(String word, String pairId, boolean draggable, String side) {

    public static String buildPairId(String fromWord, String toWord) {
        return fromWord + "|" + toWord;
    }
}
