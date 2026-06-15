package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.domain.Account;
import com.yodawife.easyll.repository.AccountRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed implementation of {@link AccountRepository}.
 * Active when the {@code "db"} Spring profile is enabled.
 */
@Primary
@Repository
public class PostgresAccountRepository implements AccountRepository {

    private static final RowMapper<Account> ACCOUNT_ROW_MAPPER = (rs, rowNum) ->
        new Account(
            rs.getString("user_id"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at_utc").toInstant());

    private final JdbcTemplate jdbc;

    public PostgresAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Account> findById(String userId) {
        var results = jdbc.query(
            "SELECT user_id, display_name, created_at_utc FROM app_user WHERE user_id = ?",
            ACCOUNT_ROW_MAPPER, userId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<Account> findByDisplayName(String displayName) {
        var results = jdbc.query(
            "SELECT user_id, display_name, created_at_utc FROM app_user WHERE lower(display_name) = lower(?)",
            ACCOUNT_ROW_MAPPER, displayName);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<Account> findAll() {
        return jdbc.query(
            "SELECT user_id, display_name, created_at_utc FROM app_user ORDER BY lower(display_name) ASC",
            ACCOUNT_ROW_MAPPER);
    }

    @Override
    public Account save(Account account) {
        jdbc.update("""
            INSERT INTO app_user (user_id, display_name, created_at_utc, active)
            VALUES (?, ?, ?, true)
            ON CONFLICT (user_id) DO UPDATE
              SET display_name = EXCLUDED.display_name
            """,
            account.userId(), account.displayName(),
            Timestamp.from(account.createdAt()));
        return account;
    }
}
