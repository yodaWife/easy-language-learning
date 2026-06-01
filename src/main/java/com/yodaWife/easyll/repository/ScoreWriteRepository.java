package com.yodawife.easyll.repository;

/**
 * Write contract for appending score attempts and persisting them.
 */
public interface ScoreWriteRepository {
    void appendAttempt(String userId, String pairId, String mode, String result);
    void flush();
}
