package com.yodawife.easyll.service;

import com.yodawife.easyll.config.MatchGameProperties;
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

    private final DataHealthService dataHealthService;
    private final MatchGameProperties matchGameProperties;
    private final Random random = new Random();

    public MatchBoardGenerator(DataHealthService dataHealthService,
                               MatchGameProperties matchGameProperties) {
        this.dataHealthService = dataHealthService;
        this.matchGameProperties = matchGameProperties;
    }

    public MatchBoard generate() {
        DataSnapshot snapshot = dataHealthService.snapshot();
        var wordData = snapshot.wordData();
        if (wordData == null) {
            return new MatchBoard(List.of(), List.of(), List.of(), java.util.Set.of());
        }
        List<WordEntry> allWords = new ArrayList<>(wordData.words());

        // Pick random entries (no duplicates within a set) using configured board size.
        Collections.shuffle(allWords, random);
        int boardSize = matchGameProperties.getBoardSize();
        List<WordEntry> picked = new ArrayList<>(allWords.subList(0, Math.min(boardSize, allWords.size())));

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
