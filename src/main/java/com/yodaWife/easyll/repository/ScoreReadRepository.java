package com.yodawife.easyll.repository;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only contract for accessing score history data.
 */
public interface ScoreReadRepository {
    Map<String, List<String>> getHistoriesForUser(String userId);
    Set<String> knownUsers();
}
