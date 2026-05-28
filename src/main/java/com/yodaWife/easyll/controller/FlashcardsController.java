package com.yodawife.easyll.controller;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.DictionaryOperationResult;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.domain.Word;
import com.yodawife.easyll.domain.WordEntry;
import com.yodawife.easyll.domain.WordId;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DictionaryEditService;
import com.yodawife.easyll.service.FlashcardService;
import com.yodawife.easyll.service.SessionStore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Controller
public class FlashcardsController {

    private static final Logger log = LoggerFactory.getLogger(FlashcardsController.class);
    private static final String SESSION_FLASHCARD_INDEX = "flashcardIndex";
    private static final String SESSION_LEARNED_IDS = "learnedWordIds";

    private final FlashcardService flashcardService;
    private final SessionStore sessionStore;
    private final DataHealthService dataHealthService;
    private final DictionaryProperties dictionaryProperties;
    private final DictionaryEditService dictionaryEditService;

    public FlashcardsController(FlashcardService flashcardService,
                                SessionStore sessionStore,
                                DataHealthService dataHealthService,
                                DictionaryProperties dictionaryProperties,
                                DictionaryEditService dictionaryEditService) {
        this.flashcardService = flashcardService;
        this.sessionStore = sessionStore;
        this.dataHealthService = dataHealthService;
        this.dictionaryProperties = dictionaryProperties;
        this.dictionaryEditService = dictionaryEditService;
    }

    @GetMapping("/flashcards")
    public String flashcardsPage(HttpSession httpSession, Model model) {
        var sessionId = (String) httpSession.getAttribute("sessionId");
        Optional<MatchSession> session = sessionStore.get(sessionId);

        if (session.isEmpty() || !"flashcards".equals(session.get().getMode())) {
            return "redirect:/";
        }

        var snapshot = dataHealthService.snapshot();
        if (!snapshot.wordsHealthy()) {
            return "redirect:/";
        }

        var languageCode = resolveLanguageCode(httpSession);
        var bundleOpt = snapshot.getLanguageBundle(languageCode);
        int totalCards = bundleOpt.map(b -> b.words().size()).orElse(0);

        // Reset game state for a fresh session
        httpSession.setAttribute(SESSION_FLASHCARD_INDEX, 1);
        httpSession.setAttribute(SESSION_LEARNED_IDS, new HashSet<String>());

        var word = flashcardService.randomWord(languageCode, "flashcards", Set.of());
        var card = word.map(w -> new WordEntry(w.fromWord(), w.toWord(), w.example())).orElse(null);
        var cardWordId = word.map(w -> w.wordId().value()).orElse(null);

        var fromLang = bundleOpt.flatMap(b -> Optional.ofNullable(b.metadata()))
                .map(m -> m.fromLanguageName()).orElse(languageCode);
        var toLang = bundleOpt.flatMap(b -> Optional.ofNullable(b.metadata()))
                .map(m -> m.toLanguageName()).orElse("?");

        model.addAttribute("card", card);
        model.addAttribute("cardWordId", cardWordId);
        model.addAttribute("cardIndex", 1);
        model.addAttribute("totalCards", totalCards);
        model.addAttribute("fromLang", fromLang);
        model.addAttribute("toLang", toLang);
        return "flashcards";
    }

    @GetMapping("/flashcards/card")
    public @Nullable String cardPartial(
            HttpSession httpSession,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        var sessionId = (String) httpSession.getAttribute("sessionId");
        var session = sessionStore.get(sessionId);

        if (session.isEmpty() || !"flashcards".equals(session.get().getMode())) {
            if (hxRequest != null) {
                response.setHeader("HX-Redirect", "/");
                return null;
            }
            return "redirect:/";
        }

        var snapshot = dataHealthService.snapshot();
        var languageCode = resolveLanguageCode(httpSession);
        int totalCards = snapshot.getLanguageBundle(languageCode)
                .map(b -> b.words().size()).orElse(1);

        var storedIndex = (Integer) httpSession.getAttribute(SESSION_FLASHCARD_INDEX);
        int currentIndex = (storedIndex == null) ? 1 : storedIndex;
        int newIndex = currentIndex >= totalCards ? 1 : currentIndex + 1;
        httpSession.setAttribute(SESSION_FLASHCARD_INDEX, newIndex);

        var learnedIds = resolveLearnedIds(httpSession);
        var word = flashcardService.randomWord(languageCode, "flashcards", learnedIds);
        var card = word.map(w -> new WordEntry(w.fromWord(), w.toWord(), w.example())).orElse(null);
        var cardWordId = word.map(w -> w.wordId().value()).orElse(null);

        model.addAttribute("card", card);
        model.addAttribute("cardWordId", cardWordId);
        model.addAttribute("cardIndex", newIndex);
        model.addAttribute("totalCards", totalCards);
        return "fragments/card :: card";
    }

    @PostMapping("/flashcards/learned")
    public @Nullable String markLearned(
            @RequestParam(defaultValue = "") String wordId,
            HttpSession httpSession,
            HttpServletResponse response,
            @RequestHeader(value = "HX-Request", required = false) String hxRequest,
            Model model) {
        var sessionId = (String) httpSession.getAttribute("sessionId");
        var session = sessionStore.get(sessionId);

        if (session.isEmpty() || !"flashcards".equals(session.get().getMode())) {
            if (hxRequest != null) {
                response.setHeader("HX-Redirect", "/");
                return null;
            }
            return "redirect:/";
        }

        var snapshot = dataHealthService.snapshot();
        var languageCode = resolveLanguageCode(httpSession);
        int totalCards = snapshot.getLanguageBundle(languageCode)
                .map(b -> b.words().size()).orElse(1);

        var storedIndex = (Integer) httpSession.getAttribute(SESSION_FLASHCARD_INDEX);
        int currentIndex = (storedIndex == null) ? 1 : storedIndex;
        int newIndex = currentIndex >= totalCards ? 1 : currentIndex + 1;
        httpSession.setAttribute(SESSION_FLASHCARD_INDEX, newIndex);

        var learnedIds = resolveLearnedIds(httpSession);
        if (!wordId.isBlank()) {
            learnedIds.add(wordId);
            var persistResult = dictionaryEditService.setModeEnabled(
                    languageCode, new WordId(wordId), "flashcards", false);
            if (persistResult instanceof DictionaryOperationResult.Failure<?> f) {
                log.debug("Could not persist learned state for word '{}' in language '{}': {}",
                        wordId, languageCode, f.errorMessage());
            }
        }

        var word = flashcardService.randomWord(languageCode, "flashcards", learnedIds);
        var card = word.map(w -> new WordEntry(w.fromWord(), w.toWord(), w.example())).orElse(null);
        var cardWordId = word.map(w -> w.wordId().value()).orElse(null);

        model.addAttribute("card", card);
        model.addAttribute("cardWordId", cardWordId);
        model.addAttribute("cardIndex", newIndex);
        model.addAttribute("totalCards", totalCards);
        return "fragments/card :: card";
    }

    private String resolveLanguageCode(HttpSession httpSession) {
        var languageCode = (String) httpSession.getAttribute("languageCode");
        return languageCode != null ? languageCode : dictionaryProperties.getPrimaryLanguageCode();
    }

    @SuppressWarnings("unchecked")
    private static Set<String> resolveLearnedIds(HttpSession httpSession) {
        var raw = httpSession.getAttribute(SESSION_LEARNED_IDS);
        if (raw instanceof Set<?> existing) {
            return (Set<String>) existing;
        }
        var fresh = new HashSet<String>();
        httpSession.setAttribute(SESSION_LEARNED_IDS, fresh);
        return fresh;
    }
}
