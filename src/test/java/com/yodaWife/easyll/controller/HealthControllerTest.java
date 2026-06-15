package com.yodawife.easyll.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest extends AbstractControllerIntegrationTest {

    @Test
    void healthDataPageLoads() throws Exception {
        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/data"))
                .andExpect(model().attributeExists("wordsHealthy", "scoresHealthy", "wordErrors", "scoreErrors"));
    }

    @Test
    void healthDataShowsHealthyWhenDataValid() throws Exception {
        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", true))
                .andExpect(model().attribute("scoresHealthy", true));
    }

    @Test
    @DisplayName("POST /admin/data/reload without credentials returns 401")
    void reloadEndpointReturns401WithoutCredentials() throws Exception {
        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reloadEndpointRedirectsToHome() throws Exception {
        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reloadEndpointRestoresHealthyStateWithValidData() throws Exception {
        // Trigger reload (data is already valid)
        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection());

        // Verify home page shows healthy state after reload
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("wordsHealthy", true))
                .andExpect(model().attribute("scoresHealthy", true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reloadEndpointReturnsBannerFragmentForHtmxRequest() throws Exception {
        mockMvc.perform(post("/admin/data/reload")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/health-banner :: healthBanner"));
    }
}
