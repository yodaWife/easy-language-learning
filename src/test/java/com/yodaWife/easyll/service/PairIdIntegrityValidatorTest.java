package com.yodawife.easyll.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.repository.DictionaryRepository;
import com.yodawife.easyll.repository.ScoreReadRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PairIdIntegrityValidatorTest {

    private final DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
    private final ScoreReadRepository scoreReadRepository = mock(ScoreReadRepository.class);
    private final PairIdIntegrityValidator validator = new PairIdIntegrityValidator(dictionaryRepository, scoreReadRepository);

    private Logger logger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(PairIdIntegrityValidator.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    @DisplayName("All pairIds in scores match dictionary entries — no WARN logged")
    void validate_allPairIdsMatch_logsInfo() {
        var word1 = new Word(new WordId("pair-1"), "Hello", "Helló", "", true);
        var word2 = new Word(new WordId("pair-2"), "World", "Világ", "", true);
        var bundle = new LanguageBundle("hu", null, List.of(word1, word2), List.of(), List.of());
        when(dictionaryRepository.availableLanguages()).thenReturn(List.of("hu"));
        when(dictionaryRepository.findLanguage("hu")).thenReturn(Optional.of(bundle));
        when(scoreReadRepository.knownUsers()).thenReturn(Set.of("user-1"));
        when(scoreReadRepository.getHistoriesForUser("user-1")).thenReturn(Map.of("pair-1", List.of(), "pair-2", List.of()));

        validator.validate();

        var warnLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).isEmpty();
    }

    @Test
    @DisplayName("Orphaned pairId in scores not found in dictionary — logs WARN with pairId")
    void validate_orphanedPairId_logsWarning() {
        var word = new Word(new WordId("pair-known"), "Cat", "Macska", "", true);
        var bundle = new LanguageBundle("hu", null, List.of(word), List.of(), List.of());
        when(dictionaryRepository.availableLanguages()).thenReturn(List.of("hu"));
        when(dictionaryRepository.findLanguage("hu")).thenReturn(Optional.of(bundle));
        when(scoreReadRepository.knownUsers()).thenReturn(Set.of("user-1"));
        when(scoreReadRepository.getHistoriesForUser("user-1")).thenReturn(Map.of("pair-orphan", List.of()));

        validator.validate();

        var warnLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).isNotEmpty();
        assertThat(warnLogs.get(0).getFormattedMessage()).contains("pair-orphan");
    }

    @Test
    @DisplayName("No languages in dictionary — integrity check skipped with WARN")
    void validate_noLanguages_skipsWithWarn() {
        when(dictionaryRepository.availableLanguages()).thenReturn(List.of());

        validator.validate();

        var warnLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).hasSize(1);
        assertThat(warnLogs.get(0).getFormattedMessage()).contains("PairId integrity check skipped");
    }

    @Test
    @DisplayName("No score entries found — logs INFO that nothing to validate")
    void validate_noScoreEntries_logsInfoNothingToValidate() {
        var word = new Word(new WordId("pair-1"), "Hello", "Helló", "", true);
        var bundle = new LanguageBundle("hu", null, List.of(word), List.of(), List.of());
        when(dictionaryRepository.availableLanguages()).thenReturn(List.of("hu"));
        when(dictionaryRepository.findLanguage("hu")).thenReturn(Optional.of(bundle));
        when(scoreReadRepository.knownUsers()).thenReturn(Set.of());

        validator.validate();

        var infoLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .toList();
        assertThat(infoLogs).hasSize(1);
        assertThat(infoLogs.get(0).getFormattedMessage()).contains("no score entries found");
    }
}
