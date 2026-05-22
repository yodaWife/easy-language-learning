package com.yodawife.easyll.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataReloadApplicationServiceTest {

    private final DataHealthService dataHealthService = mock(DataHealthService.class);
    private final DataReloadApplicationService service = new DataReloadApplicationService(dataHealthService);

    @Test
    @DisplayName("reload delegates to DataHealthService.reload() and returns the resulting snapshot")
    void reloadDelegatesToDataHealthServiceAndReturnsSnapshot() {
        var snapshot = DataSnapshot.degraded(List.of("load error"));
        when(dataHealthService.snapshot()).thenReturn(snapshot);

        var result = service.reload();

        verify(dataHealthService).reload();
        verify(dataHealthService).snapshot();
        assertThat(result).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("snapshot returns the current DataHealthService snapshot without triggering a reload")
    void snapshotReturnsDelegatedSnapshotWithoutReload() {
        var snapshot = DataSnapshot.degraded(List.of());
        when(dataHealthService.snapshot()).thenReturn(snapshot);

        var result = service.snapshot();

        assertThat(result).isEqualTo(snapshot);
        verify(dataHealthService).snapshot();
    }
}
