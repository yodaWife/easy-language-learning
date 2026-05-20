package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.AttemptResult;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchCard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.repository.ScoreRepository;
import com.yodawife.easyll.service.MatchSessionService;
import com.yodawife.easyll.service.SessionStore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Controller
public class MatchController {

    private static final String SESSION_ID_ATTR = "sessionId";
    private static final String BOARD_ATTR = "currentBoard";
    private static final String ATTEMPTS_ATTR = "sessionAttempts";

    private final SessionStore sessionStore;
    private final MatchSessionService matchSessionService;
    private final ScoreRepository scoreRepository;

    public MatchController(SessionStore sessionStore,
                           MatchSessionService matchSessionService,
                           ScoreRepository scoreRepository) {
        this.sessionStore = sessionStore;
        this.matchSessionService = matchSessionService;
        this.scoreRepository = scoreRepository;
    }

    @GetMapping("/match")
    public String matchPage(HttpSession httpSession, Model model) {
        String sessionId = (String) httpSession.getAttribute(SESSION_ID_ATTR);
        Optional<MatchSession> sessionOpt = sessionStore.get(sessionId);

        if (sessionOpt.isEmpty() || !"match".equals(sessionOpt.get().getMode())) {
            return "redirect:/";
        }

        MatchSession session = sessionOpt.get();
        MatchBoard board = matchSessionService.generateBoard();
        httpSession.setAttribute(BOARD_ATTR, board);
        httpSession.setAttribute(ATTEMPTS_ATTR, new ArrayList<String[]>());

        populateModel(model, session, board, null);
        return "match";
    }

    @SuppressWarnings("unchecked")
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

        if (board == null) {
            board = matchSessionService.generateBoard();
            httpSession.setAttribute(BOARD_ATTR, board);
        }

        AttemptResult result = matchSessionService.processAttempt(session, board, fromWord, toWord);

        // Track attempt for end-of-session score flush
        List<String[]> attempts = (List<String[]>) httpSession.getAttribute(ATTEMPTS_ATTR);
        if (attempts == null) {
            attempts = new ArrayList<>();
            httpSession.setAttribute(ATTEMPTS_ATTR, attempts);
        }
        attempts.add(new String[]{fromWord, toWord, result.correct() ? "S" : "F"});

        if (result.sessionComplete()) {
            // Persist per-user scores if nickname is present
            String nickname = session.getNickname();
            if (nickname != null) {
                for (var attempt : attempts) {
                    scoreRepository.appendAttempt(nickname, attempt[0], attempt[1], attempt[2]);
                }
                scoreRepository.flush();
            }

            httpSession.setAttribute("resultMessage", matchSessionService.resultMessage(session));
            httpSession.setAttribute("resultSession", session);
            httpSession.removeAttribute(ATTEMPTS_ATTR);
            response.setHeader("HX-Redirect", "/match/result");
            return null;
        }

        // On success mark the pair as matched; when all 5 matched generate a fresh board.
        // On failure return the same board unchanged.
        MatchBoard nextBoard;
        if (result.correct()) {
            String pairId = MatchCard.buildPairId(fromWord, toWord);
            MatchBoard updated = board.withMatched(pairId);
            nextBoard = updated.allMatched() ? matchSessionService.generateBoard() : updated;
        } else {
            nextBoard = board;
        }
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
