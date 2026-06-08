package com.yodawife.easyll.controller;

import com.yodawife.easyll.domain.ActiveUserContext;
import com.yodawife.easyll.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/account")
public class AccountController {

    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Returns the account panel fragment with the list of known users and the active user.
     */
    @GetMapping("/panel")
    public String panel(Model model, HttpSession session) {
        model.addAttribute("users", accountService.findAll());
        model.addAttribute("activeUser", accountService.resolveActiveUser(session));
        return "fragments/account-panel :: panel";
    }

    /**
     * Signs in with the given display name.
     * If displayName is blank, returns the status fragment with the current context unchanged.
     */
    @PostMapping("/sign-in")
    public String signIn(@RequestParam(name = "displayName", required = false) @Nullable String displayName,
                         HttpSession session,
                         Model model) {
        if (displayName == null || displayName.isBlank()) {
            log.debug("Sign-in attempted with blank displayName; returning current context.");
            model.addAttribute("activeUser", accountService.resolveActiveUser(session));
        } else {
            var ctx = accountService.signIn(displayName, session);
            model.addAttribute("activeUser", ctx);
        }
        return "fragments/account-status :: status";
    }

    /**
     * Signs out the current user.
     */
    @PostMapping("/sign-out")
    public String signOut(HttpSession session, Model model) {
        accountService.signOut(session);
        model.addAttribute("activeUser", ActiveUserContext.anonymous());
        return "fragments/account-status :: status";
    }

    /**
     * Returns the account status fragment for the current session.
     */
    @GetMapping("/status")
    public String status(HttpSession session, Model model) {
        model.addAttribute("activeUser", accountService.resolveActiveUser(session));
        return "fragments/account-status :: status";
    }
}
