package com.yodawife.easyll.controller;

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

    public HomeController(DataHealthService dataHealthService,
                          SessionStore sessionStore,
                          ScoreRepository scoreRepository) {
        this.dataHealthService = dataHealthService;
        this.sessionStore = sessionStore;
        this.scoreRepository = scoreRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        DataSnapshot snapshot = dataHealthService.snapshot();
        model.addAttribute("healthy", snapshot.healthy());
        model.addAttribute("errors", snapshot.errors());
        model.addAttribute("nicknames", List.copyOf(scoreRepository.knownUsers()));
        return "index";
    }

    @PostMapping("/session/start")
    public String startSession(
            @RequestParam(required = false) String nickname,
            @RequestParam String mode,
            HttpSession httpSession) {

        if (!dataHealthService.snapshot().healthy()) {
            return "redirect:/";
        }

        if (!"flashcards".equals(mode) && !"match".equals(mode)) {
            return "redirect:/";
        }

        MatchSession session = sessionStore.create(nickname, mode);
        httpSession.setAttribute("sessionId", session.getSessionId());

        return switch (mode) {
            case "flashcards" -> "redirect:/flashcards";
            case "match" -> "redirect:/match";
            default -> "redirect:/";
        };
    }
}
