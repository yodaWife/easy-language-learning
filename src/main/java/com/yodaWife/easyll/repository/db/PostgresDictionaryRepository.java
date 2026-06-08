package com.yodawife.easyll.repository.db;

import com.yodawife.easyll.config.PersistenceProfiles;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.repository.DictionaryRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * PostgreSQL-backed implementation of {@link DictionaryRepository}.
 * Active when the {@value PersistenceProfiles#DB} Spring profile is enabled.
 */
@Repository
@Profile(PersistenceProfiles.DB)
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

        return Optional.of(new LanguageBundle(languageCode, null, words, List.of(), List.of()));
    }

    @Override
    public List<String> availableLanguages() {
        return jdbc.queryForList(
            "SELECT DISTINCT language_code FROM dictionary_pair ORDER BY language_code ASC",
            String.class);
    }
}
