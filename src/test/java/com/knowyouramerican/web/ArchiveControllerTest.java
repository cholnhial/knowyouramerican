package com.knowyouramerican.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.knowyouramerican.puzzle.PuzzleCalendar;
import com.knowyouramerican.support.TempDatabase;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ArchiveControllerTest {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        TempDatabase.register(registry);
    }

    @Autowired MockMvc mvc;

    @Test
    void monthPageListsPlayableDaysWithPickers() throws Exception {
        mvc.perform(get("/archive").param("year", "2026").param("month", "3"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Every day so far")))
                .andExpect(content().string(containsString("data-kya-day=\"2026-03-01\"")))
                .andExpect(content().string(containsString("data-kya-day=\"2026-03-31\"")))
                .andExpect(content().string(containsString("Random day")))
                .andExpect(content().string(containsString("<option value=\"3\" selected=\"selected\">March</option>")));
    }

    @Test
    void htmxMonthChangeReturnsOnlyTheCalendar() throws Exception {
        mvc.perform(get("/archive").param("year", "2026").param("month", "2")
                        .header("HX-Request", "true").header("HX-Target", "calendar"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"calendar\"")))
                .andExpect(content().string(containsString("data-kya-day=\"2026-02-28\"")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void outOfRangeMonthsAreClampedAndFutureDaysAreNotLinks() throws Exception {
        mvc.perform(get("/archive").param("year", "2019").param("month", "5"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-kya-day=\"2026-01-01\"")));
        LocalDate tomorrow = LocalDate.now(java.time.ZoneOffset.UTC).plusDays(1);
        mvc.perform(get("/archive").param("year", String.valueOf(tomorrow.getYear()))
                        .param("month", String.valueOf(tomorrow.getMonthValue())))
                .andExpect(content().string(not(containsString("data-kya-day=\"" + tomorrow + "\""))));
    }

    @Test
    void randomRedirectsToAPlayableDay() throws Exception {
        for (int i = 0; i < 20; i++) {
            String target = mvc.perform(get("/archive/random"))
                    .andExpect(status().is3xxRedirection())
                    .andReturn().getResponse().getRedirectedUrl();
            LocalDate day = LocalDate.parse(target.substring("/play/".length()));
            assertThat(day).isBetween(PuzzleCalendar.FIRST_DAY, LocalDate.now(java.time.ZoneOffset.UTC));
        }
    }

    @Test
    void monthGridIsSundayFirst() {
        MonthView m = MonthView.of(YearMonth.of(2026, 11), PuzzleCalendar.FIRST_DAY,
                LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 21));
        // Nov 1, 2026 is a Sunday
        assertThat(m.weeks().getFirst().getFirst().dayOfMonth()).isEqualTo(1);
        assertThat(m.playableCount()).isEqualTo(21);
        assertThat(m.next()).isNull();
        assertThat(m.previous()).isEqualTo(YearMonth.of(2026, 10));
    }
}
