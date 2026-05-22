package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.domain.WordEntry;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.FlashcardService;
import com.yodawife.easyll.service.SessionStore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Optional;

@Controller
public class FlashcardsController {

    private final FlashcardService flashcardService;
    private final SessionStore sessionStore;
    private final DataHealthService dataHealthService;

    public FlashcardsController(FlashcardService flashcardService,
                                SessionStore sessionStore,
                                DataHealthService dataHealthService) {
        this.flashcardService = flashcardService;
        this.sessionStore = sessionStore;
        this.dataHealthService = dataHealthService;
    }

    @GetMapping("/flashcards")
    public String flashcardsPage(HttpSession httpSession, Model model) {
        var sessionId = (String) httpSession.getAttribute("sessionId");
        Optional<MatchSession> session = sessionStore.get(sessionId);

        if (session.isEmpty() || !"flashcards".equals(session.get().getMode())) {
            return "redirect:/";
        }

        var snapshot = dataHealthService.snapshot();
        if (!snapshot.wordsHealthy() || snapshot.wordData() == null) {
            return "redirect:/";
        }

        int totalCards = snapshot.wordData().words().size();
        httpSession.setAttribute("flashcardIndex", 1);

        WordEntry card = flashcardService.randomCard()
                .orElse(null);

        model.addAttribute("card", card);
        model.addAttribute("cardIndex", 1);
        model.addAttribute("totalCards", totalCards);
        model.addAttribute("fromLang", snapshot.wordData().metadata().fromLanguageName());
        model.addAttribute("toLang", snapshot.wordData().metadata().toLanguageName());
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
        int totalCards = (snapshot.wordsHealthy() && snapshot.wordData() != null)
                ? snapshot.wordData().words().size()
                : 1;

        var storedIndex = (Integer) httpSession.getAttribute("flashcardIndex");
        int currentIndex = (storedIndex == null) ? 1 : storedIndex;
        int newIndex = currentIndex >= totalCards ? 1 : currentIndex + 1;
        httpSession.setAttribute("flashcardIndex", newIndex);

        WordEntry card = flashcardService.randomCard()
                .orElse(null);

        model.addAttribute("card", card);
        model.addAttribute("cardIndex", newIndex);
        model.addAttribute("totalCards", totalCards);
        return "fragments/card :: card";
    }
}
