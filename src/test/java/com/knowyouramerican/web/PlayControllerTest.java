package com.knowyouramerican.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowyouramerican.puzzle.PuzzleRepository;
import com.knowyouramerican.puzzle.Question;
import com.knowyouramerican.support.TempDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PlayControllerTest {

    static final LocalDate DAY = LocalDate.of(2026, 3, 17);

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired GameViews games;
    @Autowired PuzzleRepository puzzles;

    /** Generates the day on demand (scheduler is off in tests). */
    List<Question> questions() {
        return games.questions(DAY).orElseThrow();
    }

    @Test
    void homeAndDirectVisitsRenderTheShellThatLoadsOverHtmx() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-get=\"/play/today\"")))
                .andExpect(content().string(containsString("KYA.answersFor")))
                .andExpect(content().string(containsString("/img/bg/")));
        mvc.perform(get("/play/" + DAY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-get=\"/play/2026-03-17\"")))
                .andExpect(content().string(containsString("data-date=\"2026-03-17\"")));
    }

    @Test
    void fragmentShowsFirstCardWithoutLeakingTheAnswer() throws Exception {
        String html = mvc.perform(get("/play/" + DAY).header("HX-Request", "true").param("a", "-,-,-,-,-"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tuesday, March 17")))
                .andExpect(content().string(containsString("From the archive")))
                .andExpect(content().string(containsString("0</span>/5 done")))
                .andExpect(content().string(not(containsString("<html"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("1</span> of 5");
        assertThat(html).doesNotContain("Nailed it").doesNotContain("✓");
        assertThat(html).contains("hx-post=\"/play/2026-03-17/q/1\"");
    }

    @Test
    void todayUsesThePlayersLocalDate() throws Exception {
        mvc.perform(get("/play/today").header("HX-Request", "true").param("today", DAY.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today")))
                .andExpect(content().string(containsString("Tuesday, March 17")));
    }

    @Test
    void correctAnswerTicksTheCircleAndEmitsTheStorageEvent() throws Exception {
        int right = questions().get(0).correctIndex();
        mvc.perform(post("/play/" + DAY + "/q/1").param("choice", String.valueOf(right)).param("a", "-,-,-,-,-"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", containsString("\"kya:answered\"")))
                .andExpect(header().string("HX-Trigger", containsString("\"correct\":true")))
                .andExpect(content().string(containsString("Nailed it!")))
                .andExpect(content().string(containsString("1</span>/5 done")))
                .andExpect(content().string(containsString("Next idiom")));
    }

    @Test
    void wrongAnswerShakesRevealsAndMarksMissed() throws Exception {
        int wrong = (questions().get(1).correctIndex() + 1) % 4;
        mvc.perform(post("/play/" + DAY + "/q/2").param("choice", String.valueOf(wrong)).param("a", "-,-,-,-,-"))
                .andExpect(status().isOk())
                .andExpect(header().string("HX-Trigger", containsString("\"correct\":false")))
                .andExpect(content().string(containsString("animate-shake")))
                .andExpect(content().string(containsString("Not quite")))
                .andExpect(content().string(containsString("Idiom 2: missed")));
    }

    @Test
    void oneShot_secondAnswerForSameCardIsIgnored() throws Exception {
        mvc.perform(post("/play/" + DAY + "/q/1").param("choice", "3").param("a", "0,-,-,-,-"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("HX-Trigger"));
    }

    @Test
    void allAnsweredShowsSummaryWithScoreAndShareText() throws Exception {
        List<Question> qs = questions();
        String a = qs.stream().map(q -> String.valueOf(q.correctIndex())).reduce((x, y) -> x + "," + y).orElseThrow();
        mvc.perform(get("/play/" + DAY).header("HX-Request", "true").param("a", a))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your result")))
                .andExpect(content().string(containsString("Star-spangled")))
                .andExpect(content().string(containsString("🟩🟩🟩🟩🟩 5/5")))
                .andExpect(content().string(containsString("5</span>/5 done")));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(get("/play/2099-01-01")).andExpect(status().isNotFound());
        mvc.perform(get("/play/2025-12-31").header("HX-Request", "true")).andExpect(status().isNotFound());
        mvc.perform(get("/play/nonsense")).andExpect(status().isNotFound());
        mvc.perform(post("/play/" + DAY + "/q/6").param("choice", "0")).andExpect(status().isBadRequest());
        mvc.perform(post("/play/" + DAY + "/q/1").param("choice", "4")).andExpect(status().isBadRequest());
    }

    @Test
    void answersParsingIsLenient() {
        assertThat(Answers.parse("2,0,-,x,9").toString()).isEqualTo("2,0,-,-,-");
        assertThat(Answers.parse(null).answeredCount()).isZero();
        assertThat(Answers.parse("1,1,1,1,1").firstOpen()).isZero();
        assertThat(GameViews.nextOpen(Answers.parse("0,-,0,-,0"), 2)).isEqualTo(4);
        assertThat(GameViews.nextOpen(Answers.parse("0,-,0,0,0"), 5)).isEqualTo(2);
    }
}
