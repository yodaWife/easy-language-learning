package com.yodawife.easyll.repository.contract;

import com.yodawife.easyll.repository.ScoreWriteRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Shared contract tests that every ScoreWriteRepository implementation must pass.
 *
 * <p>Defined as an interface with {@code default} test methods so that a single concrete
 * class can implement both this contract and {@link ScoreReadRepositoryContractTest},
 * since Java does not permit extending two abstract classes.
 */
public interface ScoreWriteRepositoryContractTest {

    ScoreWriteRepository createWriteRepository();

    @Test
    @DisplayName("appendAttempt succeeds without throwing")
    default void appendAttempt_succeedsWithoutThrowing() {
        var repository = createWriteRepository();
        assertThatCode(() -> repository.appendAttempt("user-1", "pair-1", "match", "S"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("flush does not throw")
    default void flush_doesNotThrow() {
        var repository = createWriteRepository();
        assertThatCode(repository::flush).doesNotThrowAnyException();
    }
}
