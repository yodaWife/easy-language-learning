package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.repository.AccountRepository;
import com.yodawife.easyll.repository.contract.AccountRepositoryContractTest;
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
class PostgresAccountRepositoryContractTest extends AccountRepositoryContractTest {

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

    @Override
    @BeforeEach
    protected void setUpRepository() {
        var jdbc = Objects.requireNonNull(jdbcTemplate);
        jdbc.execute("DELETE FROM score_progress");
        jdbc.execute("DELETE FROM score_attempt");
        jdbc.execute("DELETE FROM dictionary_pair");
        jdbc.execute("DELETE FROM app_user");
        super.setUpRepository();
    }

    @Override
    protected AccountRepository createRepository() {
        return new PostgresAccountRepository(Objects.requireNonNull(jdbcTemplate));
    }
}
