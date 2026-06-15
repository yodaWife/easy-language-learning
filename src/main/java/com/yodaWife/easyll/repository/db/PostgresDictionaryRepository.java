package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.repository.DictionaryRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed implementation of {@link DictionaryRepository}.
 * Active when the {@code db} Spring profile is enabled.
 */
@Repository
public class PostgresDictionaryRepository implements DictionaryRepository {

    private final JdbcTemplate jdbc;

    public PostgresDictionaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<LanguageBundle> findLanguage(String languageCode) {
        var words = jdbc.query(
            """
            SELECT pair_id, language_code, from_word, to_word, example, global_enabled
            FROM dictionary_pair
            WHERE language_code = ?
            """,
            (rs, rowNum) -> new Word(
                new WordId(rs.getString("pair_id")),
                rs.getString("from_word"),
                rs.getString("to_word"),
                rs.getString("example"),
                rs.getBoolean("global_enabled")),
            languageCode);

        if (words.isEmpty()) {
            return Optional.empty();
        }

        var modeEligibilities = jdbc.query(
            """
            SELECT me.pair_id, me.mode, me.enabled
            FROM mode_eligibility me
            JOIN dictionary_pair dp ON me.pair_id = dp.pair_id
            WHERE dp.language_code = ?
            """,
            (rs, rowNum) -> new ModeEligibility(
                new WordId(rs.getString("pair_id")),
                rs.getString("mode"),
                rs.getBoolean("enabled")),
            languageCode);

        return Optional.of(new LanguageBundle(languageCode, null, words, modeEligibilities, List.of()));
    }

    @Override
    public List<String> availableLanguages() {
        return jdbc.queryForList(
            "SELECT DISTINCT language_code FROM dictionary_pair ORDER BY language_code ASC",
            String.class);
    }

    @Override
    public void updateGlobalEnabled(String pairId, boolean enabled) {
        jdbc.update(
            "UPDATE dictionary_pair SET global_enabled = ? WHERE pair_id = ?",
            enabled, pairId);
    }

    @Override
    public void updateWordContent(String pairId, String fromWord, String toWord, String example) {
        jdbc.update(
            "UPDATE dictionary_pair SET from_word = ?, to_word = ?, example = ? WHERE pair_id = ?",
            fromWord, toWord, example, pairId);
    }

    @Override
    public void insertWord(String languageCode, String pairId, String fromWord, String toWord, String example, boolean globalEnabled) {
        jdbc.update(
            "INSERT INTO dictionary_pair (pair_id, language_code, from_word, to_word, example, global_enabled) VALUES (?, ?, ?, ?, ?, ?)",
            pairId, languageCode, fromWord, toWord, example, globalEnabled);
    }

    @Override
    public void upsertModeEligibility(String pairId, String mode, boolean enabled) {
        jdbc.update(
            "INSERT INTO mode_eligibility (pair_id, mode, enabled) VALUES (?, ?, ?) " +
            "ON CONFLICT (pair_id, mode) DO UPDATE SET enabled = EXCLUDED.enabled",
            pairId, mode, enabled);
    }
}

