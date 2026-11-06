package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The ratings change request (workflow/specs/ratings-cr.md): trimmed at 5+ ratings, 2 decimals half-even.
 * All tests share one database, and a speaker has at most 3 talks, so every test makes its own speaker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingsTrimTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void fiveRatingsLeaveOutOneHighestAndOneLowest() throws Exception {
        long s = newSpeaker();
        long talk = newTalk(s);
        rateAll(talk, 1, 5, 5, 5, 5);
        assertRating(getJson("/talks/" + talk), 5.0, 5);
        for (String path : Arrays.asList("/talks", "/talks?speakerId=" + s, "/speakers/" + s + "/talks",
                "/talks?sort=rating&speakerId=" + s)) {
            assertRating(talkIn(getJson(path), talk, path), 5.0, 5);
        }
    }

    @Test
    void fourRatingsCountEveryScore() throws Exception {
        long talk = newTalk(newSpeaker());
        rateAll(talk, 1, 4, 4, 4);
        assertRating(getJson("/talks/" + talk), 3.25, 4);
        rate(talk, 5, 4);
        assertRating(getJson("/talks/" + talk), 4.0, 5);
    }

    @Test
    void reRatingDoesNotReachTheThreshold() throws Exception {
        long talk = newTalk(newSpeaker());
        rateAll(talk, 1, 4, 4, 4);
        rate(talk, 2, 5);
        rate(talk, 2, 4);
        assertRating(getJson("/talks/" + talk), 3.25, 4);
    }

    @Test
    void halfEvenAtTheThirdDecimal() throws Exception {
        long s = newSpeaker();
        long down = newTalk(s);
        long up = newTalk(s);
        rateAll(down, 1, 3, 3, 3, 3, 3, 3, 3, 4, 5);
        rateAll(up, 1, 3, 3, 3, 3, 3, 3, 4, 5, 5);
        assertRating(getJson("/talks/" + down), 3.12, 10);
        assertRating(getJson("/talks/" + up), 3.38, 10);
    }

    @Test
    void wireTextHasTheRoundedNumber() throws Exception {
        long s = newSpeaker();
        long down = newTalk(s);
        long up = newTalk(s);
        long whole = newTalk(s);
        rateAll(down, 1, 3, 3, 3, 3, 3, 3, 3, 4, 5);
        rateAll(up, 1, 3, 3, 3, 3, 3, 3, 4, 5, 5);
        rateAll(whole, 1, 4, 4, 4, 4);
        String list = send("GET", "/talks?speakerId=" + s, null).body();
        assertWire(list, down, "3.12", 10);
        assertWire(list, up, "3.38", 10);
        assertWire(list, whole, "4.0", 5);
        assertWire(send("GET", "/talks/" + whole, null).body(), whole, "4.0", 5);
    }

    @Test
    void rankingUsesTheTrimmedAverage() throws Exception {
        long s = newSpeaker();
        long x = newTalk(s);
        long y = newTalk(s);
        long z = newTalk(s);
        rateAll(x, 1, 5, 5, 5, 5);
        rateAll(y, 4, 4, 4, 4, 5);
        rateAll(z, 5, 5, 5, 5);
        assertEquals(Arrays.asList(x, z, y), ids(getJson("/talks?sort=rating&speakerId=" + s)));
    }

    @Test
    void sameAverageRanksMoreRatingsFirst() throws Exception {
        long s = newSpeaker();
        long p = newTalk(s);
        long q = newTalk(s);
        rateAll(p, 4, 4, 5);
        rateAll(q, 1, 4, 4, 5, 5);
        assertRating(getJson("/talks/" + q), 4.33, 5);
        assertEquals(Arrays.asList(q, p), ids(getJson("/talks?sort=rating&speakerId=" + s)));
    }

    // ---------------------------------------------------------------- helpers

    private static void assertRating(JsonNode talk, double average, int count) {
        assertEquals(average, talk.get("averageRating").asDouble(), talk.toString());
        assertEquals(count, talk.get("ratingCount").asInt(), talk.toString());
    }

    private static void assertWire(String body, long talk, String average, int count) {
        boolean found = Pattern.compile("\"id\":" + talk + ",[^}]*\"averageRating\":" + Pattern.quote(average)
                + ",\"ratingCount\":" + count + "[,}]").matcher(body).find();
        assertTrue(found, "talk " + talk + " should show " + average + " and " + count + ": " + body);
    }

    private void rateAll(long talk, int... scores) throws Exception {
        for (int r = 0; r < scores.length; r++) {
            rate(talk, r + 1, scores[r]);
        }
    }

    private void rate(long talk, long reviewer, int score) throws Exception {
        HttpResponse<String> res = send("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":" + score + "}");
        assertEquals(200, res.statusCode(), res.body());
    }

    private long newSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + UUID.randomUUID() + "@example.com\"}");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private long newTalk(long speaker) throws Exception {
        HttpResponse<String> res = send("POST", "/talks",
                "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> res = send("GET", path, null);
        assertEquals(200, res.statusCode(), path + " " + res.body());
        return JSON.readTree(res.body());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            b.header("Content-Type", "application/json");
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode talkIn(JsonNode array, long id, String where) {
        for (JsonNode t : array) {
            if (t.get("id").asLong() == id) {
                return t;
            }
        }
        return fail("talk " + id + " missing from " + where);
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode t : array) {
            ids.add(t.get("id").asLong());
        }
        return ids;
    }
}
