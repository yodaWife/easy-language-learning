package com.yodawife.easyll.service;

import org.springframework.stereotype.Service;

/**
 * Application service that wraps the data reload use case.
 * Delegates to {@link DataHealthService} and exposes the resulting snapshot.
 */
@Service
public class DataReloadApplicationService {

    private final DataHealthService dataHealthService;

    public DataReloadApplicationService(DataHealthService dataHealthService) {
        this.dataHealthService = dataHealthService;
    }

    /**
     * Trigger a data reload and return the resulting snapshot.
     *
     * @return the {@link DataSnapshot} reflecting the state after reload
     */
    public DataSnapshot reload() {
        dataHealthService.reload();
        return dataHealthService.snapshot();
    }

    /**
     * Return the current data snapshot without triggering a reload.
     *
     * @return the current {@link DataSnapshot}
     */
    public DataSnapshot snapshot() {
        return dataHealthService.snapshot();
    }
}
