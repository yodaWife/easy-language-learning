package com.yodawife.easyll.controller;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DictionaryEditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DictionaryController {

    private static final Logger log = LoggerFactory.getLogger(DictionaryController.class);
    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final String FRAGMENT_ROW = "fragments/dictionary-row :: row";
    private static final String ATTR_WORD = "wv";
    private static final String ATTR_MODES = "modes";
    private static final String ATTR_LANGUAGE_CODE = "languageCode";
    private static final String ATTR_ERROR = "error";
    private static final String SORT_BY_FROM = "FROM";
    private static final String SORT_DIR_DESC = "DESC";

    private final DataHealthService dataHealthService;
    private final DictionaryEditService dictionaryEditService;
    private final DictionaryProperties dictionaryProperties;

    public DictionaryController(
            DataHealthService dataHealthService,
            DictionaryEditService dictionaryEditService,
            DictionaryProperties dictionaryProperties) {
        this.dataHealthService = dataHealthService;
        this.dictionaryEditService = dictionaryEditService;
        this.dictionaryProperties = dictionaryProperties;
    }

    @GetMapping("/dictionary")
    public String dictionaryPage(
            @RequestParam(defaultValue = "") String languageCode,
            @RequestParam(defaultValue = "FROM") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Model model) {
        var effectiveLang = resolveLanguage(languageCode);
        populateModel(model, effectiveLang, sortBy, sortDir, search, page, clampPageSize(pageSize));
        return "dictionary";
    }

    @GetMapping("/dictionary/rows")
    public String dictionaryRows(
            @RequestParam(defaultValue = "") String languageCode,
            @RequestParam(defaultValue = "FROM") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Model model) {
        var effectiveLang = resolveLanguage(languageCode);
        populateModel(model, effectiveLang, sortBy, sortDir, search, page, clampPageSize(pageSize));
        return "fragments/dictionary-table :: table-rows";
    }

    @PostMapping("/dictionary/toggle/global")
    public String toggleGlobal(
            @RequestParam String languageCode,
            @RequestParam String wordId,
            @RequestParam(defaultValue = "FROM") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);

        if (languageCode.isBlank() || wordId.isBlank()) {
            model.addAttribute(ATTR_ERROR, "Invalid request: languageCode and wordId must not be blank");
            return FRAGMENT_ROW;
        }

        var typedWordId = new WordId(wordId);
        var result = dictionaryEditService.toggleGlobalEnabled(languageCode, typedWordId);

        switch (result) {
            case DictionaryOperationResult.Success<Word> s -> {
                var eligibilities = dataHealthService.snapshot()
                        .getLanguageBundle(languageCode)
                        .map(LanguageBundle::modeEligibilities)
                        .orElse(List.of());
                model.addAttribute(ATTR_WORD, new WordViewModel(s.value(), eligibilities, modes));
            }
            case DictionaryOperationResult.Failure<Word> f -> {
                log.warn("toggleGlobalEnabled failed for language={}, wordId={}: {}",
                        languageCode, wordId, f.errorMessage());
                model.addAttribute(ATTR_ERROR, f.errorMessage());
                dataHealthService.snapshot().getLanguageBundle(languageCode).ifPresent(lb ->
                        lb.words().stream()
                                .filter(w -> w.wordId().equals(typedWordId))
                                .findFirst()
                                .ifPresent(w -> model.addAttribute(
                                        ATTR_WORD, new WordViewModel(w, lb.modeEligibilities(), modes))));
            }
        }

        return FRAGMENT_ROW;
    }

    @PostMapping("/dictionary/toggle/mode")
    public String toggleMode(
            @RequestParam String languageCode,
            @RequestParam String wordId,
            @RequestParam String mode,
            @RequestParam(defaultValue = "FROM") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            @RequestParam(defaultValue = "") String search,
            Model model) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);

        if (languageCode.isBlank() || wordId.isBlank() || mode.isBlank()) {
            model.addAttribute(ATTR_ERROR, "Invalid request: languageCode, wordId, and mode must not be blank");
            return FRAGMENT_ROW;
        }

        var typedWordId = new WordId(wordId);
        var result = dictionaryEditService.toggleModeEnabled(languageCode, typedWordId, mode);

        switch (result) {
            case DictionaryOperationResult.Success<ModeEligibility> ignored -> {
                var snapshot = dataHealthService.snapshot();
                var bundleOpt = snapshot.getLanguageBundle(languageCode);
                if (bundleOpt.isEmpty()) {
                    log.warn("No language bundle found for languageCode={} after toggleModeEnabled", languageCode);
                }
                bundleOpt.ifPresent(lb ->
                        lb.words().stream()
                                .filter(w -> w.wordId().equals(typedWordId))
                                .findFirst()
                                .ifPresent(w -> model.addAttribute(
                                        ATTR_WORD, new WordViewModel(w, lb.modeEligibilities(), modes))));
            }
            case DictionaryOperationResult.Failure<ModeEligibility> f -> {
                log.warn("toggleModeEnabled failed for language={}, wordId={}, mode={}: {}",
                        languageCode, wordId, mode, f.errorMessage());
                model.addAttribute(ATTR_ERROR, f.errorMessage());
                dataHealthService.snapshot().getLanguageBundle(languageCode).ifPresent(lb ->
                        lb.words().stream()
                                .filter(w -> w.wordId().equals(typedWordId))
                                .findFirst()
                                .ifPresent(w -> model.addAttribute(
                                        ATTR_WORD, new WordViewModel(w, lb.modeEligibilities(), modes))));
            }
        }

        return FRAGMENT_ROW;
    }

    private void populateModel(Model model, String effectiveLang, String sortBy, String sortDir,
                               String search, int page, int clampedPageSize) {
        var modes = dictionaryProperties.getModes();
        var snapshot = dataHealthService.snapshot();
        var bundle = snapshot.getLanguageBundle(effectiveLang);
        var allWords = bundle.map(LanguageBundle::words).orElse(List.of());
        var eligibilities = bundle.map(LanguageBundle::modeEligibilities).orElse(List.of());

        model.addAttribute("languages", dataHealthService.availableLanguages());
        model.addAttribute("currentLanguage", effectiveLang);
        model.addAttribute(ATTR_LANGUAGE_CODE, effectiveLang);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("search", search);
        model.addAttribute("page", page);
        model.addAttribute("pageSize", clampedPageSize);
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute("totalWords", countFiltered(allWords, search));
        model.addAttribute("words",
                applyFilterSortPage(allWords, eligibilities, modes, sortBy, sortDir, search, page, clampedPageSize));
    }

    private String resolveLanguage(String languageCode) {
        return languageCode.isBlank() ? dictionaryProperties.getPrimaryLanguageCode() : languageCode;
    }

    private static int clampPageSize(int pageSize) {
        return Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, pageSize));
    }

    private static boolean matchesSearch(Word word, String search) {
        if (search.isBlank()) {
            return true;
        }
        var lower = search.toLowerCase();
        return word.fromWord().toLowerCase().contains(lower)
                || word.toWord().toLowerCase().contains(lower)
                || word.example().toLowerCase().contains(lower);
    }

    private static int countFiltered(List<Word> words, String search) {
        return (int) words.stream().filter(w -> matchesSearch(w, search)).count();
    }

    private static List<WordViewModel> applyFilterSortPage(
            List<Word> words,
            List<ModeEligibility> eligibilities,
            List<String> modes,
            String sortBy,
            String sortDir,
            String search,
            int page,
            int pageSize) {
        var filtered = words.stream()
                .filter(w -> matchesSearch(w, search))
                .toList();

        Comparator<Word> comparator = SORT_BY_FROM.equals(sortBy)
                ? Comparator.comparing(Word::fromWord, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparing(Word::toWord, String.CASE_INSENSITIVE_ORDER);

        if (SORT_DIR_DESC.equals(sortDir)) {
            comparator = comparator.reversed();
        }

        var sorted = filtered.stream().sorted(comparator).toList();

        var fromIdx = page * pageSize;
        if (fromIdx >= sorted.size()) {
            return List.of();
        }
        var toIdx = Math.min(fromIdx + pageSize, sorted.size());

        return sorted.subList(fromIdx, toIdx).stream()
                .map(w -> new WordViewModel(w, eligibilities, modes))
                .toList();
    }

    /**
     * View model for a dictionary word that exposes per-mode eligibility flags for Thymeleaf rendering.
     */
    public static final class WordViewModel {

        private final Word word;
        private final Map<String, Boolean> modeEnabled;

        public WordViewModel(Word word, List<ModeEligibility> eligibilities, List<String> modes) {
            this.word = word;
            var map = new LinkedHashMap<String, Boolean>();
            for (var m : modes) {
                boolean enabled = eligibilities.stream()
                        .filter(e -> e.wordId().equals(word.wordId()) && e.mode().equals(m))
                        .map(ModeEligibility::enabled)
                        .findFirst()
                        .orElse(true);
                map.put(m, enabled);
            }
            this.modeEnabled = Collections.unmodifiableMap(map);
        }

        public Word getWord() {
            return word;
        }

        public Map<String, Boolean> getModeEnabled() {
            return modeEnabled;
        }
    }
}
