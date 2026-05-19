package com.yodawife.easyll.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = "app.scores.file-path=./scores-test.csv")
class HealthControllerTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void healthDataPageLoads() throws Exception {
        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(view().name("health/data"))
                .andExpect(model().attributeExists("healthy", "errors"));
    }

    @Test
    void healthDataShowsHealthyWhenDataValid() throws Exception {
        mockMvc.perform(get("/health/data"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("healthy", true));
    }

    @Test
    void reloadEndpointRedirectsToHome() throws Exception {
        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void reloadEndpointRestoresHealthyStateWithValidData() throws Exception {
        // Trigger reload (data is already valid)
        mockMvc.perform(post("/admin/data/reload"))
                .andExpect(status().is3xxRedirection());

        // Verify home page shows healthy state after reload
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("healthy", true));
    }

    @Test
    void reloadEndpointReturnsBannerFragmentForHtmxRequest() throws Exception {
        mockMvc.perform(post("/admin/data/reload")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/health-banner :: healthBanner"));
    }
}
