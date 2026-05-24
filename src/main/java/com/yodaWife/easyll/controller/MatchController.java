package com.yodawife.easyll.controller;

import com.yodawife.easyll.config.DictionaryProperties;
import com.yodawife.easyll.domain.AttemptResult;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.service.MatchGameApplicationService;
import com.yodawife.easyll.service.SessionStore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
public class MatchController {

    private static final String SESSION_ID_ATTR = "sessionId";
    private static final String BOARD_ATTR = "currentBoard";

    private final SessionStore sessionStore;
    private final MatchGameApplicationService matchGameApplicationService;
    private final DictionaryProperties dictionaryProperties;

    public MatchController(SessionStore sessionStore,
                           MatchGameApplicationService matchGameApplicationService,
                           DictionaryProperties dictionaryProperties) {
        this.sessionStore = sessionStore;
        this.matchGameApplicationService = matchGameApplicationService;
        this.dictionaryProperties = dictionaryProperties;
    }

    @GetMapping("/match")
    public String matchPage(HttpSession httpSession, Model model) {
        String sessionId = (String) httpSession.getAttribute(SESSION_ID_ATTR);
        Optional<MatchSession> sessionOpt = sessionStore.get(sessionId);

        if (sessionOpt.isEmpty() || !"match".equals(sessionOpt.get().getMode())) {
            return "redirect:/";
        }

        MatchSession session = sessionOpt.get();
        var languageCode = (String) httpSession.getAttribute("languageCode");
        if (languageCode == null) {
            languageCode = dictionaryProperties.getPrimaryLanguageCode();
        }
        MatchBoard board = matchGameApplicationService.generateBoard(languageCode);
        httpSession.setAttribute(BOARD_ATTR, board);

        populateModel(model, session, board, null);
        return "match";
    }

    @PostMapping("/match/attempt")
    public @Nullable String attempt(
            @RequestParam String fromWord,
            @RequestParam String toWord,
            HttpSession httpSession,
            HttpServletResponse response,
            Model model) {

        String sessionId = (String) httpSession.getAttribute(SESSION_ID_ATTR);
        Optional<MatchSession> sessionOpt = sessionStore.get(sessionId);

        if (sessionOpt.isEmpty()) {
            response.setHeader("HX-Redirect", "/");
            return null;
        }

        MatchSession session = sessionOpt.get();
        MatchBoard board = (MatchBoard) httpSession.getAttribute(BOARD_ATTR);

        var languageCode = (String) httpSession.getAttribute("languageCode");
        if (languageCode == null) {
            languageCode = dictionaryProperties.getPrimaryLanguageCode();
        }

        if (board == null) {
            board = matchGameApplicationService.generateBoard(languageCode);
            httpSession.setAttribute(BOARD_ATTR, board);
        }

        AttemptResult result = matchGameApplicationService.processAttempt(session, board, fromWord, toWord);
        matchGameApplicationService.recordAttempt(sessionId, fromWord, toWord, result);

        if (result.sessionComplete()) {
            String message = matchGameApplicationService.finaliseSession(session);
            httpSession.setAttribute("resultMessage", message);
            httpSession.setAttribute("resultSession", session);
            response.setHeader("HX-Redirect", "/match/result");
            return null;
        }

        MatchBoard nextBoard = matchGameApplicationService.computeNextBoard(board, result, fromWord, toWord, languageCode);
        httpSession.setAttribute(BOARD_ATTR, nextBoard);

        populateModel(model, session, nextBoard, result);
        return "fragments/board :: board";
    }

    @GetMapping("/match/result")
    public String result(HttpSession httpSession, Model model) {
        MatchSession session = (MatchSession) httpSession.getAttribute("resultSession");
        String message = (String) httpSession.getAttribute("resultMessage");

        if (session == null) {
            return "redirect:/";
        }

        sessionStore.remove(session.getSessionId());

        model.addAttribute("successes", session.getSuccesses());
        model.addAttribute("failures", session.getFailures());
        model.addAttribute("successRate", session.successRatePercent());
        model.addAttribute("resultMessage", message);
        return "match/result";
    }

    private void populateModel(Model model, MatchSession session, MatchBoard board,
                               @Nullable AttemptResult lastResult) {
        model.addAttribute("board", board);
        model.addAttribute("attempts", session.getSuccesses());
        model.addAttribute("maxAttempts", session.getMaxAttempts());
        if (lastResult != null) {
            model.addAttribute("lastCorrect", lastResult.correct());
        }
    }
}
