package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.Account;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class CsvAccountRepositoryTest {

    @TempDir
    @Nullable Path tempDir;

    private Path accountsFile;
    private CsvAccountRepository repo;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    @BeforeEach
    void setUp() {
        accountsFile = tempDir().resolve("users.csv");
        repo = new CsvAccountRepository(accountsFile.toString());
        repo.loadFromCsv();
    }

    private static Account account(String id, String name) {
        return new Account(id, name, Instant.EPOCH);
    }

    // ── empty / missing file ─────────────────────────────────────────────────

    @Test
    @DisplayName("findAll on missing CSV returns empty list")
    void missingCsv_findAll_returnsEmpty() {
        assertThat(repo.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Missing CSV file does not throw on loadFromCsv")
    void missingCsv_loadFromCsv_noException() {
        var missingPath = tempDir().resolve("nonexistent.csv").toString();
        var freshRepo = new CsvAccountRepository(missingPath);
        freshRepo.loadFromCsv(); // must not throw
        assertThat(freshRepo.findAll()).isEmpty();
    }

    // ── save / findById ──────────────────────────────────────────────────────

    @Test
    @DisplayName("save persists account and findById retrieves it")
    void save_thenFindById_returnsAccount() {
        var acc = account("uid-1", "Ewa");
        repo.save(acc);

        assertThat(repo.findById("uid-1")).contains(acc);
    }

    @Test
    @DisplayName("findById with unknown id returns empty")
    void findById_unknownId_returnsEmpty() {
        assertThat(repo.findById("no-such-id")).isEmpty();
    }

    // ── findByDisplayName ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findByDisplayName is case-insensitive")
    void findByDisplayName_caseInsensitive() {
        repo.save(account("uid-1", "Ewa"));

        assertThat(repo.findByDisplayName("EWA")).isPresent();
        assertThat(repo.findByDisplayName("ewa")).isPresent();
        assertThat(repo.findByDisplayName("Ewa")).isPresent();
    }

    @Test
    @DisplayName("findByDisplayName returns empty for unknown name")
    void findByDisplayName_unknown_returnsEmpty() {
        assertThat(repo.findByDisplayName("Ghost")).isEmpty();
    }

    // ── persistence ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("save writes data to CSV; reloading reads it back")
    void save_writesCsv_reloadReadsBack() throws IOException {
        repo.save(account("uid-1", "Ewa"));
        repo.save(account("uid-2", "Ala"));

        // Reload from disk
        var reloaded = new CsvAccountRepository(accountsFile.toString());
        reloaded.loadFromCsv();

        assertThat(reloaded.findById("uid-1")).isPresent();
        assertThat(reloaded.findById("uid-2")).isPresent();
    }

    @Test
    @DisplayName("Malformed CSV rows are skipped without throwing")
    void malformedCsvRow_isSkipped() throws IOException {
        Files.writeString(accountsFile, "uid-1;Ewa;2026-01-01T00:00:00Z\nBAD_ROW_ONLY_ONE_COLUMN\n",
                StandardCharsets.UTF_8);
        var freshRepo = new CsvAccountRepository(accountsFile.toString());
        freshRepo.loadFromCsv();

        assertThat(freshRepo.findById("uid-1")).isPresent();
        assertThat(freshRepo.findAll()).hasSize(1);
    }

    // ── findAll ordering ──────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns accounts sorted alphabetically by displayName")
    void findAll_sortedByDisplayName() {
        repo.save(account("uid-1", "Ewa"));
        repo.save(account("uid-2", "Ala"));
        repo.save(account("uid-3", "Zosia"));

        var names = repo.findAll().stream().map(Account::displayName).toList();
        assertThat(names).containsExactly("Ala", "Ewa", "Zosia");
    }

    // ── sanitisation ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Semicolons in displayName are stripped to prevent CSV injection")
    void displayName_withSemicolon_isSanitised() throws IOException {
        repo.save(account("uid-1", "Ewa;Kowalska"));

        // File must not have an extra field; verify by reloading
        var reloaded = new CsvAccountRepository(accountsFile.toString());
        reloaded.loadFromCsv();

        var acc = reloaded.findById("uid-1");
        assertThat(acc).isPresent();
        assertThat(acc.get().displayName()).doesNotContain(";");
    }
}
