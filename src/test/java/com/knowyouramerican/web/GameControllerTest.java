package com.knowyouramerican.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowyouramerican.support.TempDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class GameControllerTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Test
    void homeRendersShellWithBackgroundAndLoader() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"game\"")))
                .andExpect(content().string(containsString("data-preloaded=\"false\"")))
                .andExpect(content().string(containsString("/img/bg/")))
                .andExpect(content().string(containsString("/js/game.js")));
    }

    @Test
    void htmxPlayRequestReturnsOnlyTheGameFragment() throws Exception {
        mvc.perform(get("/play/2026-01-01").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thursday, January 1")))
                .andExpect(content().string(containsString("0</span>/5 done")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void directPlayVisitRendersFullPage() throws Exception {
        mvc.perform(get("/play/2026-01-01"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html")))
                .andExpect(content().string(containsString("data-preloaded=\"true\"")))
                .andExpect(content().string(containsString("Thursday, January 1")));
    }

    @Test
    void futureAndPreLaunchAndGarbageDatesAreNotFound() throws Exception {
        mvc.perform(get("/play/2099-01-01")).andExpect(status().isNotFound());
        mvc.perform(get("/play/2025-12-31")).andExpect(status().isNotFound());
        mvc.perform(get("/play/not-a-date")).andExpect(status().isNotFound());
    }

    @Test
    void demoCorrectAnswerTicksFirstCircle() throws Exception {
        mvc.perform(post("/play/demo/answer").param("choice", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nailed it!")))
                .andExpect(content().string(containsString("hx-swap-oob=\"true\"")))
                .andExpect(content().string(containsString("1</span>/5 done")));
    }

    @Test
    void demoWrongAnswerShakesAndRevealsAnswer() throws Exception {
        mvc.perform(post("/play/demo/answer").param("choice", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("animate-shake")))
                .andExpect(content().string(containsString("Not quite")))
                .andExpect(content().string(containsString("Idiom 1: missed")));
    }

    @Test
    void demoChoiceOutOfRangeIsBadRequest() throws Exception {
        mvc.perform(post("/play/demo/answer").param("choice", "7")).andExpect(status().isBadRequest());
    }
}
