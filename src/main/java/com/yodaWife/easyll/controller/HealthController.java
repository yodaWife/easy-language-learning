package com.yodawife.easyll.controller;

import com.yodawife.easyll.service.DataHealthService;
import com.yodawife.easyll.service.DataSnapshot;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class HealthController {

    private final DataHealthService dataHealthService;

    public HealthController(DataHealthService dataHealthService) {
        this.dataHealthService = dataHealthService;
    }

    @GetMapping("/health/data")
    public String dataHealth(Model model) {
        DataSnapshot snapshot = dataHealthService.snapshot();
        model.addAttribute("healthy", snapshot.healthy());
        model.addAttribute("errors", snapshot.errors());
        return "health/data";
    }

    @PostMapping("/admin/data/reload")
    public String reload(@RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                         Model model) {
        dataHealthService.reload();
        if (htmxRequest != null) {
            DataSnapshot snapshot = dataHealthService.snapshot();
            model.addAttribute("healthy", snapshot.healthy());
            model.addAttribute("errors", snapshot.errors());
            return "fragments/health-banner :: healthBanner";
        }
        return "redirect:/";
    }
}
