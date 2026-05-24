package com.yodawife.easyll.validation;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.CsvParseResult;
import com.yodawife.easyll.service.DictionaryDiscoveryService;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.MultiLanguageDataBundle;
import com.yodawife.easyll.domain.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrating parser that ties together language discovery and per-language CSV parsing.
 *
 * <p>Calls {@link DictionaryDiscoveryService} to enumerate language
 * folders, then delegates to {@link WordsCsvParser} and {@link ModeEligibilityCsvParser} for each
 * language. Errors in one language's parsing are isolated and stored in that language's
 * {@link LanguageBundle} — they never propagate to other languages.
 */
@Component
public class MultiLanguageDictionaryParser {

    private static final Logger log = LoggerFactory.getLogger(MultiLanguageDictionaryParser.class);

    private static final String WORDS_CSV = "words.csv";
    private static final String MODE_ELIGIBILITY_CSV = "mode-eligibility.csv";

    private final DictionaryDiscoveryService discoveryService;
    private final DictionaryProperties dictionaryProperties;

    public MultiLanguageDictionaryParser(
            DictionaryDiscoveryService discoveryService,
            DictionaryProperties dictionaryProperties) {
        this.discoveryService = discoveryService;
        this.dictionaryProperties = dictionaryProperties;
    }

    /**
     * Parses all discovered language dictionaries.
     *
     * <p>Returns an empty bundle map (no exception) when no language folders are found.
     * Each language is parsed in isolation: a failure in one language never aborts the others.
     *
     * @return a {@link MultiLanguageDataBundle} containing per-language results and the resolved
     *         primary language code
     */
    public MultiLanguageDataBundle parseAll() {
        var discovered = discoveryService.discoverLanguages();

        if (discovered.isEmpty()) {
            log.warn("No language dictionaries discovered under root path '{}'", dictionaryProperties.getRootPath());
            return new MultiLanguageDataBundle(Map.of(), dictionaryProperties.getPrimaryLanguageCode());
        }

        var bundles = new HashMap<String, LanguageBundle>();

        for (var entry : discovered.entrySet()) {
            var code = entry.getKey();
            var folder = entry.getValue();
            log.info("Parsing language dictionary: '{}'", code);
            bundles.put(code, parseLanguage(code, folder));
        }

        var primaryCode = resolvePrimaryLanguageCode(bundles);
        return new MultiLanguageDataBundle(bundles, primaryCode);
    }

    private LanguageBundle parseLanguage(String code, Path folder) {
        try {
            if (!discoveryService.hasRequiredFiles(folder)) {
                log.warn("Language '{}': required files missing in folder '{}'", code, folder);
                return new LanguageBundle(
                        code, null, List.of(), List.of(),
                        List.of("Language '" + code + "': required files missing (words.csv and/or mode-eligibility.csv)"));
            }

            var wordsResult = new WordsCsvParser(folder.resolve(WORDS_CSV)).parse();

            return switch (wordsResult) {
                case CsvParseResult.Failure<WordsCsvParser.WordParseData> failure -> {
                    log.warn("Language '{}': words.csv parse failed with {} error(s)", code, failure.errors().size());
                    yield new LanguageBundle(code, null, List.of(), List.of(), failure.errors());
                }
                case CsvParseResult.Success<WordsCsvParser.WordParseData> success -> {
                    var words = success.value().words();
                    var wordIds = words.stream().map(Word::wordId).toList();
                    var eligibilityResult = new ModeEligibilityCsvParser(folder.resolve(MODE_ELIGIBILITY_CSV)).parse(wordIds);
                    yield switch (eligibilityResult) {
                        case CsvParseResult.Failure<List<ModeEligibility>> eligibilityFailure -> {
                            log.warn("Language '{}': mode-eligibility.csv parse failed with {} error(s)",
                                    code, eligibilityFailure.errors().size());
                            yield new LanguageBundle(code, null, words, List.of(), eligibilityFailure.errors());
                        }
                        case CsvParseResult.Success<List<ModeEligibility>> eligibilitySuccess ->
                                new LanguageBundle(code, null, words, eligibilitySuccess.value(), List.of());
                    };
                }
            };
        } catch (Exception e) {
            var message = e.getMessage();
            var description = message != null ? message : e.getClass().getSimpleName();
            log.warn("Language '{}': unexpected error during parsing: {}", code, description, e);
            return new LanguageBundle(code, null, List.of(), List.of(),
                    List.of("Language '" + code + "': unexpected error during parsing: " + description));
        }
    }

    private String resolvePrimaryLanguageCode(Map<String, LanguageBundle> bundles) {
        var configured = dictionaryProperties.getPrimaryLanguageCode();
        if (bundles.containsKey(configured)) {
            return configured;
        }
        var fallback = bundles.keySet().stream().sorted().findFirst().orElse(configured);
        log.warn("Configured primary language '{}' was not discovered; falling back to '{}'", configured, fallback);
        return fallback;
    }
}
