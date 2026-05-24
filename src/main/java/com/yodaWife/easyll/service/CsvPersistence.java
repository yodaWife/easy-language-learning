package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Handles atomic CSV persistence for dictionary data.
 * All writes use a temp-file-then-rename strategy to ensure atomicity.
 */
@Service
public class CsvPersistence {

    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setDelimiter(';')
            .build();

    /**
     * Writes the given words to a CSV file at {@code targetPath} atomically.
     * The CSV includes a header row: {@code WORD_ID;FROM;TO;EXAMPLE;GLOBAL_ENABLED}.
     *
     * @param targetPath the destination file path
     * @param words      the list of words to write
     * @throws IOException if an I/O error occurs during writing or the atomic move fails
     */
    public void writeWords(Path targetPath, List<Word> words) throws IOException {
        var tempPath = resolveTempPath(targetPath);
        try {
            writeWordsToCsv(tempPath, words);
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tempPath);
            throw e;
        }
    }

    /**
     * Writes the given mode eligibilities to a CSV file at {@code targetPath} atomically.
     * The CSV includes a header row: {@code WORD_ID;MODE;ENABLED}.
     *
     * @param targetPath    the destination file path
     * @param eligibilities the list of mode eligibilities to write
     * @throws IOException if an I/O error occurs during writing or the atomic move fails
     */
    public void writeModeEligibilities(Path targetPath, List<ModeEligibility> eligibilities) throws IOException {
        var tempPath = resolveTempPath(targetPath);
        try {
            writeModeEligibilitiesToCsv(tempPath, eligibilities);
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tempPath);
            throw e;
        }
    }

    private void writeWordsToCsv(Path path, List<Word> words) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            printer.printRecord("WORD_ID", "FROM", "TO", "EXAMPLE", "GLOBAL_ENABLED");
            for (var word : words) {
                printer.printRecord(
                        word.wordId().value(),
                        word.fromWord(),
                        word.toWord(),
                        word.example(),
                        word.globalEnabled()
                );
            }
        }
    }

    private void writeModeEligibilitiesToCsv(Path path, List<ModeEligibility> eligibilities) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
             CSVPrinter printer = new CSVPrinter(writer, CSV_FORMAT)) {
            printer.printRecord("WORD_ID", "MODE", "ENABLED");
            for (var eligibility : eligibilities) {
                printer.printRecord(
                        eligibility.wordId().value(),
                        eligibility.mode(),
                        eligibility.enabled()
                );
            }
        }
    }

    private static Path resolveTempPath(Path targetPath) {
        var parent = targetPath.getParent();
        var fileName = targetPath.getFileName().toString() + ".tmp";
        return parent != null ? parent.resolve(fileName) : Path.of(fileName);
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup — original exception is propagated
        }
    }
}
