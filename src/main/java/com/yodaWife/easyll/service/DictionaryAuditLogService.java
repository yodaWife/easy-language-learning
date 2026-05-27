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

    /**
     * Logs an edit of the text fields (fromWord, toWord, example) of a word.
     *
     * @param languageCode the language the word belongs to
     * @param wordId       the identifier of the word
     * @param oldWord      the word before editing
     * @param newWord      the word after editing
     */
    public void logWordEdit(String languageCode, WordId wordId, com.yodawife.easyll.domain.Word oldWord,
                            com.yodawife.easyll.domain.Word newWord) {
        log.info("[DICT-AUDIT] language={} wordId={} action=edit from={}→{} to={}→{} example={}→{} timestamp={}",
                languageCode, wordId.value(),
                oldWord.fromWord(), newWord.fromWord(),
                oldWord.toWord(), newWord.toWord(),
                oldWord.example(), newWord.example(),
                Instant.now());
    }

    /**
     * Logs the addition of a new word.
     *
     * @param languageCode the language the word was added to
     * @param wordId       the identifier assigned to the new word
     * @param newWord      the newly created word
     */
    public void logWordAdd(String languageCode, WordId wordId, com.yodawife.easyll.domain.Word newWord) {
        log.info("[DICT-AUDIT] language={} wordId={} action=add from={} to={} example={} timestamp={}",
                languageCode, wordId.value(),
                newWord.fromWord(), newWord.toWord(), newWord.example(),
                Instant.now());
    }
}
