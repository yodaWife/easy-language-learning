package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.repository.ScoreReadRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PostgreSQL-backed implementation of {@link ScoreReadRepository}.
 * Active when the {@code db} Spring profile is enabled.
 */
@Repository
public class PostgresScoreReadRepository implements ScoreReadRepository {

    private final JdbcTemplate jdbc;

    public PostgresScoreReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, List<String>> getHistoriesForUser(String userId) {
        var result = new LinkedHashMap<String, List<String>>();
        jdbc.query(
            "SELECT pair_id, history_last12 FROM score_progress WHERE user_id = ?",
            rs -> {
                var pairId = rs.getString("pair_id");
                var encoded = rs.getString("history_last12");
                List<String> entries = encoded == null || encoded.isBlank()
                    ? List.of()
                    : Arrays.asList(encoded.split(","));
                result.put(pairId, entries);
            },
            userId);
        return Collections.unmodifiableMap(result);
    }

    @Override
    public Set<String> knownUsers() {
        var users = new LinkedHashSet<String>();
        jdbc.query("SELECT DISTINCT user_id FROM score_attempt", rs -> {
            users.add(rs.getString("user_id"));
        });
        return Collections.unmodifiableSet(users);
    }
}
