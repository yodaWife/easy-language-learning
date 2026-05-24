package com.yodawife.easyll.service;

import com.yodawife.easyll.config.DictionaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Discovers language subdirectories under the configured dictionary root path.
 *
 * <p>Supports both {@code classpath:} prefixed paths and plain filesystem paths.
 * All error conditions (missing root, permission failures) are handled gracefully
 * by logging a warning and returning empty results rather than throwing.
 */
@Service
public class DictionaryDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DictionaryDiscoveryService.class);
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String WORDS_CSV = "words.csv";
    private static final String MODE_ELIGIBILITY_CSV = "mode-eligibility.csv";

    private final DictionaryProperties dictionaryProperties;

    public DictionaryDiscoveryService(DictionaryProperties dictionaryProperties) {
        this.dictionaryProperties = dictionaryProperties;
    }

    /**
     * Scans the configured root path for immediate language subdirectories.
     *
     * @return unmodifiable map of language code to its folder {@link Path};
     *         empty map if the root is missing, not a directory, or unreadable
     */
    public Map<String, Path> discoverLanguages() {
        Path root;
        try {
            root = resolveRootPath();
        } catch (IOException e) {
            log.warn("Failed to resolve dictionary root path '{}': {}", dictionaryProperties.getRootPath(), e.getMessage());
            return Map.of();
        }

        if (!Files.exists(root)) {
            log.warn("Dictionary root path does not exist: {}", root);
            return Map.of();
        }

        if (!Files.isDirectory(root)) {
            log.warn("Dictionary root path is not a directory: {}", root);
            return Map.of();
        }

        var languages = new HashMap<String, Path>();
        try (var stream = Files.list(root)) {
            stream.filter(path -> {
                try {
                    return Files.isDirectory(path);
                } catch (SecurityException e) {
                    log.warn("Cannot access path '{}': {}", path, e.getMessage());
                    return false;
                }
            }).forEach(languageFolder -> {
                var fileName = languageFolder.getFileName();
                if (fileName == null) {
                    log.warn("Skipping language folder with null filename: {}", languageFolder);
                    return;
                }
                languages.put(fileName.toString(), languageFolder);
            });
        } catch (IOException e) {
            log.warn("Failed to list root dictionary directory '{}': {}", root, e.getMessage());
            return Map.of();
        }

        return Collections.unmodifiableMap(languages);
    }

    /**
     * Checks whether a language folder contains the required CSV files.
     *
     * @param languageFolder path to the language subdirectory
     * @return {@code true} if both {@code words.csv} and {@code mode-eligibility.csv} are present
     */
    public boolean hasRequiredFiles(Path languageFolder) {
        return Files.exists(languageFolder.resolve(WORDS_CSV))
                && Files.exists(languageFolder.resolve(MODE_ELIGIBILITY_CSV));
    }

    private Path resolveRootPath() throws IOException {
        var rootPath = dictionaryProperties.getRootPath();
        if (rootPath.startsWith(CLASSPATH_PREFIX)) {
            var classpathPath = rootPath.substring(CLASSPATH_PREFIX.length());
            var resource = new ClassPathResource(classpathPath);
            try {
                return Path.of(resource.getURL().toURI());
            } catch (URISyntaxException e) {
                throw new IOException("Failed to convert classpath URL to URI: " + e.getMessage(), e);
            }
        }
        return Path.of(rootPath);
    }
}
