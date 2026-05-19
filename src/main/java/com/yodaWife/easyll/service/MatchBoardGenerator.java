package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchCard;
import com.yodawife.easyll.domain.WordEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class MatchBoardGenerator {

    static final int BOARD_SIZE = 5;

    private final DataHealthService dataHealthService;
    private final Random random = new Random();

    public MatchBoardGenerator(DataHealthService dataHealthService) {
        this.dataHealthService = dataHealthService;
    }

    public MatchBoard generate() {
        DataSnapshot snapshot = dataHealthService.snapshot();
        List<WordEntry> allWords = new ArrayList<>(snapshot.wordData().words());

        // Pick 3 distinct random entries (no duplicates within a set)
        Collections.shuffle(allWords, random);
        List<WordEntry> picked = new ArrayList<>(allWords.subList(0, Math.min(BOARD_SIZE, allWords.size())));

        // Build left (draggable FROM) and right (drop-slot TO) columns, then shuffle each independently
        List<MatchCard> leftColumn = new ArrayList<>();
        List<MatchCard> rightColumn = new ArrayList<>();
        for (WordEntry entry : picked) {
            String pairId = MatchCard.buildPairId(entry.fromWord(), entry.toWord());
            leftColumn.add(new MatchCard(entry.fromWord(), pairId, true, "from"));
            rightColumn.add(new MatchCard(entry.toWord(), pairId, false, "to"));
        }
        Collections.shuffle(leftColumn, random);
        Collections.shuffle(rightColumn, random);

        return new MatchBoard(leftColumn, rightColumn, picked, java.util.Set.of());
    }

}
