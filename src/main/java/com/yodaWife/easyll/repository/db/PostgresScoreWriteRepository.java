package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.config.PersistenceProfiles;
import com.yodawife.easyll.repository.ScoreWriteRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;

/**
 * PostgreSQL-backed implementation of {@link ScoreWriteRepository}.
 * Active when the {@value PersistenceProfiles#DB} Spring profile is enabled.
 */
@Repository
@Profile(PersistenceProfiles.DB)
public class PostgresScoreWriteRepository implements ScoreWriteRepository {

    private static final int MAX_HISTORY = 12;

    private final JdbcTemplate jdbc;

    public PostgresScoreWriteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void appendAttempt(String userId, String pairId, String mode, String result) {
        jdbc.update(
            """
            INSERT INTO score_attempt (user_id, pair_id, mode, result, attempted_at_utc)
            VALUES (?, ?, ?, ?, ?)
            """,
            userId, pairId, mode, result, Timestamp.from(Instant.now()));

        var recentResults = jdbc.query(
            """
            SELECT result FROM score_attempt
            WHERE user_id = ? AND pair_id = ? AND mode = ?
            ORDER BY attempted_at_utc DESC
            LIMIT 12
            """,
            (rs, rowNum) -> {
                var r = rs.getString("result");
                return r != null ? r : "";
            },
            userId, pairId, mode);

        var history = new ArrayList<>(recentResults);
        Collections.reverse(history);

        var historyLast12 = String.join(",", history);
        var totalCount = history.size();
        var successCount = (int) history.stream().filter("S"::equals).count();
        var successPercent = (int) Math.round(successCount * 100.0 / MAX_HISTORY);

        jdbc.update(
            """
            INSERT INTO score_progress (user_id, pair_id, mode, history_last12, success_count, total_count, success_percent, updated_at_utc)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, pair_id, mode) DO UPDATE SET
              history_last12 = EXCLUDED.history_last12,
              success_count = EXCLUDED.success_count,
              total_count = EXCLUDED.total_count,
              success_percent = EXCLUDED.success_percent,
              updated_at_utc = EXCLUDED.updated_at_utc
            """,
            userId, pairId, mode, historyLast12, successCount, totalCount, successPercent, Timestamp.from(Instant.now()));
    }

    @Override
    public void flush() {
        // No-op: DB persistence is transactional; no batch flush required.
    }
}
