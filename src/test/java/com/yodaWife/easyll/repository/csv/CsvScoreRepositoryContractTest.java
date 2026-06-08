package com.yodawife.easyll.repository.csv;

import com.yodawife.easyll.repository.ScoreReadRepository;
import com.yodawife.easyll.repository.ScoreRepository;
import com.yodawife.easyll.repository.ScoreWriteRepository;
import com.yodawife.easyll.repository.contract.ScoreReadRepositoryContractTest;
import com.yodawife.easyll.repository.contract.ScoreWriteRepositoryContractTest;
import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DataSnapshot;
import com.yodawife.easyll.service.UserScoreService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvScoreRepositoryContractTest extends ScoreReadRepositoryContractTest implements ScoreWriteRepositoryContractTest {

    @TempDir
    @Nullable Path tempDir;

    @Nullable ScoreRepository scoreRepository;

    @Override
    protected ScoreReadRepository createRepository() {
        var mockHealth = mock(DataHealthService.class);
        var mockSnapshot = mock(DataSnapshot.class);
        when(mockHealth.snapshot()).thenReturn(mockSnapshot);
        when(mockSnapshot.healthy()).thenReturn(false);

        var scoreFile = Objects.requireNonNull(tempDir).resolve("scores.csv");
        var repo = new ScoreRepository(mockHealth, new UserScoreService(), scoreFile.toString());
        scoreRepository = repo;
        return repo;
    }

    @Override
    public ScoreWriteRepository createWriteRepository() {
        return Objects.requireNonNull(scoreRepository);
    }

    @Override
    protected void populateHistory(String userId, String pairId, String mode, List<String> entries) {
        var repo = Objects.requireNonNull(scoreRepository);
        for (var entry : entries) {
            repo.appendAttempt(userId, pairId, mode, entry);
        }
        repo.flush();
    }
}
