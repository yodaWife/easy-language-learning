package com.yodawife.easyll.service;

import com.yodawife.easyll.domain.WordId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Records structured audit log entries for dictionary modification events.
 */
@Service
public class DictionaryAuditLogService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryAuditLogService.class);

    /**
     * Logs a change to the {@code globalEnabled} flag of a word.
     *
     * @param languageCode the language the word belongs to
     * @param wordId       the identifier of the word
     * @param oldValue     the previous value of the flag
     * @param newValue     the new value of the flag
     */
    public void logGlobalToggle(String languageCode, WordId wordId, boolean oldValue, boolean newValue) {
        log.info("[DICT-AUDIT] language={} wordId={} field=globalEnabled old={} new={} timestamp={}",
                languageCode, wordId.value(), oldValue, newValue, Instant.now());
    }

    /**
     * Logs a change to the {@code modeEnabled} flag of a word for a specific mode.
     *
     * @param languageCode the language the word belongs to
     * @param wordId       the identifier of the word
     * @param mode         the mode whose eligibility changed
     * @param oldValue     the previous value of the flag
     * @param newValue     the new value of the flag
     */
    public void logModeToggle(String languageCode, WordId wordId, String mode, boolean oldValue, boolean newValue) {
        log.info("[DICT-AUDIT] language={} wordId={} mode={} field=modeEnabled old={} new={} timestamp={}",
                languageCode, wordId.value(), mode, oldValue, newValue, Instant.now());
    }
}
