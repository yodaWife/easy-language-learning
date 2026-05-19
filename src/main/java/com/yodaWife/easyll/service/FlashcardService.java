package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.WordEntry;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

@Service
public class FlashcardService {

    private final DataHealthService dataHealthService;
    private final RandomGenerator random = RandomGenerator.getDefault();

    public FlashcardService(DataHealthService dataHealthService) {
        this.dataHealthService = dataHealthService;
    }

    public Optional<WordEntry> randomCard() {
        DataSnapshot snapshot = dataHealthService.snapshot();
        if (!snapshot.healthy() || snapshot.wordData() == null) {
            return Optional.empty();
        }
        List<WordEntry> words = snapshot.wordData().words();
        if (words.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(words.get(random.nextInt(words.size())));
    }
}
