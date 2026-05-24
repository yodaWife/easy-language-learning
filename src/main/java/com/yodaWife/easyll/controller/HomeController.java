package com.yodawife.easyll.controller;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.repository.ScoreRepository;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DataSnapshot;
import com.yodawife.easyll.service.SessionStore;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

    private final DataHealthService dataHealthService;
    private final SessionStore sessionStore;
    private final ScoreRepository scoreRepository;
    private final DictionaryProperties dictionaryProperties;

    public HomeController(DataHealthService dataHealthService,
                          SessionStore sessionStore,
                          ScoreRepository scoreRepository,
                          DictionaryProperties dictionaryProperties) {
        this.dataHealthService = dataHealthService;
        this.sessionStore = sessionStore;
        this.scoreRepository = scoreRepository;
        this.dictionaryProperties = dictionaryProperties;
    }

    @GetMapping("/")
    public String home(Model model) {
        DataSnapshot snapshot = dataHealthService.snapshot();
        model.addAttribute("wordsHealthy", snapshot.wordsHealthy());
        model.addAttribute("scoresHealthy", snapshot.scoresHealthy());
        model.addAttribute("wordErrors", snapshot.wordErrors());
        model.addAttribute("scoreErrors", snapshot.scoreErrors());
        model.addAttribute("nicknames", List.copyOf(scoreRepository.knownUsers()));
        model.addAttribute("languages", dataHealthService.availableLanguages());
        model.addAttribute("primaryLanguage", dictionaryProperties.getPrimaryLanguageCode());
        return "index";
    }

    @PostMapping("/session/start")
    public String startSession(
            @RequestParam(required = false) String nickname,
            @RequestParam String mode,
            @RequestParam(required = false) String languageCode,
            HttpSession httpSession) {

        if (!dataHealthService.snapshot().wordsHealthy()) {
            return "redirect:/";
        }

        if (!"flashcards".equals(mode) && !"match".equals(mode)) {
            return "redirect:/";
        }

        List<String> available = dataHealthService.availableLanguages();
        String resolvedLanguage;
        if (languageCode == null || languageCode.isBlank() || !available.contains(languageCode)) {
            resolvedLanguage = dictionaryProperties.getPrimaryLanguageCode();
        } else {
            resolvedLanguage = languageCode;
        }
        httpSession.setAttribute("languageCode", resolvedLanguage);

        MatchSession session = sessionStore.create(nickname, mode);
        httpSession.setAttribute("sessionId", session.getSessionId());

        return switch (mode) {
            case "flashcards" -> "redirect:/flashcards";
            case "match" -> "redirect:/match";
            default -> "redirect:/";
        };
    }
}
