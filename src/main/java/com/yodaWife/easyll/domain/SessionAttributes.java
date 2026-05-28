package com.yodawife.easyll.domain;

/**
 * Shared HttpSession attribute key constants.
 * Using typed constants prevents typo-driven bugs across controllers.
 */
public final class SessionAttributes {

    /** Key for {@link ActiveUserContext} stored in HttpSession. */
    public static final String ACTIVE_USER = "activeUser";

    private SessionAttributes() {}
}
