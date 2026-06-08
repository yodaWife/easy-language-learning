package com.yodawife.easyll.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.migration")
public class MigrationProperties {

    private boolean enabled = false;
    private boolean dryRun = true;
    private String errorsOutputPath = "./data/migration-errors.csv";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public String getErrorsOutputPath() { return errorsOutputPath; }
    public void setErrorsOutputPath(String errorsOutputPath) { this.errorsOutputPath = errorsOutputPath; }
}
