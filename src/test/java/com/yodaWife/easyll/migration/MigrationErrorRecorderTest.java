package com.yodawife.easyll.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationErrorRecorderTest {

    @TempDir
    @Nullable Path tempDir;

    private Path tempDir() {
        return Objects.requireNonNull(tempDir);
    }

    @Test
    @DisplayName("close writes error file with header row and two data rows")
    void close_writesErrorFile_withHeaderAndRows() throws IOException {
        var outputPath = tempDir().resolve("errors.csv").toString();

        try (var recorder = new MigrationErrorRecorder(outputPath)) {
            recorder.record("userId:abc", "users.csv", "not found");
            recorder.record("pairId:xyz", "scores.csv", "pair not found");
        }

        var lines = Files.readAllLines(Path.of(outputPath), StandardCharsets.UTF_8);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo("row_number;source_table;error_message");
        assertThat(lines.get(1)).contains("userId:abc").contains("users.csv").contains("not found");
        assertThat(lines.get(2)).contains("pairId:xyz").contains("scores.csv").contains("pair not found");
    }

    @Test
    @DisplayName("close does not create the output file when no errors have been recorded")
    void close_doesNotCreateFile_whenNoErrors() {
        var outputPath = tempDir().resolve("no-errors.csv");

        try (var recorder = new MigrationErrorRecorder(outputPath.toString())) {
            // record nothing
        }

        assertThat(outputPath).doesNotExist();
    }

    @Test
    @DisplayName("errorCount increments by one for each recorded error")
    void errorCount_incrementsOnRecord() {
        var recorder = new MigrationErrorRecorder(tempDir().resolve("count-errors.csv").toString());

        recorder.record("row1", "table1", "error1");
        recorder.record("row2", "table2", "error2");
        recorder.record("row3", "table3", "error3");

        assertThat(recorder.errorCount()).isEqualTo(3);
        recorder.close();
    }

    @Test
    @DisplayName("semicolons inside field values are replaced with underscores to keep CSV structure intact")
    void escape_replacesSemicolonWithUnderscore() throws IOException {
        var outputPath = tempDir().resolve("semicolon-errors.csv").toString();

        try (var recorder = new MigrationErrorRecorder(outputPath)) {
            recorder.record("userId:abc", "scores.csv", "error;with;semicolons");
        }

        var content = Files.readString(Path.of(outputPath), StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("error;with;semicolons");
        assertThat(content).contains("error_with_semicolons");
    }
}
