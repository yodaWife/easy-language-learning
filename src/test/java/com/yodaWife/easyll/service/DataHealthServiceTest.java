package com.yodawife.easyll.service;

import com.yodawife.easyll.repository.DictionaryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataHealthServiceTest {

    private final DictionaryRepository dictionaryRepository = mock(DictionaryRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    // @PostConstruct is not invoked in plain unit tests; initial snapshot is DataSnapshot.degraded("Data not yet loaded")
    private final DataHealthService service = new DataHealthService(dictionaryRepository, eventPublisher, jdbcTemplate);

    @Test
    @DisplayName("reportScoreWritePathError preserves wordsHealthy=false when word health was already degraded")
    void reportScoreWritePathError_preservesWordsHealthyFalseWhenAlreadyDegraded() {
        assertThat(service.snapshot().wordsHealthy()).isFalse();

        service.reportScoreWritePathError("score write failed");

        var snapshot = service.snapshot();
        assertThat(snapshot.wordsHealthy()).isFalse();
        assertThat(snapshot.scoresHealthy()).isFalse();
        assertThat(snapshot.scoreErrors()).containsExactly("score write failed");
    }

    @Test
    @DisplayName("reportScoreWritePathError preserves existing wordErrors when word health was already degraded")
    void reportScoreWritePathError_preservesWordErrorsFromDegradedState() {
        // Arrange: establish a known degraded state with specific wordErrors
        service.reportRuntimeError("word data corrupted");
        assertThat(service.snapshot().wordErrors()).containsExactly("word data corrupted");

        // Act
        service.reportScoreWritePathError("score write failed");

        // Assert
        var snapshot = service.snapshot();
        assertThat(snapshot.wordsHealthy()).isFalse();
        assertThat(snapshot.wordErrors()).containsExactly("word data corrupted");
        assertThat(snapshot.scoresHealthy()).isFalse();
        assertThat(snapshot.scoreErrors()).containsExactly("score write failed");
    }

    @Test
    @DisplayName("reportScoreWritePathError sets scoresHealthy=false and records the score error message")
    void reportScoreWritePathError_setsScoresUnhealthyWithErrorMessage() {
        service.reportScoreWritePathError("write error");

        var snapshot = service.snapshot();
        assertThat(snapshot.scoresHealthy()).isFalse();
        assertThat(snapshot.scoreErrors()).isEqualTo(List.of("write error"));
    }
}
