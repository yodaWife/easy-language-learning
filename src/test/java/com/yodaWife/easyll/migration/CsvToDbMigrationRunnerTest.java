package com.yodawife.easyll.migration;

import com.yodawife.easyll.config.MigrationProperties;
import com.yodawife.easyll.domain.Account;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.ScoreDataBundle;
import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.repository.CsvAccountRepository;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DataSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvToDbMigrationRunnerTest {

    @TempDir
    @Nullable Path tempDir;

    private final DataHealthService dataHealthService = mock(DataHealthService.class);
    private final CsvAccountRepository accountRepository = mock(CsvAccountRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    // ── helpers ──────────────────────────────────────────────────────────────

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    private MigrationProperties makeProperties(boolean enabled, boolean dryRun) {
        var props = new MigrationProperties();
        props.setEnabled(enabled);
        props.setDryRun(dryRun);
        props.setErrorsOutputPath(tempDir().resolve("migration-errors.csv").toString());
        return props;
    }

    private static Account makeAccount(String userId, String name) {
        return new Account(userId, name, Instant.now());
    }

    private static Word makeWord(String id, String from, String to) {
        return new Word(new WordId(id), from, to, "", true);
    }

    private static LanguageBundle makeBundle(String langCode, List<Word> words) {
        return new LanguageBundle(langCode, null, words, List.of(), List.of());
    }

    private static MultiLanguageDataBundle makeMultiLang(Map<String, LanguageBundle> bundles) {
        var primaryLang = bundles.keySet().stream().findFirst().orElseThrow();
        return new MultiLanguageDataBundle(bundles, primaryLang);
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("dry-run mode logs all entities but never calls jdbc.update")
    void run_dryRun_doesNotExecuteAnyJdbcUpdates() throws Exception {
        var account = makeAccount("user-1", "Alice");
        var word = makeWord("pair-1", "Hello", "Hallo");
        var bundle = makeBundle("en", List.of(word));
        var multiLang = makeMultiLang(Map.of("en", bundle));
        var scoreKey = new ScoreKey("user-1", "pair-1", "match");
        var history = new UserWordHistory(List.of("S"));
        var scoreData = new ScoreDataBundle(Map.of(scoreKey, history));
        var snapshot = new DataSnapshot(true, true, List.of(), List.of(), null, scoreData, multiLang);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.findById("user-1")).thenReturn(Optional.of(account));

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, true));
        runner.run(args);

        verifyNoInteractions(jdbc);
    }

    @Test
    @DisplayName("live mode calls jdbc.update at least once per user and per word pair")
    void run_liveMode_executesJdbcUpdatesForUsersAndPairs() throws Exception {
        var account1 = makeAccount("user-1", "Alice");
        var account2 = makeAccount("user-2", "Bob");
        var word1 = makeWord("pair-1", "Hello", "Hallo");
        var word2 = makeWord("pair-2", "World", "Welt");
        var bundle = makeBundle("en", List.of(word1, word2));
        var multiLang = makeMultiLang(Map.of("en", bundle));
        // scoreData=null → migrateScores returns 0 immediately, no findById calls
        var snapshot = new DataSnapshot(true, false, List.of(), List.of(), null, null, multiLang);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of(account1, account2));

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, false));
        runner.run(args);

        // 2 user inserts + 2 pair inserts = 4 calls minimum
        verify(jdbc, atLeast(4)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("score entry is skipped and error file is written when pair ID is not in the dictionary")
    void run_skipsScoreEntry_whenPairIdNotFound() throws IOException, Exception {
        var account = makeAccount("user-1", "Alice");
        var bundle = makeBundle("en", List.of()); // 0 words → pairIds set will be empty
        var multiLang = makeMultiLang(Map.of("en", bundle));
        var scoreKey = new ScoreKey("user-1", "unknown-pair", "match");
        var history = new UserWordHistory(List.of("S"));
        var scoreData = new ScoreDataBundle(Map.of(scoreKey, history));
        var snapshot = new DataSnapshot(true, true, List.of(), List.of(), null, scoreData, multiLang);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.findById("user-1")).thenReturn(Optional.of(account));

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, false));
        runner.run(args);

        var errorFile = tempDir().resolve("migration-errors.csv");
        assertThat(errorFile).exists();
        var lines = Files.readAllLines(errorFile);
        assertThat(lines).hasSizeGreaterThan(1); // header + at least 1 error row
    }

    @Test
    @DisplayName("score entry is skipped and score upsert is not attempted when user ID is not found")
    void run_skipsScoreEntry_whenUserIdNotFound() throws Exception {
        var word = makeWord("pair-1", "Hello", "Hallo");
        var bundle = makeBundle("en", List.of(word));
        var multiLang = makeMultiLang(Map.of("en", bundle));
        var scoreKey = new ScoreKey("ghost-user", "pair-1", "match");
        var history = new UserWordHistory(List.of("S"));
        var scoreData = new ScoreDataBundle(Map.of(scoreKey, history));
        var snapshot = new DataSnapshot(true, true, List.of(), List.of(), null, scoreData, multiLang);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of());
        when(accountRepository.findById("ghost-user")).thenReturn(Optional.empty());

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, false));
        runner.run(args);

        // Only 1 call expected: the pair insert (0 users → 0 user inserts, score skipped)
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("null score data is handled gracefully and users and pairs are still migrated")
    void run_handlesNullScoreData_gracefully() {
        var account = makeAccount("user-1", "Alice");
        var word = makeWord("pair-1", "Hello", "Hallo");
        var bundle = makeBundle("en", List.of(word));
        var multiLang = makeMultiLang(Map.of("en", bundle));
        var snapshot = new DataSnapshot(true, false, List.of(), List.of(), null, null, multiLang);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of(account));

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, false));

        assertThatCode(() -> runner.run(args)).doesNotThrowAnyException();
        // 1 user insert + 1 pair insert = 2 calls; no score upserts
        verify(jdbc, atLeast(2)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("null multi-language data is handled gracefully and users are still migrated")
    void run_handlesNullMultiLanguageData_gracefully() {
        var account = makeAccount("user-1", "Alice");
        var scoreData = ScoreDataBundle.empty();
        var snapshot = new DataSnapshot(false, true, List.of(), List.of(), null, scoreData, null);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of(account));

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, false));

        assertThatCode(() -> runner.run(args)).doesNotThrowAnyException();
        // 1 user insert only; no pair inserts (multiLanguageData is null), no score upserts
        verify(jdbc, times(1)).update(anyString(), any(Object[].class));
    }

    @Test
    @DisplayName("live mode inserts one score_attempt row per history entry before upserting score_progress")
    void run_liveMode_insertsScoreAttemptRowsPerHistoryEntry() throws Exception {
        var account = makeAccount("user-1", "Alice");
        var word = makeWord("pair-1", "Hello", "Hallo");
        var bundle = makeBundle("en", List.of(word));
        var multiLang = makeMultiLang(Map.of("en", bundle));
        var scoreKey = new ScoreKey("user-1", "pair-1", "match");
        var history = new UserWordHistory(List.of("S", "F", "S"));
        var scoreData = new ScoreDataBundle(Map.of(scoreKey, history));
        var snapshot = new DataSnapshot(true, true, List.of(), List.of(), null, scoreData, multiLang);

        when(dataHealthService.snapshot()).thenReturn(snapshot);
        when(accountRepository.findAll()).thenReturn(List.of(account));
        when(accountRepository.findById("user-1")).thenReturn(Optional.of(account));

        var runner = new CsvToDbMigrationRunner(dataHealthService, accountRepository, jdbc, makeProperties(true, false));
        runner.run(args);

        // 1 user insert + 1 pair insert + 3 score_attempt inserts + 1 score_progress upsert = 6 total
        verify(jdbc, times(6)).update(anyString(), any(Object[].class));
    }
}
