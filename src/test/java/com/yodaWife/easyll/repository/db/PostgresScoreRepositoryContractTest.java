package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.repository.ScoreReadRepository;
import com.yodawife.easyll.repository.ScoreWriteRepository;
import com.yodawife.easyll.repository.contract.ScoreReadRepositoryContractTest;
import com.yodawife.easyll.repository.contract.ScoreWriteRepositoryContractTest;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Objects;

@Testcontainers(disabledWithoutDocker = true)
class PostgresScoreRepositoryContractTest extends ScoreReadRepositoryContractTest implements ScoreWriteRepositoryContractTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Nullable
    static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void initDb() {
        var ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
    }

    @BeforeEach
    void setUp() {
        var jdbc = Objects.requireNonNull(jdbcTemplate);
        jdbc.execute("DELETE FROM score_progress");
        jdbc.execute("DELETE FROM score_attempt");
        jdbc.execute("DELETE FROM dictionary_pair");
        jdbc.execute("DELETE FROM app_user");
        repository = createRepository();
    }

    @Override
    protected ScoreReadRepository createRepository() {
        return new PostgresScoreReadRepository(Objects.requireNonNull(jdbcTemplate));
    }

    @Override
    public ScoreWriteRepository createWriteRepository() {
        return new PostgresScoreWriteRepository(Objects.requireNonNull(jdbcTemplate));
    }

    @Override
    protected void populateHistory(String userId, String pairId, String mode, List<String> entries) {
        var jdbc = Objects.requireNonNull(jdbcTemplate);
        insertUser(jdbc, userId);
        insertPair(jdbc, pairId);
        for (var entry : entries) {
            jdbc.update(
                "INSERT INTO score_attempt (user_id, pair_id, mode, result, attempted_at_utc) VALUES (?,?,?,?,now())",
                userId, pairId, mode, entry);
        }
        upsertScoreProgress(jdbc, userId, pairId, mode, entries);
    }

    @Override
    @Test
    public void appendAttempt_succeedsWithoutThrowing() {
        var jdbc = Objects.requireNonNull(jdbcTemplate);
        insertUser(jdbc, "user-1");
        insertPair(jdbc, "pair-1");
        ScoreWriteRepositoryContractTest.super.appendAttempt_succeedsWithoutThrowing();
    }

    private static void insertUser(JdbcTemplate jdbc, String userId) {
        jdbc.update(
            "INSERT INTO app_user (user_id, display_name, created_at_utc, active) VALUES (?,?,now(),true) ON CONFLICT DO NOTHING",
            userId, "display-" + userId);
    }

    private static void insertPair(JdbcTemplate jdbc, String pairId) {
        jdbc.update(
            """
            INSERT INTO dictionary_pair (pair_id, language_code, from_word, to_word, example, global_enabled, created_at_utc, updated_at_utc)
            VALUES (?,?,?,?,?,true,now(),now()) ON CONFLICT DO NOTHING
            """,
            pairId, "en", "from-" + pairId, "to-" + pairId, "example-" + pairId);
    }

    @Test
    @DisplayName("appendAttempt inserts score_attempt and upserts score_progress")
    void appendAttempt_persistsAttemptAndProgress() {
        var jdbc = Objects.requireNonNull(jdbcTemplate);
        insertUser(jdbc, "user-w");
        insertPair(jdbc, "pair-w");

        createWriteRepository().appendAttempt("user-w", "pair-w", "match", "S");

        var attemptCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM score_attempt WHERE user_id=? AND pair_id=? AND mode=?",
            Integer.class, "user-w", "pair-w", "match");
        assertThat(attemptCount).isEqualTo(1);

        var history = jdbc.queryForObject(
            "SELECT history_last12 FROM score_progress WHERE user_id=? AND pair_id=? AND mode=?",
            String.class, "user-w", "pair-w", "match");
        assertThat(history).isEqualTo("S");
    }

    private static void upsertScoreProgress(JdbcTemplate jdbc, String userId, String pairId, String mode, List<String> entries) {
        var historyLast12 = String.join(",", entries);
        var successCount = (short) entries.stream().filter("S"::equals).count();
        var totalCount = (short) entries.size();
        var successPercent = (short) Math.round(successCount * 100.0 / 12);
        jdbc.update(
            """
            INSERT INTO score_progress (user_id, pair_id, mode, history_last12, success_count, total_count, success_percent, updated_at_utc)
            VALUES (?,?,?,?,?,?,?,now())
            ON CONFLICT (user_id, pair_id, mode) DO UPDATE SET
              history_last12 = EXCLUDED.history_last12,
              success_count = EXCLUDED.success_count,
              total_count = EXCLUDED.total_count,
              success_percent = EXCLUDED.success_percent,
              updated_at_utc = EXCLUDED.updated_at_utc
            """,
            userId, pairId, mode, historyLast12, successCount, totalCount, successPercent);
    }
}
