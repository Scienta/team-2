package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import no.scienta.cfp.Venue.Slot;

/**
 * Programme scheduling against workflow/specs/scheduling.md and its decisions D1 to D20. All tests share one database:
 * the programme is emptied before each test, and every talk gets its own speaker unless the test is about a shared one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProgrammeTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int ROUNDS = 10;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ProgrammeDao programmeDao;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void emptyProgramme() {
        jdbc.update("TRUNCATE programme_entry");
    }

    // ---------------------------------------------------------------- PUT and GET

    @Test
    void schedulesATalkAndShowsItsPlace() throws Exception {
        long talk = acceptedTalk("PRESENTATION");
        HttpResponse<String> put = schedule("R1", "S2", talk);
        assertEquals(200, put.statusCode(), put.body());
        String entry = "{\"room\":\"R1\",\"slot\":\"S2\",\"start\":\"10:15\",\"end\":\"11:15\","
                + "\"talkId\":" + talk + "}";
        assertEquals(entry, put.body());
        HttpResponse<String> get = send("GET", "/programme/R1/S2", null, null);
        assertEquals(200, get.statusCode());
        assertEquals(entry, get.body());
    }

    @Test
    void anEmptyPlaceIsNotFound() throws Exception {
        assertEquals(404, send("GET", "/programme/R1/S1", null, null).statusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"R4/S1", "R1/S6", "r1/S1", "R1/s1", "R0/S0"})
    void anUnknownRoomOrSlotIsNotFound(String place) throws Exception {
        long talk = acceptedTalk("PRESENTATION");
        assertEquals(404, send("PUT", "/programme/" + place, talkIdBody(talk), "application/json").statusCode());
        assertEquals(404, send("GET", "/programme/" + place, null, null).statusCode());
        assertEquals(404, send("DELETE", "/programme/" + place, null, null).statusCode());
        assertEquals(0, entries());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"talkId\":null}", "{\"talkId\":\"5\"}", "{\"talkId\":1.5}", "{\"talkId\":5.0}",
            "{\"talkId\":0}", "{\"talkId\":-1}", "{\"talkId\":true}", "{\"talkId\":99999999999999999999}"})
    void anInvalidTalkIdIsABadRequest(String body) throws Exception {
        assertEquals(400, send("PUT", "/programme/R1/S1", body, "application/json").statusCode(), body);
        assertEquals(0, entries());
    }

    @ParameterizedTest
    @ValueSource(longs = {2147483647L, 2147483648L})
    void anUnknownTalkIsABadRequest(long talkId) throws Exception {
        assertError(400, "talk not found", schedule("R1", "S1", talkId));
    }

    @Test
    void onlyAcceptedTalksGoOnTheProgramme() throws Exception {
        long submitted = newTalk(newSpeaker(), "PRESENTATION");
        long waitlisted = newTalk(newSpeaker(), "PRESENTATION");
        assertEquals(200, decide(waitlisted, "WAITLISTED").statusCode());
        long rejected = newTalk(newSpeaker(), "PRESENTATION");
        assertEquals(200, decide(rejected, "REJECTED").statusCode());
        long speaker = newSpeaker();
        long withdrawn = newTalk(speaker, "PRESENTATION");
        assertEquals(200, withdraw(withdrawn, speaker).statusCode());

        for (long talk : List.of(submitted, waitlisted, rejected, withdrawn)) {
            assertError(409, SchedulingRules.NOT_ACCEPTED, schedule("R1", "S1", talk));
        }
        assertEquals(0, entries());
    }

    @Test
    void aWorkshopNeedsTheLongSlot() throws Exception {
        long workshop = acceptedTalk("WORKSHOP");
        assertError(409, SchedulingRules.WRONG_SLOT, schedule("R1", "S1", workshop));
        assertEquals(200, schedule("R1", "S4", workshop).statusCode());
        assertEquals(200, schedule("R2", "S4", acceptedTalk("LIGHTNING_10")).statusCode());
    }

    @Test
    void onePlaceHoldsOneTalk() throws Exception {
        long first = acceptedTalk("PRESENTATION");
        assertEquals(200, schedule("R1", "S1", first).statusCode());
        assertError(409, SchedulingRules.TAKEN, schedule("R1", "S1", acceptedTalk("PRESENTATION")));
        assertEquals(first, talkIn("R1", "S1"));
    }

    @Test
    void theSamePlaceAgainChangesNothing() throws Exception {
        long talk = acceptedTalk("PRESENTATION");
        HttpResponse<String> first = schedule("R3", "S5", talk);
        HttpResponse<String> again = schedule("R3", "S5", talk);
        assertEquals(200, again.statusCode(), again.body());
        assertEquals(first.body(), again.body());
        assertEquals(1, entries());
    }

    @Test
    void aTalkIsOnTheProgrammeOnlyOnce() throws Exception {
        long talk = acceptedTalk("PRESENTATION");
        assertEquals(200, schedule("R1", "S1", talk).statusCode());
        assertError(409, SchedulingRules.ELSEWHERE, schedule("R2", "S2", talk));
        assertEquals(404, send("GET", "/programme/R2/S2", null, null).statusCode());
    }

    @Test
    void nobodySpeaksInTwoRoomsAtOnce() throws Exception {
        long speaker = newSpeaker();
        long first = acceptedTalk(speaker, "PRESENTATION");
        long second = acceptedTalk(speaker, "PRESENTATION");
        assertEquals(200, schedule("R1", "S3", first).statusCode());
        assertError(409, SchedulingRules.CLASH, schedule("R2", "S3", second));
        assertEquals(200, schedule("R2", "S2", second).statusCode(), "another slot is fine");
    }

    @Test
    void aCoSpeakerCanNotBeInTwoRoomsAtOnce() throws Exception {
        long coSpeaker = newSpeaker();
        long withCoSpeaker = acceptedTalk("PRESENTATION");
        String addCoSpeaker = "/talks/" + withCoSpeaker + "/co-speakers/" + coSpeaker;
        assertEquals(200, send("PUT", addCoSpeaker, null, null).statusCode());
        long byTheCoSpeaker = acceptedTalk(coSpeaker, "PRESENTATION");
        assertEquals(200, schedule("R1", "S5", withCoSpeaker).statusCode());
        assertError(409, SchedulingRules.CLASH, schedule("R3", "S5", byTheCoSpeaker));
    }

    @Test
    void badBodies() throws Exception {
        String path = "/programme/R1/S1";
        assertEquals(400, send("PUT", path, null, "application/json").statusCode(), "no body");
        assertEquals(400, send("PUT", path, "not json", "application/json").statusCode(), "not json");
        assertEquals(400, send("PUT", path, "[1]", "application/json").statusCode(), "array");
        long talk = acceptedTalk("PRESENTATION");
        assertEquals(415, send("PUT", path, talkIdBody(talk), "text/plain").statusCode(), "text/plain");
        assertEquals(0, entries());
    }

    @Test
    void aTalkWithdrawnAfterwardsStaysButCanNotBePutBack() throws Exception {
        long speaker = newSpeaker();
        long talk = acceptedTalk(speaker, "PRESENTATION");
        assertEquals(200, schedule("R2", "S1", talk).statusCode());
        assertEquals(200, withdraw(talk, speaker).statusCode());
        assertEquals(talk, talkIn("R2", "S1"));
        assertError(409, SchedulingRules.NOT_ACCEPTED, schedule("R2", "S1", talk));
    }

    @Test
    void theTalkJsonIsUnchanged() throws Exception {
        long talk = acceptedTalk("PRESENTATION");
        assertEquals(200, schedule("R1", "S1", talk).statusCode());
        HttpResponse<String> res = send("GET", "/talks/" + talk, null, null);
        assertEquals(WireFormatTest.TALK_KEYS, WireFormatTest.keys(res.body()));
    }

    @Test
    void aBadBodyComesBeforeAnUnknownRoom() throws Exception {
        String path = "/programme/R4/S1";
        assertEquals(400, send("PUT", path, null, "application/json").statusCode(), "no body");
        assertEquals(400, send("PUT", path, "not json", "application/json").statusCode(), "not json");
        assertEquals(415, send("PUT", path, "{\"talkId\":1}", "text/plain").statusCode(), "text/plain");
    }

    @Test
    void errorsComeInTheDecidedOrder() throws Exception {
        long taken = acceptedTalk("PRESENTATION");
        assertEquals(200, schedule("R1", "S1", taken).statusCode());
        long submitted = newTalk(newSpeaker(), "WORKSHOP");

        assertEquals(404, send("PUT", "/programme/R4/S1", "{\"talkId\":0}", "application/json").statusCode(),
                "an unknown room before an invalid talkId");
        assertEquals(400, send("PUT", "/programme/R1/S1", "{\"talkId\":0}", "application/json").statusCode(),
                "an invalid talkId before the place is looked at");
        assertError(400, "talk not found", schedule("R1", "S1", 2147483647L));
        assertError(409, SchedulingRules.NOT_ACCEPTED, schedule("R1", "S1", submitted));
        assertEquals(taken, talkIn("R1", "S1"));
    }

    @Test
    void aCoSpeakerAddedAfterwardsChangesNothingOnTheProgramme() throws Exception {
        long first = acceptedTalk("PRESENTATION");
        long secondSpeaker = newSpeaker();
        long second = acceptedTalk(secondSpeaker, "PRESENTATION");
        assertEquals(200, schedule("R1", "S4", first).statusCode());
        assertEquals(200, schedule("R2", "S4", second).statusCode());

        String addCoSpeaker = "/talks/" + first + "/co-speakers/" + secondSpeaker;
        assertEquals(200, send("PUT", addCoSpeaker, null, null).statusCode());

        assertEquals(first, talkIn("R1", "S4"));
        assertEquals(second, talkIn("R2", "S4"));
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    void takingATalkOutLeavesThePlaceEmpty() throws Exception {
        assertEquals(200, schedule("R2", "S3", acceptedTalk("PRESENTATION")).statusCode());
        HttpResponse<String> res = send("DELETE", "/programme/R2/S3", null, null);
        assertEquals(204, res.statusCode(), res.body());
        assertEquals("", res.body());
        assertEquals(404, send("GET", "/programme/R2/S3", null, null).statusCode());
    }

    @Test
    void takingOutOfAnEmptyPlaceIsNotFound() throws Exception {
        assertEquals(404, send("DELETE", "/programme/R1/S1", null, null).statusCode());
    }

    @Test
    void aTalkTakenOutCanGoSomewhereElse() throws Exception {
        long talk = acceptedTalk("PRESENTATION");
        assertEquals(200, schedule("R1", "S1", talk).statusCode());
        assertEquals(204, send("DELETE", "/programme/R1/S1", null, null).statusCode());
        assertEquals(200, schedule("R3", "S2", talk).statusCode());
        assertEquals(talk, talkIn("R3", "S2"));
        assertEquals(1, entries());
    }

    // ---------------------------------------------------------------- concurrency (D19)

    @Test
    void concurrentPutsIntoOnePlace() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            emptyProgramme();
            List<Long> talks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                talks.add(acceptedTalk("PRESENTATION"));
            }
            List<HttpResponse<String>> responses = all(talks.stream().map(talk -> scheduleAsync("R1", "S1", talk)));
            assertOneWinner(responses, SchedulingRules.TAKEN);
            long winner = JSON.readTree(winner(responses).body()).get("talkId").asLong();
            assertEquals(winner, talkIn("R1", "S1"));
            assertEquals(1, entries());
        }
    }

    @Test
    void concurrentPutsOfOneTalkIntoTwoPlaces() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            emptyProgramme();
            long talk = acceptedTalk("PRESENTATION");
            assertOneWinner(all(Stream.of(scheduleAsync("R1", "S1", talk), scheduleAsync("R2", "S2", talk))),
                    SchedulingRules.ELSEWHERE);
            assertEquals(1, entries());
        }
    }

    @Test
    void concurrentSharedSpeakerInOneSlot() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            emptyProgramme();
            long speaker = newSpeaker();
            long first = acceptedTalk(speaker, "PRESENTATION");
            long second = acceptedTalk(speaker, "PRESENTATION");
            assertOneWinner(all(Stream.of(scheduleAsync("R1", "S3", first), scheduleAsync("R2", "S3", second))),
                    SchedulingRules.CLASH);
            assertEquals(1, entries());
        }
    }

    @Test
    void concurrentSamePutTwice() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            emptyProgramme();
            long talk = acceptedTalk("PRESENTATION");
            List<HttpResponse<String>> responses =
                    all(Stream.of(scheduleAsync("R1", "S2", talk), scheduleAsync("R1", "S2", talk)));
            for (HttpResponse<String> res : responses) {
                assertEquals(200, res.statusCode(), res.body());
            }
            assertEquals(1, entries());
        }
    }

    @Test
    void lockingASlotFailsLoudlyWithoutItsSeedRow() {
        jdbc.update("DELETE FROM programme_slot WHERE slot = 'S5'");
        try {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> programmeDao.lockSlot(Slot.S5));
            assertTrue(e.getMessage().contains("S5"), e.getMessage());
        } finally {
            jdbc.update("INSERT INTO programme_slot (slot) VALUES ('S5')");
        }
    }

    // ---------------------------------------------------------------- helpers

    private CompletableFuture<HttpResponse<String>> scheduleAsync(String room, String slot, long talk) {
        return http.sendAsync(request("PUT", "/programme/" + room + "/" + slot, talkIdBody(talk), "application/json"),
                HttpResponse.BodyHandlers.ofString());
    }

    private static List<HttpResponse<String>> all(Stream<CompletableFuture<HttpResponse<String>>> sent) {
        return sent.toList().stream().map(CompletableFuture::join).toList();
    }

    /**
     * Exactly one 200; every other response is the 409 it would get if it came second (D19).
     */
    private static void assertOneWinner(List<HttpResponse<String>> responses, String loserReason) {
        assertEquals(1, responses.stream().filter(res -> res.statusCode() == 200).count(), bodies(responses));
        responses.stream()
                .filter(res -> res.statusCode() != 200)
                .forEach(res -> assertError(409, loserReason, res));
    }

    private static HttpResponse<String> winner(List<HttpResponse<String>> responses) {
        return responses.stream().filter(res -> res.statusCode() == 200).findFirst().orElseThrow();
    }

    private static String bodies(List<HttpResponse<String>> responses) {
        return responses.stream().map(res -> res.statusCode() + " " + res.body()).toList().toString();
    }

    private static String talkIdBody(long talk) {
        return "{\"talkId\":" + talk + "}";
    }

    private HttpResponse<String> schedule(String room, String slot, long talk) throws Exception {
        return send("PUT", "/programme/" + room + "/" + slot, talkIdBody(talk), "application/json");
    }

    private long talkIn(String room, String slot) throws Exception {
        HttpResponse<String> res = send("GET", "/programme/" + room + "/" + slot, null, null);
        assertEquals(200, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("talkId").asLong();
    }

    private int entries() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM programme_entry", Integer.class);
    }

    private static void assertError(int status, String message, HttpResponse<String> res) {
        assertEquals(status, res.statusCode(), res.body());
        assertEquals(message, JSON.readTree(res.body()).get("message").asString(), res.body());
    }

    private long acceptedTalk(String format) throws Exception {
        return acceptedTalk(newSpeaker(), format);
    }

    private long acceptedTalk(long speaker, String format) throws Exception {
        long talk = newTalk(speaker, format);
        for (int reviewer = 1; reviewer <= 2; reviewer++) {
            HttpResponse<String> res = send("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":4}",
                    "application/json");
            assertEquals(200, res.statusCode(), res.body());
        }
        assertEquals(200, decide(talk, "ACCEPTED").statusCode());
        return talk;
    }

    private HttpResponse<String> decide(long talk, String status) throws Exception {
        return send("PUT", "/talks/" + talk + "/status", "{\"status\":\"" + status + "\"}", "application/json");
    }

    private HttpResponse<String> withdraw(long talk, long speaker) throws Exception {
        return send("POST", "/talks/" + talk + "/withdraw", "{\"speakerId\":" + speaker + "}", "application/json");
    }

    private long newSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}",
                "application/json");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private long newTalk(long speaker, String format) throws Exception {
        HttpResponse<String> res = send("POST", "/talks",
                "{\"title\":\"Talk\",\"format\":\"" + format + "\",\"level\":\"BEGINNER\","
                        + "\"speakerId\":" + speaker + "}",
                "application/json");
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        return http.send(request(method, path, body, contentType), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String body, String contentType) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return request.build();
    }
}
