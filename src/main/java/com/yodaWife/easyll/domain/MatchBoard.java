package com.yodawife.easyll.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represents one round of the match game.
 * leftColumn: shuffled draggable FROM cards
 * rightColumn: shuffled drop-slot TO cards
 * pairs: the 3 WordEntry pairs on this board (used for server-side validation)
 * matchedPairIds: pairIds that have been correctly matched this round
 */
public record MatchBoard(List<MatchCard> leftColumn, List<MatchCard> rightColumn, List<WordEntry> pairs, Set<String> matchedPairIds) {
    public MatchBoard {
        leftColumn = List.copyOf(leftColumn);
        rightColumn = List.copyOf(rightColumn);
        pairs = List.copyOf(pairs);
        matchedPairIds = Set.copyOf(matchedPairIds);
    }

    public MatchBoard withMatched(String pairId) {
        Set<String> updated = new HashSet<>(matchedPairIds);
        updated.add(pairId);
        return new MatchBoard(leftColumn, rightColumn, pairs, updated);
    }

    public boolean allMatched() {
        return matchedPairIds.size() >= pairs.size();
    }
}
