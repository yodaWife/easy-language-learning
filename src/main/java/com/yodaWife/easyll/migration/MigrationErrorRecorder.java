package com.yodawife.easyll.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects migration errors and writes them to a CSV output file.
 * Call {@link #record(String, String, String)} for each error,
 * then {@link #close()} to flush all errors to disk.
 */
public class MigrationErrorRecorder implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(MigrationErrorRecorder.class);

    private final Path outputPath;
    private final List<String[]> errors = new ArrayList<>();

    public MigrationErrorRecorder(String outputPath) {
        this.outputPath = Path.of(outputPath);
    }

    /**
     * Record a migration error.
     *
     * @param rowNumber      a human-readable identifier for the source row (e.g. "userId:abc123")
     * @param sourceTable    name of the source table/file (e.g. "scores.csv")
     * @param errorMessage   human-readable error description
     */
    public void record(String rowNumber, String sourceTable, String errorMessage) {
        errors.add(new String[]{rowNumber, sourceTable, errorMessage});
        log.warn("Migration error [{}] [{}]: {}", rowNumber, sourceTable, errorMessage);
    }

    public int errorCount() {
        return errors.size();
    }

    @Override
    public void close() {
        if (errors.isEmpty()) return;
        try {
            var parent = outputPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write("row_number;source_table;error_message");
                writer.newLine();
                for (var row : errors) {
                    writer.write(escape(row[0]) + ";" + escape(row[1]) + ";" + escape(row[2]));
                    writer.newLine();
                }
            }
            log.info("Migration errors written to {}: {} error(s)", outputPath, errors.size());
        } catch (IOException e) {
            log.error("Failed to write migration errors to {}: {}", outputPath, e.getMessage(), e);
        }
    }

    private String escape(String value) {
        return value == null ? "" : value.replace(";", "_");
    }
}
