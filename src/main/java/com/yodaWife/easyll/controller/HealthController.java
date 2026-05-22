package com.yodawife.easyll.controller;

import com.yodawife.easyll.service.DataReloadApplicationService;
import com.yodawife.easyll.service.DataSnapshot;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class HealthController {

    private final DataReloadApplicationService dataReloadApplicationService;

    public HealthController(DataReloadApplicationService dataReloadApplicationService) {
        this.dataReloadApplicationService = dataReloadApplicationService;
    }

    @GetMapping("/health/data")
    public String dataHealth(Model model) {
        DataSnapshot snapshot = dataReloadApplicationService.snapshot();
        model.addAttribute("wordsHealthy", snapshot.wordsHealthy());
        model.addAttribute("scoresHealthy", snapshot.scoresHealthy());
        model.addAttribute("wordErrors", snapshot.wordErrors());
        model.addAttribute("scoreErrors", snapshot.scoreErrors());
        return "health/data";
    }

    @PostMapping("/admin/data/reload")
    public String reload(@RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                         Model model) {
        DataSnapshot snapshot = dataReloadApplicationService.reload();
        if (htmxRequest != null) {
            model.addAttribute("wordsHealthy", snapshot.wordsHealthy());
            model.addAttribute("scoresHealthy", snapshot.scoresHealthy());
            model.addAttribute("wordErrors", snapshot.wordErrors());
            model.addAttribute("scoreErrors", snapshot.scoreErrors());
            return "fragments/health-banner :: healthBanner";
        }
        return "redirect:/";
    }
}
