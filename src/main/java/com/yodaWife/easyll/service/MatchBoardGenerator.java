package com.yodawife.easyll.service;

import com.yodawife.easyll.config.MatchGameProperties;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchCard;
import com.yodawife.easyll.domain.WordEntry;
import com.yodawife.easyll.repository.DictionaryRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

@Service
public class MatchBoardGenerator {

    private final DictionaryRepository dictionaryRepository;
    private final MatchGameProperties matchGameProperties;
    private final EligibilityEvaluator eligibilityEvaluator;
    private final Random random = new Random();

    public MatchBoardGenerator(DictionaryRepository dictionaryRepository,
                               MatchGameProperties matchGameProperties,
                               EligibilityEvaluator eligibilityEvaluator) {
        this.dictionaryRepository = dictionaryRepository;
        this.matchGameProperties = matchGameProperties;
        this.eligibilityEvaluator = eligibilityEvaluator;
    }

    public MatchBoard generate() {
        var languages = dictionaryRepository.availableLanguages();
        if (languages.isEmpty()) {
            return new MatchBoard(List.of(), List.of(), List.of(), java.util.Set.of());
        }
        return generate(languages.get(0), "match");
    }

    /**
     * Generates a match board using eligible words for the given language and game mode.
     * Falls back to {@link #generate()} when no bundle is found for the language code.
     * Returns an empty board when eligible words are fewer than the configured board size.
     *
     * @param languageCode the language code to look up in the multi-language data bundle
     * @param mode         the game mode used for eligibility filtering (e.g. "match")
     * @return a new {@link MatchBoard}, or an empty board when eligible words are insufficient
     */
    public MatchBoard generate(String languageCode, String mode) {
        var bundleOpt = dictionaryRepository.findLanguage(languageCode);
        if (bundleOpt.isEmpty()) {
            return new MatchBoard(List.of(), List.of(), List.of(), java.util.Set.of());
        }
        var bundle = bundleOpt.get();
        var eligible = eligibilityEvaluator.filterEligible(bundle.words(), mode, bundle.modeEligibilities());
        int boardSize = matchGameProperties.getBoardSize();
        if (eligible.size() < boardSize) {
            return new MatchBoard(List.of(), List.of(), List.of(), java.util.Set.of());
        }
        List<WordEntry> wordEntries = new ArrayList<>(eligible.stream()
                .map(w -> new WordEntry(w.fromWord(), w.toWord(), w.example()))
                .toList());
        Collections.shuffle(wordEntries, random);
        var picked = new ArrayList<>(wordEntries.subList(0, boardSize));

        List<MatchCard> leftColumn = new ArrayList<>();
        List<MatchCard> rightColumn = new ArrayList<>();
        for (var entry : picked) {
            var pairId = MatchCard.buildPairId(entry.fromWord(), entry.toWord());
            leftColumn.add(new MatchCard(entry.fromWord(), pairId, true, "from"));
            rightColumn.add(new MatchCard(entry.toWord(), pairId, false, "to"));
        }
        Collections.shuffle(leftColumn, random);
        Collections.shuffle(rightColumn, random);

        return new MatchBoard(leftColumn, rightColumn, picked, java.util.Set.of());
    }

}
