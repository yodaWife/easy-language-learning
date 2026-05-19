package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.AttemptResult;
import com.yodawife.easyll.domain.MatchBoard;
import com.yodawife.easyll.domain.MatchSession;
import org.springframework.stereotype.Service;

@Service
public class MatchSessionService {

    private final MatchBoardGenerator boardGenerator;

    public MatchSessionService(MatchBoardGenerator boardGenerator) {
        this.boardGenerator = boardGenerator;
    }

    /**
     * Generate a fresh board for the current session.
     */
    public MatchBoard generateBoard() {
        return boardGenerator.generate();
    }

    /**
     * Process a single match attempt.
     * A correct attempt is when fromWord and toWord form a real pair on the board.
     * Every attempt (correct or not) is counted.
     *
     * @param session  the active MatchSession
     * @param board    the current MatchBoard being played
     * @param fromWord the dragged word
     * @param toWord   the target word
     * @return AttemptResult with correct/incorrect and whether session is now complete
     */
    public AttemptResult processAttempt(MatchSession session, MatchBoard board, String fromWord, String toWord) {
        boolean correct = isCorrectPair(board, fromWord, toWord);

        if (correct) {
            session.recordSuccess();
        } else {
            session.recordFailure();
        }

        return new AttemptResult(correct, session.isComplete(), session.getAttempts());
    }

    /**
     * Checks whether fromWord and toWord form a valid pair on the given board.
     * Comparison is case-sensitive and exact (matches what was parsed from CSV).
     */
    private boolean isCorrectPair(MatchBoard board, String fromWord, String toWord) {
        return board.pairs().stream()
                .anyMatch(entry -> entry.fromWord().equals(fromWord) && entry.toWord().equals(toWord));
    }

    /**
     * Result message thresholds:
     * 100%   → "You did it!"
     * 85-99% → "Almost!"
     * <85%   → "Let's practice some more!"
     */
    public String resultMessage(MatchSession session) {
        int rate = session.successRatePercent();
        if (rate == 100) return "You did it!";
        if (rate >= 85)  return "Almost!";
        return "Let's practice some more!";
    }
}
