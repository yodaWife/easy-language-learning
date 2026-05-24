package com.yodawife.easyll.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides per-language write locks to prevent concurrent dictionary modifications
 * for the same language code.
 */
@Service
public class DictionaryWriteLock {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Returns the {@link ReentrantLock} for the given language code, creating one if absent.
     *
     * @param languageCode the language identifier
     * @return the lock associated with the language code
     */
    public ReentrantLock getLock(String languageCode) {
        return locks.computeIfAbsent(languageCode, k -> new ReentrantLock());
    }

    /**
     * Executes the given action while holding the write lock for the specified language.
     *
     * @param languageCode the language identifier
     * @param timeoutMs    maximum time in milliseconds to wait for the lock
     * @param action       the action to execute under the lock
     * @throws DictionaryLockTimeoutException if the lock cannot be acquired within the timeout
     * @throws InterruptedException           if the current thread is interrupted while waiting
     */
    public void executeWithLock(String languageCode, long timeoutMs, Runnable action)
            throws DictionaryLockTimeoutException, InterruptedException {
        var lock = getLock(languageCode);
        var acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        if (!acquired) {
            throw new DictionaryLockTimeoutException(languageCode);
        }
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Thrown when a write lock for a language cannot be acquired within the configured timeout.
     */
    public static final class DictionaryLockTimeoutException extends Exception {

        /**
         * @param languageCode the language code for which the lock timed out
         */
        public DictionaryLockTimeoutException(String languageCode) {
            super("Could not acquire write lock for language '" + languageCode + "' within timeout");
        }
    }
}
