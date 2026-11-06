package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The acceptance state machine against workflow/specs/acceptance.md and its decisions D1 to D16. All tests share one
 * database, so each creates its own speakers and talks (a speaker has at most 3 talks).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AcceptanceTest {

    private static final long MISSING_TALK = 2147483647L;
    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // ---------------------------------------------------------------- decisions

    @Test
    void decisionsFromSubmitted() throws Exception {
        long rejected = newTalk(newSpeaker());
        HttpResponse<String> res = decide(rejected, "REJECTED");
        assertEquals(200, res.statusCode(), res.body());
        JsonNode talk = JSON.readTree(res.body());
        assertEquals("REJECTED", talk.get("status").asText());
        assertEquals(rejected, talk.get("id").asLong());
        assertTrue(talk.has("ratingCount") && talk.has("averageRating") && talk.has("coSpeakerIds"), talk.toString());

        long waitlisted = newTalk(newSpeaker());
        assertStatus(200, decide(waitlisted, "WAITLISTED"), "WAITLISTED");

        long accepted = newTalk(newSpeaker());
        rateAll(accepted, 4, 5);
        assertStatus(200, decide(accepted, "ACCEPTED"), "ACCEPTED");
    }

    @Test
    void waitlistedCanStillBeDecided() throws Exception {
        long toAccept = newTalk(newSpeaker());
        rateAll(toAccept, 3, 4);
        assertEquals(200, decide(toAccept, "WAITLISTED").statusCode());
        assertStatus(200, decide(toAccept, "ACCEPTED"), "ACCEPTED");

        long toReject = newTalk(newSpeaker());
        assertEquals(200, decide(toReject, "WAITLISTED").statusCode());
        assertStatus(200, decide(toReject, "REJECTED"), "REJECTED");
    }

    @Test
    void acceptNeedsTwoDistinctReviewers() throws Exception {
        long talk = newTalk(newSpeaker());
        assertEquals(409, decide(talk, "ACCEPTED").statusCode());
        assertEquals("SUBMITTED", statusOf(talk));

        assertEquals(200, rate(talk, 1, 4).statusCode());
        assertEquals(200, rate(talk, 1, 5).statusCode());
        assertEquals(409, decide(talk, "ACCEPTED").statusCode());
        assertEquals("SUBMITTED", statusOf(talk));

        assertEquals(200, rate(talk, 2, 3).statusCode());
        assertStatus(200, decide(talk, "ACCEPTED"), "ACCEPTED");
    }

    @Test
    void finalDecisionsAreFinal() throws Exception {
        long accepted = newTalk(newSpeaker());
        rateAll(accepted, 4, 5);
        assertEquals(200, decide(accepted, "ACCEPTED").statusCode());
        long rejected = newTalk(newSpeaker());
        assertEquals(200, decide(rejected, "REJECTED").statusCode());
        for (String status : Arrays.asList("ACCEPTED", "REJECTED", "WAITLISTED")) {
            assertEquals(409, decide(accepted, status).statusCode(), "ACCEPTED -> " + status);
            assertEquals(409, decide(rejected, status).statusCode(), "REJECTED -> " + status);
        }
        assertEquals("ACCEPTED", statusOf(accepted));
        assertEquals("REJECTED", statusOf(rejected));

        long waitlisted = newTalk(newSpeaker());
        assertEquals(200, decide(waitlisted, "WAITLISTED").statusCode());
        assertEquals(409, decide(waitlisted, "WAITLISTED").statusCode());
        assertEquals("WAITLISTED", statusOf(waitlisted));
    }

    @Test
    void invalidStatusGives400() throws Exception {
        long talk = newTalk(newSpeaker());
        for (String body : Arrays.asList("{\"status\":\"SUBMITTED\"}", "{\"status\":\"WITHDRAWN\"}",
                "{\"status\":\"accepted\"}", "{\"status\":\"x\"}", "{\"status\":null}", "{\"status\":1}", "{}")) {
            assertEquals(400, send("PUT", "/talks/" + talk + "/status", body, "application/json").statusCode(), body);
        }
        assertEquals("SUBMITTED", statusOf(talk));
    }

    @Test
    void unknownTalkGives404() throws Exception {
        assertEquals(404, decide(MISSING_TALK, "REJECTED").statusCode());
        assertEquals(404, send("PUT", "/talks/" + MISSING_TALK + "/status", "{\"status\":\"x\"}",
                "application/json").statusCode());
    }

    @Test
    void badBodies() throws Exception {
        long talk = newTalk(newSpeaker());
        String path = "/talks/" + talk + "/status";
        assertEquals(400, send("PUT", path, null, "application/json").statusCode(), "no body");
        assertEquals(400, send("PUT", path, "not json", "application/json").statusCode(), "not json");
        assertEquals(400, send("PUT", path, "[1]", "application/json").statusCode(), "array");
        assertEquals(415, send("PUT", path, "{\"status\":\"REJECTED\"}", "text/plain").statusCode(), "text/plain");
        assertEquals(400, send("PUT", "/talks/abc/status", "{\"status\":\"REJECTED\"}", "application/json").statusCode());
        assertEquals("SUBMITTED", statusOf(talk));
    }

    @Test
    void statusShowsWhereverTheTalkIsShown() throws Exception {
        long speaker = newSpeaker();
        long waitlisted = newTalk(speaker);
        long rejected = newTalk(speaker);
        assertEquals(200, decide(waitlisted, "WAITLISTED").statusCode());
        assertEquals(200, decide(rejected, "REJECTED").statusCode());

        assertEquals("WAITLISTED", statusOf(waitlisted));
        for (String path : Arrays.asList("/talks", "/talks?sort=rating", "/talks?speakerId=" + speaker,
                "/speakers/" + speaker + "/talks")) {
            JsonNode list = getJson(path);
            assertEquals("WAITLISTED", talkIn(list, waitlisted, path).get("status").asText(), path);
            assertEquals("REJECTED", talkIn(list, rejected, path).get("status").asText(), path);
        }
    }

    // ---------------------------------------------------------------- withdrawal

    @Test
    void speakerWithdraws() throws Exception {
        long speaker = newSpeaker();
        long submitted = newTalk(speaker);
        long waitlisted = newTalk(speaker);
        long accepted = newTalk(speaker);
        assertEquals(200, decide(waitlisted, "WAITLISTED").statusCode());
        rateAll(accepted, 4, 5);
        assertEquals(200, decide(accepted, "ACCEPTED").statusCode());
        for (long talk : new long[] {submitted, waitlisted, accepted}) {
            HttpResponse<String> res = withdraw(talk, speaker);
            assertStatus(200, res, "WITHDRAWN");
            assertEquals(talk, JSON.readTree(res.body()).get("id").asLong());
        }
    }

    @Test
    void withdrawnIsOutForGood() throws Exception {
        long speaker = newSpeaker();
        long talk = newTalk(speaker);
        rateAll(talk, 4, 5);
        assertEquals(200, withdraw(talk, speaker).statusCode());
        for (String status : Arrays.asList("ACCEPTED", "REJECTED", "WAITLISTED")) {
            assertEquals(409, decide(talk, status).statusCode(), status);
        }
        assertEquals(409, withdraw(talk, speaker).statusCode());
        assertEquals("WITHDRAWN", statusOf(talk));
    }

    @Test
    void rejectedCantBeWithdrawn() throws Exception {
        long speaker = newSpeaker();
        long talk = newTalk(speaker);
        assertEquals(200, decide(talk, "REJECTED").statusCode());
        assertEquals(409, withdraw(talk, speaker).statusCode());
        assertEquals("REJECTED", statusOf(talk));
    }

    @Test
    void onlyTheMainSpeakerCanWithdraw() throws Exception {
        long speaker = newSpeaker();
        long talk = newTalk(speaker);
        long other = newSpeaker();
        long coSpeaker = newSpeaker();
        assertEquals(200, send("PUT", "/talks/" + talk + "/co-speakers/" + coSpeaker, null, null).statusCode());
        assertEquals(403, withdraw(talk, other).statusCode());
        assertEquals(403, withdraw(talk, coSpeaker).statusCode());
        assertEquals(403, withdraw(talk, 999999).statusCode());
        assertEquals("SUBMITTED", statusOf(talk));
    }

    @Test
    void invalidWithdrawBody() throws Exception {
        long talk = newTalk(newSpeaker());
        String path = "/talks/" + talk + "/withdraw";
        for (String body : Arrays.asList("{}", "{\"speakerId\":null}", "{\"speakerId\":\"1\"}",
                "{\"speakerId\":1.5}", "{\"speakerId\":0}", "{\"speakerId\":-1}", "not json")) {
            assertEquals(400, send("POST", path, body, "application/json").statusCode(), body);
        }
        assertEquals(400, send("POST", path, null, "application/json").statusCode(), "no body");
        assertEquals(415, send("POST", path, "{\"speakerId\":1}", "text/plain").statusCode(), "text/plain");
        assertEquals("SUBMITTED", statusOf(talk));
    }

    @Test
    void withdrawPrecedence() throws Exception {
        assertEquals(404, send("POST", "/talks/" + MISSING_TALK + "/withdraw", "{}", "application/json").statusCode());
        long talk = newTalk(newSpeaker());
        assertEquals(200, decide(talk, "REJECTED").statusCode());
        assertEquals(403, withdraw(talk, newSpeaker()).statusCode());
    }

    @Test
    void afterwardsNothingElseChanges() throws Exception {
        long speaker = newSpeaker();
        long withdrawn = newTalk(speaker);
        newTalk(speaker);
        newTalk(speaker);
        assertEquals(200, withdraw(withdrawn, speaker).statusCode());
        assertEquals(200, rate(withdrawn, 1, 4).statusCode());
        assertEquals(1, getJson("/talks/" + withdrawn).get("ratingCount").asInt());
        HttpResponse<String> fourth = send("POST", "/talks",
                "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}",
                "application/json");
        assertEquals(409, fourth.statusCode(), fourth.body());
    }

    // ---------------------------------------------------------------- helpers

    private HttpResponse<String> withdraw(long talk, long speaker) throws Exception {
        return send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}", "application/json");
    }

    private void assertStatus(int code, HttpResponse<String> res, String status) throws Exception {
        assertEquals(code, res.statusCode(), res.body());
        assertEquals(status, JSON.readTree(res.body()).get("status").asText());
    }

    private String statusOf(long talk) throws Exception {
        return getJson("/talks/" + talk).get("status").asText();
    }

    private HttpResponse<String> decide(long talk, String status) throws Exception {
        return send("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}", "application/json");
    }

    private HttpResponse<String> rate(long talk, long reviewer, int score) throws Exception {
        return send("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":" + score + "}", "application/json");
    }

    private void rateAll(long talk, int... scores) throws Exception {
        for (int r = 0; r < scores.length; r++) {
            assertEquals(200, rate(talk, r + 1, scores[r]).statusCode());
        }
    }

    private long newSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers",
                "{\"name\":\"Margaret Hamilton\",\"email\":\"margaret-" + UUID.randomUUID() + "@example.com\"}",
                "application/json");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private long newTalk(long speaker) throws Exception {
        HttpResponse<String> res = send("POST", "/talks",
                "{\"title\":\"Talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}",
                "application/json");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
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
}
