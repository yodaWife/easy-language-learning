package com.yodawife.easyll.repository;

import com.yodawife.easyll.domain.Account;
import jakarta.annotation.PostConstruct;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * CSV-backed implementation of {@link AccountRepository}.
 *
 * <p>Accounts are loaded into memory on startup from a semicolon-delimited CSV file.
 * All mutating operations atomically rewrite the file using a temp-file rename pattern.
 */
@Profile({"csv", "db"})
@Repository
public class CsvAccountRepository implements AccountRepository {

    private static final Logger log = LoggerFactory.getLogger(CsvAccountRepository.class);

    private static final int EXPECTED_COLUMNS = 3;
    private static final int COL_USER_ID = 0;
    private static final int COL_DISPLAY_NAME = 1;
    private static final int COL_CREATED_AT = 2;

    private final Path accountsPath;

    /** In-memory store: userId → Account. */
    private Map<String, Account> accounts = new HashMap<>();

    public CsvAccountRepository(@Value("${app.accounts.file-path}") String accountsFilePath) {
        this.accountsPath = Path.of(accountsFilePath);
    }

    @PostConstruct
    synchronized void loadFromCsv() {
        if (!Files.exists(accountsPath)) {
            log.debug("Accounts CSV file not found at {}; starting with empty account store.", accountsPath);
            return;
        }

        var loaded = new HashMap<String, Account>();
        try (Reader reader = Files.newBufferedReader(accountsPath, StandardCharsets.UTF_8);
             CSVParser csvParser = CSVFormat.DEFAULT
                     .builder()
                     .setDelimiter(';')
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            var records = csvParser.getRecords();
            for (int i = 0; i < records.size(); i++) {
                var record = records.get(i);
                int rowNumber = i + 1;
                var account = parseRecord(record, rowNumber);
                if (account != null) {
                    loaded.put(account.userId(), account);
                }
            }

        } catch (IOException e) {
            log.error("Failed to read accounts CSV from {}: {}", accountsPath, e.getMessage(), e);
            return;
        }

        accounts = loaded;
        log.debug("Loaded {} account(s) from {}.", accounts.size(), accountsPath);
    }

    @Override
    public synchronized Optional<Account> findById(String userId) {
        return Optional.ofNullable(accounts.get(userId));
    }

    @Override
    public synchronized Optional<Account> findByDisplayName(String displayName) {
        return accounts.values().stream()
                .filter(a -> a.displayName().equalsIgnoreCase(displayName))
                .findFirst();
    }

    @Override
    public synchronized List<Account> findAll() {
        return accounts.values().stream()
                .sorted(Comparator.comparing(Account::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Override
    public synchronized Account save(Account account) {
        accounts.put(account.userId(), account);
        writeToCsv();
        return account;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private @Nullable Account parseRecord(CSVRecord record, int rowNumber) {
        if (record.size() != EXPECTED_COLUMNS) {
            log.warn("Accounts CSV row {}: expected {} columns, got {}; skipping.",
                    rowNumber, EXPECTED_COLUMNS, record.size());
            return null;
        }

        var userId = record.get(COL_USER_ID);
        var displayName = record.get(COL_DISPLAY_NAME);
        var createdAtRaw = record.get(COL_CREATED_AT);

        if (userId.isBlank()) {
            log.warn("Accounts CSV row {}: userId is blank; skipping.", rowNumber);
            return null;
        }
        if (displayName.isBlank()) {
            log.warn("Accounts CSV row {}: displayName is blank; skipping.", rowNumber);
            return null;
        }
        if (createdAtRaw.isBlank()) {
            log.warn("Accounts CSV row {}: createdAt is blank; skipping.", rowNumber);
            return null;
        }

        Instant createdAt;
        try {
            createdAt = Instant.parse(createdAtRaw);
        } catch (DateTimeParseException e) {
            log.warn("Accounts CSV row {}: cannot parse createdAt '{}'; skipping.", rowNumber, createdAtRaw);
            return null;
        }

        try {
            return new Account(userId, displayName, createdAt);
        } catch (IllegalArgumentException e) {
            log.warn("Accounts CSV row {}: invalid account data — {}; skipping.", rowNumber, e.getMessage());
            return null;
        }
    }

    private void writeToCsv() {
        var tempPath = accountsPath.resolveSibling(accountsPath.getFileName() + ".tmp");
        try {
            var parent = accountsPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
                for (var account : accounts.values()) {
                    writer.write(escape(account.userId()) + ";" +
                                 escape(account.displayName()) + ";" +
                                 account.createdAt().toString());
                    writer.newLine();
                }
            }

            atomicMove(tempPath, accountsPath);
            log.debug("Accounts CSV written successfully to {}.", accountsPath);

        } catch (IOException e) {
            log.error("Failed to write accounts CSV to {}: {}", accountsPath, e.getMessage(), e);
            tryDeleteTemp(tempPath);
        }
    }

    private void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            log.debug("ATOMIC_MOVE not supported for {}; falling back to REPLACE_EXISTING.", target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void tryDeleteTemp(Path tempPath) {
        try {
            Files.deleteIfExists(tempPath);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private String escape(String value) {
        return value.replace(";", "_")
                    .replace("\r", "")
                    .replace("\n", "");
    }
}
