package com.yodawife.easyll.service;

import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link DataHealthService} after a successful data reload.
 * Listeners can react to the refreshed {@link DataSnapshot} being available.
 */
public class DataReloadedEvent extends ApplicationEvent {

    public DataReloadedEvent(Object source) {
        super(source);
    }
}
