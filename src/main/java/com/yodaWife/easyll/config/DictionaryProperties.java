package com.yodawife.easyll.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.dictionaries")
public class DictionaryProperties {

    @NotBlank
    private String primaryLanguageCode = "hun";

    private List<String> modes = List.of("flashcards", "match");

    public String getPrimaryLanguageCode() {
        return primaryLanguageCode;
    }

    public void setPrimaryLanguageCode(String primaryLanguageCode) {
        this.primaryLanguageCode = primaryLanguageCode;
    }

    public List<String> getModes() {
        return modes;
    }

    public void setModes(List<String> modes) {
        this.modes = modes;
    }
}
