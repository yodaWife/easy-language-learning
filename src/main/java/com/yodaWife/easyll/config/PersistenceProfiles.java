package com.yodawife.easyll.config;

/**
 * Spring profile name constants for persistence adapter selection.
 *
 * <p>Activate profile {@value #CSV} (default) to use CSV-backed adapters.
 * Activate profile {@value #DB} to use PostgreSQL-backed adapters.
 */
public final class PersistenceProfiles {

    public static final String CSV = "csv";
    public static final String DB = "db";

    private PersistenceProfiles() {}
}
