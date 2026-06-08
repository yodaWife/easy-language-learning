package com.yodawife.easyll.migration;

import com.yodawife.easyll.config.MigrationProperties;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ScoreKey;
import com.yodawife.easyll.domain.UserWordHistory;
import com.yodawife.easyll.repository.CsvAccountRepository;
import com.yodawife.easyll.service.DataHealthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.migration.enabled", havingValue = "true")
public class CsvToDbMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvToDbMigrationRunner.class);

    private static final String INSERT_USER_SQL =
            "INSERT INTO app_user (user_id, display_name, created_at_utc, active) VALUES (?,?,?,true) ON CONFLICT (user_id) DO NOTHING";

    private static final String INSERT_PAIR_SQL =
            "INSERT INTO dictionary_pair (pair_id, language_code, from_word, to_word, example, global_enabled, " +
            "created_at_utc, updated_at_utc) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (pair_id) DO NOTHING";

    private static final String UPSERT_SCORE_SQL = """
            INSERT INTO score_progress (user_id, pair_id, mode, history_last12, success_count, total_count, success_percent, updated_at_utc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, pair_id, mode) DO UPDATE SET
              history_last12 = EXCLUDED.history_last12,
              success_count = EXCLUDED.success_count,
              total_count = EXCLUDED.total_count,
              success_percent = EXCLUDED.success_percent,
              updated_at_utc = EXCLUDED.updated_at_utc
            """;

    private static final String INSERT_ATTEMPT_SQL =
            "INSERT INTO score_attempt (user_id, pair_id, mode, result, attempted_at_utc) VALUES (?, ?, ?, ?, ?)";

    private final DataHealthService dataHealthService;
    private final CsvAccountRepository accountRepository;
    private final JdbcTemplate jdbc;
    private final MigrationProperties migrationProperties;

    public CsvToDbMigrationRunner(DataHealthService dataHealthService,
                                   CsvAccountRepository accountRepository,
                                   JdbcTemplate jdbc,
                                   MigrationProperties migrationProperties) {
        this.dataHealthService = dataHealthService;
        this.accountRepository = accountRepository;
        this.jdbc = jdbc;
        this.migrationProperties = migrationProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try (var errorRecorder = new MigrationErrorRecorder(migrationProperties.getErrorsOutputPath())) {
            var usersCount = migrateUsers();
            var pairIds = new HashSet<String>();
            var pairsCount = migratePairs(pairIds);
            var scoresCount = migrateScores(pairIds, errorRecorder);
            log.info("Migration complete: {} users, {} pairs, {} score entries processed. {} errors. Dry-run: {}",
                    usersCount, pairsCount, scoresCount, errorRecorder.errorCount(), migrationProperties.isDryRun());
        }
    }

    private int migrateUsers() {
        var accounts = accountRepository.findAll();
        if (migrationProperties.isDryRun()) {
            accounts.forEach(a -> log.info("[DRY-RUN] Would insert user: {} ({})", a.userId(), a.displayName()));
        } else {
            accounts.forEach(account ->
                    jdbc.update(INSERT_USER_SQL, account.userId(), account.displayName(), Timestamp.from(account.createdAt())));
        }
        return accounts.size();
    }

    private int migratePairs(Set<String> pairIds) {
        var multiLanguageData = dataHealthService.snapshot().multiLanguageData();
        if (multiLanguageData == null) {
            log.warn("No multi-language data available for migration.");
            return 0;
        }
        int count = 0;
        for (var bundle : multiLanguageData.bundles().values()) {
            count += migrateBundle(bundle, pairIds);
        }
        return count;
    }

    private int migrateBundle(LanguageBundle bundle, Set<String> pairIds) {
        int count = 0;
        var now = Timestamp.from(Instant.now());
        for (var word : bundle.words()) {
            var pairId = word.wordId().value();
            pairIds.add(pairId);
            if (migrationProperties.isDryRun()) {
                log.info("[DRY-RUN] Would insert pair: {} ({} → {})", pairId, word.fromWord(), word.toWord());
            } else {
                jdbc.update(INSERT_PAIR_SQL, pairId, bundle.languageCode(), word.fromWord(), word.toWord(),
                        word.example(), word.globalEnabled(), now, now);
            }
            count++;
        }
        return count;
    }

    private int migrateScores(Set<String> knownPairIds, MigrationErrorRecorder errorRecorder) {
        var scoreData = dataHealthService.snapshot().scoreData();
        if (scoreData == null) {
            log.warn("No score data available for migration, skipping score migration.");
            return 0;
        }
        int count = 0;
        for (var entry : scoreData.histories().entrySet()) {
            if (processScoreEntry(entry.getKey(), entry.getValue(), knownPairIds, errorRecorder)) {
                count++;
            }
        }
        return count;
    }

    private boolean processScoreEntry(ScoreKey key, UserWordHistory history,
                                       Set<String> knownPairIds, MigrationErrorRecorder errorRecorder) {
        if (accountRepository.findById(key.userId()).isEmpty()) {
            errorRecorder.record("userId:" + key.userId(), "scores.csv", "userId not found in users.csv");
            return false;
        }
        if (!knownPairIds.contains(key.pairId())) {
            errorRecorder.record("pairId:" + key.pairId(), "scores.csv", "pairId not found in dictionary");
            return false;
        }
        var entries = history.entries();
        var last12 = entries.subList(Math.max(0, entries.size() - 12), entries.size());
        insertAttemptRows(key.userId(), key.pairId(), key.mode(), last12);
        var historyLast12 = String.join(",", last12);
        var totalCount = last12.size();
        var successCount = (int) last12.stream().filter("S"::equals).count();
        var successPercent = (int) Math.round(successCount * 100.0 / 12);
        if (migrationProperties.isDryRun()) {
            log.info("[DRY-RUN] Would upsert score_progress for userId={} pairId={} mode={}",
                    key.userId(), key.pairId(), key.mode());
        } else {
            jdbc.update(UPSERT_SCORE_SQL, key.userId(), key.pairId(), key.mode(),
                    historyLast12, successCount, totalCount, successPercent, Timestamp.from(Instant.now()));
        }
        return true;
    }

    private void insertAttemptRows(String userId, String pairId, String mode, List<String> last12) {
        var baseTime = Instant.now().minusSeconds(last12.size() * 60L);
        for (int i = 0; i < last12.size(); i++) {
            var result = last12.get(i);
            if (migrationProperties.isDryRun()) {
                log.info("[DRY-RUN] Would insert score_attempt for userId={} pairId={} mode={} result={}",
                        userId, pairId, mode, result);
            } else {
                var timestamp = baseTime.plusSeconds(i * 60L);
                jdbc.update(INSERT_ATTEMPT_SQL, userId, pairId, mode, result, Timestamp.from(timestamp));
            }
        }
    }
}
