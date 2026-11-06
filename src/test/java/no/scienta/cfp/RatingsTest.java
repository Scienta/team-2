package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Ratings against the spec (workflow/specs/ratings.md) and the team's error policy (todo/ratings_plan.md, E1 to E7).
 * All tests share one database, so list assertions only look at the talks the test created.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RatingsTest {

    private static final long MISSING_TALK = 2147483647L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter US_SHORT =
            DateTimeFormatter.ofPattern("M/d/yy, h:mm a", Locale.US).withZone(ZoneOffset.UTC);

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void happyPath() throws Exception {
        long talk = newTalk(newSpeaker());
        HttpResponse<String> res = rate(talk, 7, "{\"score\":4}");
        assertEquals(200, res.statusCode(), res.body());
        JsonNode body = JSON.readTree(res.body());
        assertEquals(talk, body.get("talkId").asLong());
        assertEquals(7, body.get("reviewerId").asLong());
        assertEquals(4, body.get("score").asInt());
        assertTrue(body.get("ratedAtDisplay").asString().matches("^\\d{1,2}/\\d{1,2}/\\d{2}, \\d{1,2}:\\d{2} [AP]M$"),
                body.get("ratedAtDisplay").asString());
    }

    @Test
    void reRatingReplacesTheScore() throws Exception {
        long talk = newTalk(newSpeaker());
        assertEquals(200, rate(talk, 1, "{\"score\":2}").statusCode());
        assertEquals(200, rate(talk, 1, "{\"score\":5}").statusCode());
        JsonNode t = getJson("/talks/" + talk);
        assertEquals(1, t.get("ratingCount").asInt());
        assertEquals(5.0, t.get("averageRating").asDouble());
    }

    @Test
    void aggregatesShowWhereverATalkIsShown() throws Exception {
        long s = newSpeaker();
        long t1 = newTalk(s);
        HttpResponse<String> created = send("POST", "/talks", talkJson(s), "application/json");
        assertEquals(201, created.statusCode(), created.body());
        JsonNode t2Node = JSON.readTree(created.body());
        assertEquals(0, t2Node.get("ratingCount").asInt());
        assertNullField(t2Node, "averageRating");
        long t2 = t2Node.get("id").asLong();

        for (int r = 1; r <= 3; r++) {
            assertEquals(200, rate(t1, r, "{\"score\":4}").statusCode());
        }
        assertEquals(200, rate(t1, 3, "{\"score\":5}").statusCode());

        assertAggregates(getJson("/talks/" + t1), getJson("/talks/" + t2));
        for (String path : Arrays.asList("/talks", "/talks?sort=rating", "/talks?speakerId=" + s,
                "/talks?sort=rating&speakerId=" + s, "/speakers/" + s + "/talks")) {
            JsonNode list = getJson(path);
            assertAggregates(talkIn(list, t1, path), talkIn(list, t2, path));
        }
    }

    private static void assertAggregates(JsonNode rated, JsonNode unrated) {
        assertEquals(3, rated.get("ratingCount").asInt(), rated.toString());
        assertEquals(4.33, rated.get("averageRating").asDouble(), rated.toString());
        assertEquals(0, unrated.get("ratingCount").asInt(), unrated.toString());
        assertNullField(unrated, "averageRating");
    }

    @Test
    void averageHasTwoDecimals() throws Exception {
        long talk = newTalk(newSpeaker());
        int[] scores = {4, 4, 4, 5};
        for (int r = 0; r < scores.length; r++) {
            assertEquals(200, rate(talk, r + 1, "{\"score\":" + scores[r] + "}").statusCode());
        }
        assertEquals(4.25, getJson("/talks/" + talk).get("averageRating").asDouble());
    }

    @Test
    void ranking() throws Exception {
        long s1 = newSpeaker();
        long s2 = newSpeaker();
        long a = newTalk(s1);
        long b = newTalk(s1);
        long c = newTalk(s1);
        long d = newTalk(s2);
        long e = newTalk(s2);
        rateAll(a, 4, 4, 4, 5);
        rateAll(b, 5, 4, 4);
        rateAll(c, 4, 5, 4, 4);
        rateAll(e, 5);
        List<Long> ours = Arrays.asList(a, b, c, d, e);

        assertEquals(Arrays.asList(e, b, a, c, d), idsInOrder(getJson("/talks?sort=rating"), ours));
        assertEquals(Arrays.asList(b, a, c), ids(getJson("/talks?sort=rating&speakerId=" + s1)));
        assertEquals(Arrays.asList(e, d), ids(getJson("/talks?sort=rating&speakerId=" + s2)));
        assertEquals(Arrays.asList(a, b, c, d, e), idsInOrder(getJson("/talks"), ours));
    }

    @Test
    void ratedAtDisplayIsTheLatestRatingsTime() throws Exception {
        long talk = newTalk(newSpeaker());
        assertEquals(200, rate(talk, 1, "{\"score\":3}").statusCode());
        jdbc.update("UPDATE rating SET rated_at = '2020-01-01 00:00:00' WHERE talk_id = ? AND reviewer_id = 1", talk);

        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        HttpResponse<String> res = rate(talk, 1, "{\"score\":4}");
        Instant after = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertEquals(200, res.statusCode(), res.body());

        String stored = jdbc.queryForObject("SELECT rated_at FROM rating WHERE talk_id = ? AND reviewer_id = 1",
                String.class, talk);
        assertNotEquals("2020-01-01 00:00:00", stored);
        Instant storedAt = LocalDateTime.parse(stored, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                .toInstant(ZoneOffset.UTC);
        assertFalse(storedAt.isBefore(before), stored);
        assertFalse(storedAt.isAfter(after), stored);

        String display = JSON.readTree(res.body()).get("ratedAtDisplay").asString();
        assertNotEquals("1/1/20, 12:00 AM", display);
        assertTrue(display.equals(US_SHORT.format(before)) || display.equals(US_SHORT.format(after)), display);
    }

    @Test
    void invalidScoresGive400() throws Exception {
        long talk = newTalk(newSpeaker());
        for (String body : Arrays.asList("{\"score\":0}", "{\"score\":6}", "{\"score\":4.5}", "{\"score\":5.0}",
                "{\"score\":\"5\"}", "{\"score\":null}", "{\"score\":true}", "{}")) {
            assertEquals(400, rate(talk, 1, body).statusCode(), body);
        }
        assertEquals(0, getJson("/talks/" + talk).get("ratingCount").asInt());
    }

    @Test
    void invalidReviewerGives400() throws Exception {
        long talk = newTalk(newSpeaker());
        for (String reviewer : Arrays.asList("0", "-1", "abc")) {
            HttpResponse<String> res = send("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":3}",
                    "application/json");
            assertEquals(400, res.statusCode(), reviewer);
        }
        assertEquals(400, send("PUT", "/talks/" + MISSING_TALK + "/ratings/0", "{\"score\":6}",
                "application/json").statusCode());
    }

    @Test
    void badBodies() throws Exception {
        long talk = newTalk(newSpeaker());
        String path = "/talks/" + talk + "/ratings/1";
        assertEquals(400, send("PUT", path, null, "application/json").statusCode(), "E4a no body");
        assertEquals(400, send("PUT", path, "not json", "application/json").statusCode(), "E4b not json");
        assertEquals(400, send("PUT", path, "[1]", "application/json").statusCode(), "E4c array");
        assertEquals(415, send("PUT", path, "{\"score\":3}", "text/plain").statusCode(), "E4d text/plain");
        assertEquals(400, send("PUT", "/talks/" + MISSING_TALK + "/ratings/1", "not json",
                "application/json").statusCode(), "E4b on a missing talk");
    }

    @Test
    void missingTalkGives404() throws Exception {
        assertEquals(404, rate(MISSING_TALK, 1, "{\"score\":3}").statusCode());
    }

    // E1 (todo/ratings_plan.md, approved): a missing talk gives 404 before the score is validated
    @Test
    void invalidScoreOnMissingTalk() throws Exception {
        assertEquals(404, rate(MISSING_TALK, 1, "{\"score\":6}").statusCode());
    }

    @Test
    void errorPrecedence() throws Exception {
        long talk = newTalk(newSpeaker());
        assertEquals(400, rate(MISSING_TALK, 0, "{\"score\":6}").statusCode(), "reviewer first");
        assertEquals(404, rate(MISSING_TALK, 1, "{\"score\":6}").statusCode(), "talk before score");
        assertEquals(404, rate(MISSING_TALK, 1, "{}").statusCode(), "talk before missing score");
        assertEquals(400, rate(talk, 1, "{\"score\":6}").statusCode(), "score on an existing talk");
        assertEquals(400, rate(MISSING_TALK, 1, "not json").statusCode(), "binding first");
    }

    @Test
    void filterAndSort() throws Exception {
        HttpResponse<String> unknown = send("GET", "/talks?speakerId=999999", null, null);
        assertEquals(200, unknown.statusCode());
        assertEquals(0, JSON.readTree(unknown.body()).size());
        assertEquals(400, send("GET", "/talks?sort=foo", null, null).statusCode());
    }

    @Test
    void concurrentRatingsFromManyReviewersAreAllCountedOnce() throws Exception {
        long talk = newTalk(newSpeaker());
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int r = 1; r <= 20; r++) {
            String body = "{\"score\":" + (r <= 10 ? 3 : 4) + "}";
            sent.add(rateAsync(talk, r, body));
            sent.add(rateAsync(talk, r, body));
        }
        for (CompletableFuture<HttpResponse<String>> f : sent) {
            HttpResponse<String> res = f.join();
            assertEquals(200, res.statusCode(), res.body());
        }
        JsonNode t = getJson("/talks/" + talk);
        assertEquals(20, t.get("ratingCount").asInt());
        assertEquals(3.5, t.get("averageRating").asDouble());
    }

    @Test
    void concurrentRatingsFromOneReviewerCountOnce() throws Exception {
        long talk = newTalk(newSpeaker());
        int[] scores = {1, 2, 3, 4, 5, 1, 2, 3, 4, 5};
        List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
        for (int score : scores) {
            sent.add(rateAsync(talk, 1, "{\"score\":" + score + "}"));
        }
        for (int i = 0; i < scores.length; i++) {
            HttpResponse<String> res = sent.get(i).join();
            assertEquals(200, res.statusCode(), res.body());
            assertEquals(scores[i], JSON.readTree(res.body()).get("score").asInt());
        }
        JsonNode t = getJson("/talks/" + talk);
        assertEquals(1, t.get("ratingCount").asInt());
        double avg = t.get("averageRating").asDouble();
        assertTrue(Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0).contains(avg), String.valueOf(avg));
    }

    // ---------------------------------------------------------------- helpers

    private void rateAll(long talk, int... scores) throws Exception {
        for (int r = 0; r < scores.length; r++) {
            assertEquals(200, rate(talk, r + 1, "{\"score\":" + scores[r] + "}").statusCode());
        }
    }

    private long newSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers",
                "{\"name\":\"Ada Lovelace\",\"email\":\"ada-" + UUID.randomUUID() + "@example.com\"}", "application/json");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private long newTalk(long speaker) throws Exception {
        HttpResponse<String> res = send("POST", "/talks", talkJson(speaker), "application/json");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private static String talkJson(long speaker) {
        return "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}";
    }

    private HttpResponse<String> rate(long talk, long reviewer, String json) throws Exception {
        return send("PUT", "/talks/" + talk + "/ratings/" + reviewer, json, "application/json");
    }

    private CompletableFuture<HttpResponse<String>> rateAsync(long talk, long reviewer, String json) {
        return http.sendAsync(request("PUT", "/talks/" + talk + "/ratings/" + reviewer, json, "application/json"),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode getJson(String path) throws Exception {
        HttpResponse<String> res = send("GET", path, null, null);
        assertEquals(200, res.statusCode(), path + " " + res.body());
        return JSON.readTree(res.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        return http.send(request(method, path, body, contentType), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String body, String contentType) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            b.header("Content-Type", contentType);
        }
        return b.build();
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

    private static List<Long> idsInOrder(JsonNode array, List<Long> ours) {
        List<Long> ids = ids(array);
        ids.retainAll(ours);
        return ids;
    }

    private static void assertNullField(JsonNode node, String field) {
        assertTrue(node.has(field), field + " missing in " + node);
        assertTrue(node.get(field).isNull(), field + " not null in " + node);
    }
}
