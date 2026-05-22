package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.AttemptResult;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchCard;
import com.yodawife.easyll.domain.MatchSession;
import com.yodawife.easyll.repository.ScoreRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application service that orchestrates the match game workflow.
 * Encapsulates board generation, attempt processing, board state transitions,
 * and session finalisation with optional score persistence.
 */
@Service
public class MatchGameApplicationService {

    private final MatchSessionService matchSessionService;
    private final ScoreRepository scoreRepository;
    private final ConcurrentHashMap<String, List<AttemptRecord>> pendingAttempts = new ConcurrentHashMap<>();

    public MatchGameApplicationService(MatchSessionService matchSessionService, ScoreRepository scoreRepository) {
        this.matchSessionService = matchSessionService;
        this.scoreRepository = scoreRepository;
    }

    /**
     * Generate a fresh match board.
     *
     * @return a new {@link MatchBoard}
     */
    public MatchBoard generateBoard() {
        return matchSessionService.generateBoard();
    }

    /**
     * Process a single match attempt and return the result.
     *
     * @param session  the active match session
     * @param board    the current board
     * @param fromWord the dragged word
     * @param toWord   the target word
     * @return the {@link AttemptResult} for this attempt
     */
    public AttemptResult processAttempt(MatchSession session, MatchBoard board, String fromWord, String toWord) {
        return matchSessionService.processAttempt(session, board, fromWord, toWord);
    }

    /**
     * Record an attempt in the internal buffer for the given session.
     *
     * @param sessionId the session identifier
     * @param fromWord  the dragged word
     * @param toWord    the target word
     * @param result    the result of the attempt
     */
    public void recordAttempt(String sessionId, String fromWord, String toWord, AttemptResult result) {
        pendingAttempts.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>())
                .add(new AttemptRecord(fromWord, toWord, result.correct()));
    }

    /**
     * Compute the next board state after an attempt.
     * <ul>
     *   <li>Correct and not all matched → mark the pair as matched</li>
     *   <li>Correct and all matched → generate a fresh board</li>
     *   <li>Incorrect → return the same board unchanged</li>
     * </ul>
     *
     * @param board    the current board
     * @param result   the result of the latest attempt
     * @param fromWord the dragged word
     * @param toWord   the target word
     * @return the next {@link MatchBoard} to display
     */
    public MatchBoard computeNextBoard(MatchBoard board, AttemptResult result, String fromWord, String toWord) {
        if (!result.correct()) {
            return board;
        }
        var pairId = MatchCard.buildPairId(fromWord, toWord);
        var updated = board.withMatched(pairId);
        return updated.allMatched() ? matchSessionService.generateBoard() : updated;
    }

    /**
     * Finalise the session: persist per-user scores from the internal attempt buffer if a nickname is
     * present, then return the result message. Clears the buffer for this session.
     *
     * @param session the completed match session
     * @return the result message string for display
     */
    public String finaliseSession(MatchSession session) {
        var sessionId = session.getSessionId();
        var attempts = pendingAttempts.remove(sessionId);
        var nickname = session.getNickname();
        if (nickname != null && attempts != null) {
            for (var attempt : attempts) {
                scoreRepository.appendAttempt(nickname, attempt.fromWord(), attempt.toWord(),
                        attempt.correct() ? "S" : "F");
            }
            scoreRepository.flush();
        }
        return matchSessionService.resultMessage(session);
    }

    /**
     * Return a result message based on the session success rate.
     *
     * @param session the completed match session
     * @return the result message string
     */
    public String resultMessage(MatchSession session) {
        return matchSessionService.resultMessage(session);
    }
}
