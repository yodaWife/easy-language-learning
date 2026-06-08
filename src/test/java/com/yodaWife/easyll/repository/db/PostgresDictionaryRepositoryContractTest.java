package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.repository.DictionaryRepository;
import com.yodawife.easyll.repository.contract.DictionaryRepositoryContractTest;
import org.flywaydb.core.Flyway;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Objects;

@Testcontainers(disabledWithoutDocker = true)
class PostgresDictionaryRepositoryContractTest extends DictionaryRepositoryContractTest {

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
        jdbc.update("""
                INSERT INTO dictionary_pair
                    (pair_id, language_code, from_word, to_word, example, global_enabled, created_at_utc, updated_at_utc)
                VALUES (?, ?, ?, ?, ?, ?, now(), now())
                """,
                "test-pair-1", "hun", "Hello", "Szia", "", true);
        repository = createRepository();
    }

    @Override
    protected DictionaryRepository createRepository() {
        return new PostgresDictionaryRepository(Objects.requireNonNull(jdbcTemplate));
    }

    @Override
    protected String getTestLanguageCode() {
        return "hun";
    }
}
