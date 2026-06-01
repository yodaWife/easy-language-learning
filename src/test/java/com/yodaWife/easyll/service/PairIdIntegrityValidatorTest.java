package com.yodawife.easyll.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PairIdIntegrityValidatorTest {

    private final DataHealthService dataHealthService = mock(DataHealthService.class);
    private final PairIdIntegrityValidator validator = new PairIdIntegrityValidator(dataHealthService);

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
        var multiLanguageData = new MultiLanguageDataBundle(Map.of("hu", bundle), "hu");
        var scoreData = new ScoreDataBundle(Map.of(
                new ScoreKey("user-1", "pair-1", "match"), new UserWordHistory(),
                new ScoreKey("user-1", "pair-2", "match"), new UserWordHistory()
        ));
        var snapshot = new DataSnapshot(true, true, List.of(), List.of(), null, scoreData, multiLanguageData);
        when(dataHealthService.snapshot()).thenReturn(snapshot);

        validator.validate();

        List<ILoggingEvent> warnLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).isEmpty();
    }

    @Test
    @DisplayName("Orphaned pairId in scores not found in dictionary — logs WARN with pairId")
    void validate_orphanedPairId_logsWarning() {
        var word = new Word(new WordId("pair-known"), "Cat", "Macska", "", true);
        var bundle = new LanguageBundle("hu", null, List.of(word), List.of(), List.of());
        var multiLanguageData = new MultiLanguageDataBundle(Map.of("hu", bundle), "hu");
        var scoreData = new ScoreDataBundle(Map.of(
                new ScoreKey("user-1", "pair-orphan", "match"), new UserWordHistory()
        ));
        var snapshot = new DataSnapshot(true, true, List.of(), List.of(), null, scoreData, multiLanguageData);
        when(dataHealthService.snapshot()).thenReturn(snapshot);

        validator.validate();

        List<ILoggingEvent> warnLogs = listAppender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertThat(warnLogs).isNotEmpty();
        assertThat(warnLogs.get(0).getFormattedMessage()).contains("pair-orphan");
    }

    @Test
    @DisplayName("Null score data in snapshot — validator skips gracefully")
    void validate_nullScoreData_skipsGracefully() {
        var snapshot = new DataSnapshot(false, false, List.of(), List.of(), null, null, null);
        when(dataHealthService.snapshot()).thenReturn(snapshot);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Null multi-language data in snapshot — validator skips gracefully")
    void validate_nullMultiLanguageData_skipsGracefully() {
        var scoreData = new ScoreDataBundle(Map.of(
                new ScoreKey("user-1", "pair-1", "match"), new UserWordHistory()
        ));
        var snapshot = new DataSnapshot(false, false, List.of(), List.of(), null, scoreData, null);
        when(dataHealthService.snapshot()).thenReturn(snapshot);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
