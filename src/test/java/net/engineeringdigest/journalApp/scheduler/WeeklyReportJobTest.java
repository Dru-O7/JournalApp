package net.engineeringdigest.journalApp.scheduler;

import net.engineeringdigest.journalApp.IntegrationTest;
import net.engineeringdigest.journalApp.crypto.DataCipher;
import net.engineeringdigest.journalApp.entity.Sentiment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WeeklyReportJobTest extends IntegrationTest {
    @Autowired
    WeeklyReportJob job;
    @Autowired
    DataCipher cipher;

    private String signupWithMoods(String name, String email, boolean optIn, Sentiment... moods) throws Exception {
        String body = """
                {"username":"%s","authKey":"%s","wrappedKey":"eA==","wrappedKeyIv":"AAAAAAAAAAAAAAAA","email":"%s","weeklyReport":%s}
                """.formatted(name, "a".repeat(64), email, optIn);
        String json = mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = com.jayway.jsonpath.JsonPath.read(json, "$.token");
        for (Sentiment m : moods) {
            mvc.perform(post("/api/v1/entries").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\":\"%s\",\"ciphertext\":\"eA==\",\"iv\":\"AAAAAAAAAAAAAAAA\",\"reportSentiment\":\"%s\"}"
                                    .formatted(UUID.randomUUID(), m)))
                    .andExpect(status().isCreated());
        }
        return name;
    }

    @Test
    void sendsMostFrequentMoodOnlyToOptedInUsers() throws Exception {
        signupWithMoods("rep_a" + System.nanoTime() % 10000, "a@example.com", true, Sentiment.SAD, Sentiment.HAPPY, Sentiment.SAD);
        signupWithMoods("rep_b" + System.nanoTime() % 10000, "b@example.com", false, Sentiment.ANGRY);

        job.run();

        verify(emailService).send(eq("a@example.com"), anyString(), contains("most frequent mood was sad"));
        verify(emailService, never()).send(eq("b@example.com"), anyString(), anyString());
    }

    @Test
    void oneFailingUserDoesNotStopTheOthers() throws Exception {
        signupWithMoods("rep_c" + System.nanoTime() % 10000, "c@example.com", true, Sentiment.HAPPY);
        signupWithMoods("rep_d" + System.nanoTime() % 10000, "d@example.com", true, Sentiment.ANXIOUS);
        doThrow(new MailSendException("smtp down")).when(emailService).send(eq("c@example.com"), anyString(), anyString());

        job.run();

        verify(emailService).send(eq("d@example.com"), anyString(), contains("anxious"));
    }

    @Test
    void usersWithoutMoodsThisWeekGetNoEmail() throws Exception {
        signupWithMoods("rep_e" + System.nanoTime() % 10000, "e@example.com", true);
        job.run();
        verify(emailService, never()).send(eq("e@example.com"), anyString(), anyString());
    }

    @Test
    void tiesAreBrokenDeterministically() {
        assertThat(WeeklyReportJob.mostFrequent(List.of(Sentiment.ANXIOUS, Sentiment.SAD))).contains(Sentiment.SAD);
        assertThat(WeeklyReportJob.mostFrequent(List.of(Sentiment.ANGRY, Sentiment.HAPPY))).contains(Sentiment.HAPPY);
        assertThat(WeeklyReportJob.mostFrequent(List.of())).isEqualTo(Optional.empty());
    }
}
