package com.yodawife.easyll.controller;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.LanguageBundle;
import com.yodawife.easyll.domain.ModeEligibility;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.service.AccountService;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DictionaryEditService;
import com.yodawife.easyll.service.ScoreProgressService;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
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
    private static final String FRAGMENT_ROW_EDIT = "fragments/dictionary-row :: row-edit";
    private static final String FRAGMENT_ROW_NEW = "fragments/dictionary-row :: row-new";
    private static final String ATTR_WORD = "wv";
    private static final String ATTR_MODES = "modes";
    private static final String ATTR_LANGUAGE_CODE = "languageCode";
    private static final String ATTR_ERROR = "error";
    private static final String SORT_BY_FROM = "FROM";
    private static final String SORT_BY_PROGRESS = "PROGRESS";
    private static final String SORT_DIR_DESC = "DESC";

    private final DataHealthService dataHealthService;
    private final DictionaryEditService dictionaryEditService;
    private final DictionaryProperties dictionaryProperties;
    private final ScoreProgressService scoreProgressService;
    private final AccountService accountService;

    public DictionaryController(
            DataHealthService dataHealthService,
            DictionaryEditService dictionaryEditService,
            DictionaryProperties dictionaryProperties,
            ScoreProgressService scoreProgressService,
            AccountService accountService) {
        this.dataHealthService = dataHealthService;
        this.dictionaryEditService = dictionaryEditService;
        this.dictionaryProperties = dictionaryProperties;
        this.scoreProgressService = scoreProgressService;
        this.accountService = accountService;
    }

    @GetMapping("/dictionary")
    public String dictionaryPage(
            @RequestParam(defaultValue = "") String languageCode,
            @RequestParam(defaultValue = "FROM") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            Model model,
            HttpSession session) {
        var effectiveLang = resolveLanguage(languageCode);
        populateModel(model, effectiveLang, sortBy, sortDir, search, page, clampPageSize(pageSize), session);
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
            Model model,
            HttpSession session) {
        var effectiveLang = resolveLanguage(languageCode);
        populateModel(model, effectiveLang, sortBy, sortDir, search, page, clampPageSize(pageSize), session);
        return "fragments/dictionary-table :: table-rows";
    }

    @PostMapping("/dictionary/toggle/global")
    public String toggleGlobal(
            @RequestParam String languageCode,
            @RequestParam String wordId,
            @RequestParam(defaultValue = "FROM") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDir,
            @RequestParam(defaultValue = "") String search,
            Model model,
            HttpSession session) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);
        var activeUser = accountService.resolveActiveUser(session);
        boolean progressEnabled = activeUser.signedIn() && activeUser.userId() != null;
        model.addAttribute("progressEnabled", progressEnabled);
        var userId = activeUser.userId();
        Map<String, Integer> progressMap = (userId != null) ? scoreProgressService.getProgressForUser(userId) : Map.of();

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
                model.addAttribute(ATTR_WORD, new WordViewModel(s.value(), eligibilities, modes,
                        progressMap.get(s.value().wordId().value())));
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
            Model model,
            HttpSession session) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);
        var activeUser = accountService.resolveActiveUser(session);
        boolean progressEnabled = activeUser.signedIn() && activeUser.userId() != null;
        model.addAttribute("progressEnabled", progressEnabled);
        var userId = activeUser.userId();
        Map<String, Integer> progressMap = (userId != null) ? scoreProgressService.getProgressForUser(userId) : Map.of();

        if (languageCode.isBlank() || wordId.isBlank() || mode.isBlank()) {
            model.addAttribute(ATTR_ERROR, "Invalid request: languageCode, wordId, and mode must not be blank");
            return FRAGMENT_ROW;
        }

        var typedWordId = new WordId(wordId);
        // Capture the word before the toggle — word data is unchanged by a mode toggle
        var preToggleBundle = dataHealthService.snapshot().getLanguageBundle(languageCode);
        var wordBeforeToggle = preToggleBundle.flatMap(lb -> lb.words().stream()
                .filter(w -> w.wordId().equals(typedWordId))
                .findFirst());

        var result = dictionaryEditService.toggleModeEnabled(languageCode, typedWordId, mode);

        switch (result) {
            case DictionaryOperationResult.Success<ModeEligibility> s -> {
                // Use updated eligibilities from post-reload snapshot; if bundle is unavailable, fall back
                // to applying the single change to the pre-toggle eligibilities
                var postReloadBundle = dataHealthService.snapshot().getLanguageBundle(languageCode);
                var eligibilities = postReloadBundle
                        .map(LanguageBundle::modeEligibilities)
                        .orElseGet(() -> {
                            log.warn("Post-reload bundle unavailable for languageCode={}; applying toggle to pre-toggle state", languageCode);
                            return withAppliedChange(
                                    preToggleBundle.map(LanguageBundle::modeEligibilities).orElse(List.of()),
                                    typedWordId, mode, s.value());
                        });
                wordBeforeToggle.ifPresentOrElse(
                        w -> model.addAttribute(ATTR_WORD,
                                new WordViewModel(w, eligibilities, modes, progressMap.get(w.wordId().value()))),
                        () -> {
                            log.warn("Word not found before toggle: language={}, wordId={}", languageCode, wordId);
                            model.addAttribute(ATTR_ERROR, "Word not found");
                        });
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

    @GetMapping("/dictionary/row")
    public String dictionaryRow(
            @RequestParam String languageCode,
            @RequestParam String wordId,
            Model model,
            HttpSession session) {
        populateSingleRowModel(model, languageCode, wordId, session);
        return FRAGMENT_ROW;
    }

    @GetMapping("/dictionary/row/new")
    public String newWordRow(
            @RequestParam String languageCode,
            Model model) {
        model.addAttribute(ATTR_MODES, dictionaryProperties.getModes());
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);
        return FRAGMENT_ROW_NEW;
    }

    @PostMapping("/dictionary/add")
    public String addWord(
            @RequestParam String languageCode,
            @RequestParam(defaultValue = "") String fromWord,
            @RequestParam(defaultValue = "") String toWord,
            @RequestParam(defaultValue = "") String example,
            Model model,
            HttpSession session) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);
        var activeUser = accountService.resolveActiveUser(session);
        boolean progressEnabled = activeUser.signedIn() && activeUser.userId() != null;
        model.addAttribute("progressEnabled", progressEnabled);
        var userId = activeUser.userId();
        Map<String, Integer> progressMap = (userId != null) ? scoreProgressService.getProgressForUser(userId) : Map.of();

        if (languageCode.isBlank()) {
            model.addAttribute(ATTR_ERROR, "Invalid request: languageCode must not be blank");
            return FRAGMENT_ROW_NEW;
        }

        if (fromWord.isBlank() || toWord.isBlank()) {
            model.addAttribute(ATTR_ERROR, "From and To words must not be blank");
            return FRAGMENT_ROW_NEW;
        }

        var result = dictionaryEditService.addWord(languageCode, fromWord, toWord, example);
        return switch (result) {
            case DictionaryOperationResult.Success<Word> s -> {
                var eligibilities = dataHealthService.snapshot()
                        .getLanguageBundle(languageCode)
                        .map(LanguageBundle::modeEligibilities)
                        .orElse(List.of());
                model.addAttribute(ATTR_WORD, new WordViewModel(s.value(), eligibilities, modes,
                        progressMap.get(s.value().wordId().value())));
                yield FRAGMENT_ROW;
            }
            case DictionaryOperationResult.Failure<Word> f -> {
                log.warn("addWord failed for language={}: {}", languageCode, f.errorMessage());
                model.addAttribute(ATTR_ERROR, f.errorMessage());
                yield FRAGMENT_ROW_NEW;
            }
        };
    }

    @GetMapping("/dictionary/row/edit")
    public String dictionaryRowEdit(
            @RequestParam String languageCode,
            @RequestParam String wordId,
            Model model,
            HttpSession session) {
        populateSingleRowModel(model, languageCode, wordId, session);
        return FRAGMENT_ROW_EDIT;
    }

    @PostMapping("/dictionary/edit")
    public String editWord(
            @RequestParam String languageCode,
            @RequestParam String wordId,
            @RequestParam String fromWord,
            @RequestParam String toWord,
            @RequestParam(defaultValue = "") String example,
            Model model,
            HttpSession session) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);
        var activeUser = accountService.resolveActiveUser(session);
        boolean progressEnabled = activeUser.signedIn() && activeUser.userId() != null;
        model.addAttribute("progressEnabled", progressEnabled);
        var userId = activeUser.userId();
        Map<String, Integer> progressMap = (userId != null) ? scoreProgressService.getProgressForUser(userId) : Map.of();

        if (languageCode.isBlank() || wordId.isBlank()) {
            model.addAttribute(ATTR_ERROR, "Invalid request: languageCode and wordId must not be blank");
            return FRAGMENT_ROW;
        }

        if (fromWord.isBlank() || toWord.isBlank()) {
            model.addAttribute(ATTR_ERROR, "From and To words must not be blank");
            populateSingleRowModel(model, languageCode, wordId, session);
            return FRAGMENT_ROW_EDIT;
        }

        var typedWordId = new WordId(wordId);
        var result = dictionaryEditService.editWord(languageCode, typedWordId, fromWord, toWord, example);

        switch (result) {
            case DictionaryOperationResult.Success<Word> s -> {
                var eligibilities = dataHealthService.snapshot()
                        .getLanguageBundle(languageCode)
                        .map(LanguageBundle::modeEligibilities)
                        .orElse(List.of());
                model.addAttribute(ATTR_WORD, new WordViewModel(s.value(), eligibilities, modes,
                        progressMap.get(s.value().wordId().value())));
            }
            case DictionaryOperationResult.Failure<Word> f -> {
                log.warn("editWord failed for language={}, wordId={}: {}", languageCode, wordId, f.errorMessage());
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

    private void populateSingleRowModel(Model model, String languageCode, String wordId, HttpSession session) {
        var modes = dictionaryProperties.getModes();
        model.addAttribute(ATTR_MODES, modes);
        model.addAttribute(ATTR_LANGUAGE_CODE, languageCode);
        var activeUser = accountService.resolveActiveUser(session);
        boolean progressEnabled = activeUser.signedIn() && activeUser.userId() != null;
        model.addAttribute("progressEnabled", progressEnabled);
        var userId = activeUser.userId();
        var typedWordId = new WordId(wordId);
        var found = dataHealthService.snapshot().getLanguageBundle(languageCode)
                .flatMap(lb -> lb.words().stream()
                        .filter(w -> w.wordId().equals(typedWordId))
                        .findFirst()
                        .map(w -> {
                            var progressPercent = (progressEnabled && userId != null)
                                    ? scoreProgressService.getProgressForUser(userId).get(w.wordId().value())
                                    : null;
                            return new WordViewModel(w, lb.modeEligibilities(), modes, progressPercent);
                        }));
        if (found.isPresent()) {
            model.addAttribute(ATTR_WORD, found.get());
        } else {
            model.addAttribute(ATTR_ERROR, "Word not found: " + wordId);
        }
    }

    private void populateModel(Model model, String effectiveLang, String sortBy, String sortDir,
                               String search, int page, int clampedPageSize, HttpSession session) {
        var modes = dictionaryProperties.getModes();
        var snapshot = dataHealthService.snapshot();
        var bundle = snapshot.getLanguageBundle(effectiveLang);
        var allWords = bundle.map(LanguageBundle::words).orElse(List.of());
        var eligibilities = bundle.map(LanguageBundle::modeEligibilities).orElse(List.of());

        var activeUser = accountService.resolveActiveUser(session);
        var userId = activeUser.userId();
        boolean progressEnabled = activeUser.signedIn() && userId != null;
        Map<String, Integer> progressMap = userId != null && activeUser.signedIn()
                ? scoreProgressService.getProgressForUser(userId)
                : Map.of();

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
        model.addAttribute("progressEnabled", progressEnabled);
        model.addAttribute("words",
                applyFilterSortPage(allWords, eligibilities, modes, sortBy, sortDir, search, page, clampedPageSize, progressMap));
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
            int pageSize,
            Map<String, Integer> progressMap) {
        var filtered = words.stream()
                .filter(w -> matchesSearch(w, search))
                .toList();

        Comparator<Word> comparator;
        if (SORT_BY_FROM.equals(sortBy)) {
            comparator = Comparator.comparing(Word::fromWord, String.CASE_INSENSITIVE_ORDER);
        } else if (SORT_BY_PROGRESS.equals(sortBy)) {
            // Words with no history are treated as -1 so they sort before 0% in ASC
            // (unstarted words first) and after everything in DESC (most-learned first).
            comparator = Comparator.comparingInt((Word w) ->
                    progressMap.getOrDefault(w.wordId().value(), -1));
        } else {
            comparator = Comparator.comparing(Word::toWord, String.CASE_INSENSITIVE_ORDER);
        }

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
                .map(w -> new WordViewModel(w, eligibilities, modes, progressMap.get(w.wordId().value())))
                .toList();
    }

    /**
     * Builds a new eligibility list from {@code existing} with the single {@code updated} entry
     * replacing any prior entry for the same (wordId, mode) pair.
     */
    private static List<ModeEligibility> withAppliedChange(
            List<ModeEligibility> existing, WordId wordId, String mode, ModeEligibility updated) {
        var result = new java.util.ArrayList<ModeEligibility>(existing.size() + 1);
        for (var me : existing) {
            if (!(me.wordId().equals(wordId) && me.mode().equals(mode))) {
                result.add(me);
            }
        }
        result.add(updated);
        return List.copyOf(result);
    }

    /**
     * View model for a dictionary word that exposes per-mode eligibility flags for Thymeleaf rendering.
     */
    public static final class WordViewModel {

        private final Word word;
        private final Map<String, Boolean> modeEnabled;
        private final @Nullable Integer progressPercent;

        public WordViewModel(Word word, List<ModeEligibility> eligibilities, List<String> modes,
                             @Nullable Integer progressPercent) {
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
            this.progressPercent = progressPercent;
        }

        public WordViewModel(Word word, List<ModeEligibility> eligibilities, List<String> modes) {
            this(word, eligibilities, modes, null);
        }

        public Word getWord() {
            return word;
        }

        public Map<String, Boolean> getModeEnabled() {
            return modeEnabled;
        }

        public boolean getModeEnabledOrDefault(String mode) {
            Boolean val = modeEnabled.get(mode);
            return val == null || val;
        }

        public @Nullable Integer getProgressPercent() {
            return progressPercent;
        }
    }
}
