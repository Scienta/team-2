package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Workshop registration against workflow/specs/workshop-registration.md and its decisions D1 to D18. All tests share one
 * database, so each creates its own speakers and talks (a speaker has at most 3 talks).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkshopRegistrationTest {

    private static final Logger log = LoggerFactory.getLogger(WorkshopRegistrationTest.class);
    private static final long MISSING_TALK = 2147483647L;
    private static final ObjectMapper JSON = new ObjectMapper();

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
    void registersAndReturnsTheRegistration() throws Exception {
        long workshop = acceptedWorkshop();
        HttpResponse<String> res = register(workshop, "Ada Lovelace", "ada@example.com");
        assertEquals(201, res.statusCode(), res.body());
        assertJson(res);
        JsonNode body = JSON.readTree(res.body());
        assertTrue(body.get("id").isIntegralNumber() && body.get("id").asLong() > 0, res.body());
        assertTrue(body.get("workshopId").isIntegralNumber(), res.body());
        assertEquals(workshop, body.get("workshopId").asLong());
        assertEquals("Ada Lovelace", body.get("name").asText());
        assertEquals("ada@example.com", body.get("email").asText());
        assertEquals(4, body.size(), res.body());
    }

    @Test
    void listIsInRegistrationOrder() throws Exception {
        long workshop = acceptedWorkshop();
        long first = idOf(register(workshop, "Ada", "ada@example.com"));
        long second = idOf(register(workshop, "Bob", "bob@example.com"));
        long third = idOf(register(workshop, "Cyd", "cyd@example.com"));

        assertJson(get(workshop));
        JsonNode list = list(workshop);
        assertEquals(3, list.size(), list.toString());
        assertEquals(first, list.get(0).get("id").asLong());
        assertEquals(second, list.get(1).get("id").asLong());
        assertEquals(third, list.get(2).get("id").asLong());
        assertEquals("Bob", list.get(1).get("name").asText());
        assertEquals(workshop, list.get(1).get("workshopId").asLong());
        assertEquals("bob@example.com", list.get(1).get("email").asText());
    }

    @Test
    void emailIsReturnedAsTyped() throws Exception {
        long workshop = acceptedWorkshop();
        String typed = "  ADA@Example.com ";
        HttpResponse<String> res = register(workshop, "Ada", typed);
        assertEquals(201, res.statusCode(), res.body());
        assertEquals(typed, JSON.readTree(res.body()).get("email").asText());
        assertEquals(typed, list(workshop).get(0).get("email").asText());
    }

    @Test
    void unknownTalkIs404() throws Exception {
        assertEquals(404, register(MISSING_TALK, "Ada", "ada@example.com").statusCode());
        assertEquals(404, get(MISSING_TALK).statusCode());
    }

    @Test
    void talkThatIsNotAWorkshopIs404() throws Exception {
        long talk = newTalk(newSpeaker(), "PRESENTATION");
        setStatus(talk, "ACCEPTED");
        assertEquals(404, register(talk, "Ada", "ada@example.com").statusCode());
        assertEquals(404, get(talk).statusCode());
    }

    @Test
    void workshopThatIsNotAcceptedIs409() throws Exception {
        for (String status : new String[] {"SUBMITTED", "WAITLISTED", "REJECTED"}) {
            long workshop = newTalk(newSpeaker(), "WORKSHOP");
            setStatus(workshop, status);
            assertEquals(409, register(workshop, "Ada", "ada@example.com").statusCode(), status);
            HttpResponse<String> listed = get(workshop);
            assertEquals(200, listed.statusCode(), status);
            assertEquals(0, JSON.readTree(listed.body()).size(), status);
        }
    }

    @Test
    void withdrawnWorkshopKeepsItsListAndTakesNoMore() throws Exception {
        long speaker = newSpeaker();
        long workshop = newTalk(speaker, "WORKSHOP");
        assertEquals(200, send("PUT", "/talks/" + workshop + "/ratings/1", "{\"score\":4}", "application/json").statusCode());
        assertEquals(200, send("PUT", "/talks/" + workshop + "/ratings/2", "{\"score\":5}", "application/json").statusCode());
        assertEquals(200, send("PUT", "/talks/" + workshop + "/status", "{\"status\":\"ACCEPTED\"}", "application/json").statusCode());
        assertEquals(201, register(workshop, "Ada", "ada@example.com").statusCode());

        assertEquals(200, send("POST", "/talks/" + workshop + "/withdraw", "{\"speakerId\":" + speaker + "}", "application/json").statusCode());

        assertEquals(409, register(workshop, "Bob", "bob@example.com").statusCode());
        JsonNode list = list(workshop);
        assertEquals(1, list.size(), list.toString());
        assertEquals("Ada", list.get(0).get("name").asText());
    }

    @Test
    void missingOrBlankFieldsAre400() throws Exception {
        long workshop = acceptedWorkshop();
        String email = "\"email\":\"ada@example.com\"";
        String name = "\"name\":\"Ada\"";
        String[] bodies = {
            "{}",
            "{" + email + "}",
            "{" + name + "}",
            "{\"name\":null," + email + "}",
            "{" + name + ",\"email\":null}",
            "{\"name\":\"\"," + email + "}",
            "{" + name + ",\"email\":\"\"}",
            "{\"name\":\"  \"," + email + "}",
            "{" + name + ",\"email\":\"  \"}",
            "{\"name\":\"\\u00A0\"," + email + "}",
            "{" + name + ",\"email\":\"\\u00A0\"}",
            "{\"name\":123," + email + "}",
            "{" + name + ",\"email\":123}",
            "{\"name\":\"a\\u0000\"," + email + "}",
            "{" + name + ",\"email\":\"a\\u0000@example.com\"}",
            "{\"name\":\"a\\ud800\"," + email + "}",
        };
        for (String body : bodies) {
            assertEquals(400, post(workshop, body).statusCode(), body);
        }
        assertEquals(0, list(workshop).size());
    }

    // D6: control characters count after trimming, so edges are fine and the value is still stored as typed
    @Test
    void controlCharactersCountOnlyInsideTheTrimmedValue() throws Exception {
        long workshop = acceptedWorkshop();
        assertEquals(400, post(workshop, "{\"name\":\"A\\tda\",\"email\":\"ada@example.com\"}").statusCode(), "inside the name");
        assertEquals(400, post(workshop, "{\"name\":\"Ada\",\"email\":\"a\\tda@example.com\"}").statusCode(), "inside the email");

        HttpResponse<String> res = register(workshop, "Ada\n", "\tada@example.com\n");
        assertEquals(201, res.statusCode(), res.body());
        JsonNode stored = list(workshop).get(0);
        assertEquals("Ada\n", stored.get("name").asText());
        assertEquals("\tada@example.com\n", stored.get("email").asText());
    }

    // D13: the request gives no limit
    @Test
    void longNameAndEmailAreAccepted() throws Exception {
        long workshop = acceptedWorkshop();
        String name = "N".repeat(10_000);
        String email = "e".repeat(10_000) + "@example.com";
        HttpResponse<String> res = register(workshop, name, email);
        assertEquals(201, res.statusCode(), res.body());
        assertEquals(name, list(workshop).get(0).get("name").asText());
        assertEquals(email, list(workshop).get(0).get("email").asText());
    }

    // D14: a speaker on the workshop can attend it, and an attendee doesn't have to be a speaker
    @Test
    void attendeesAreIndependentOfSpeakers() throws Exception {
        String speakerEmail = "grace-" + UUID.randomUUID() + "@example.com";
        long speaker = idOf(send("POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"" + speakerEmail + "\"}", "application/json"));
        long workshop = newTalk(speaker, "WORKSHOP");
        setStatus(workshop, "ACCEPTED");

        assertEquals(201, register(workshop, "Grace Hopper", speakerEmail).statusCode(), "the workshop's own speaker");
        assertEquals(201, register(workshop, "Not A Speaker", "not-a-speaker-" + UUID.randomUUID() + "@example.com").statusCode());
        assertEquals(2, list(workshop).size());
    }

    // D16: the API has no way to remove a registration, so a seat stays taken
    @Test
    void aRegistrationCantBeRemoved() throws Exception {
        long workshop = acceptedWorkshop();
        long id = idOf(register(workshop, "Ada", "ada@example.com"));
        String path = "/workshops/" + workshop + "/registrations";
        assertEquals(405, send("DELETE", path, null, "application/json").statusCode());
        assertEquals(404, send("DELETE", path + "/" + id, null, "application/json").statusCode());
        assertEquals(1, list(workshop).size());
    }

    @Test
    void malformedEmailIs400() throws Exception {
        long workshop = acceptedWorkshop();
        for (String email : new String[] {"not-an-email", "@example.com", "ada@example.", "ada@.com", "@.", "a b@c.d", "a@b@c.d", "ada@localhost"}) {
            assertEquals(400, register(workshop, "Ada", email).statusCode(), email);
        }
        assertEquals(0, list(workshop).size());
    }

    @Test
    void badBodies() throws Exception {
        long workshop = acceptedWorkshop();
        String path = "/workshops/" + workshop + "/registrations";
        String good = "{\"name\":\"Ada\",\"email\":\"ada@example.com\"}";
        assertEquals(400, send("POST", path, null, "application/json").statusCode(), "no body");
        assertEquals(400, send("POST", path, "not json", "application/json").statusCode(), "not json");
        assertEquals(400, send("POST", path, "[1]", "application/json").statusCode(), "array");
        assertEquals(400, send("POST", path, "null", "application/json").statusCode(), "json null");
        assertEquals(415, send("POST", path, good, "text/plain").statusCode(), "text/plain");
        assertEquals(400, send("POST", "/workshops/abc/registrations", good, "application/json").statusCode(), "id not a number");
        assertEquals(400, send("GET", "/workshops/abc/registrations", null, "application/json").statusCode(), "id not a number");
        assertEquals(400, send("POST", "/workshops/" + MISSING_TALK + "/registrations", "not json", "application/json").statusCode(),
                "not json on a missing talk");
    }

    @Test
    void precedence() throws Exception {
        String badField = "{\"name\":\"\",\"email\":\"ada@example.com\"}";
        assertEquals(404, post(MISSING_TALK, badField).statusCode(), "missing talk before the fields");
        long submitted = newTalk(newSpeaker(), "WORKSHOP");
        assertEquals(400, post(submitted, badField).statusCode(), "the fields before the status");
    }

    @Test
    void twentyFitAndTheTwentyFirstIs409() throws Exception {
        long workshop = acceptedWorkshop();
        for (int i = 0; i < 20; i++) {
            assertEquals(201, register(workshop, "Attendee " + i, "a" + i + "@example.com").statusCode());
        }
        assertEquals(409, register(workshop, "One too many", "late@example.com").statusCode());
        assertEquals(20, list(workshop).size());
    }

    @Test
    void sameEmailTwiceIs409() throws Exception {
        long workshop = acceptedWorkshop();
        assertEquals(201, register(workshop, "Ada", "ada@example.com").statusCode());
        for (String email : new String[] {"ada@example.com", "ADA@Example.com", " ada@example.com ", " ada@example.com"}) {
            assertEquals(409, register(workshop, "Ada again", email).statusCode(), email);
        }
        JsonNode list = list(workshop);
        assertEquals(1, list.size(), list.toString());
        assertEquals("Ada", list.get(0).get("name").asText());
    }

    @Test
    void sameEmailOnAnotherWorkshopIsFine() throws Exception {
        assertEquals(201, register(acceptedWorkshop(), "Ada", "ada@example.com").statusCode());
        assertEquals(201, register(acceptedWorkshop(), "Ada", "ada@example.com").statusCode());
    }

    @Test
    void aRushNeverOverfills() throws Exception {
        long workshop = acceptedWorkshop();
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            bodies.add(registrationJson("Attendee " + i, "rush" + i + "@example.com"));
        }
        long start = System.nanoTime();
        List<HttpResponse<String>> responses = postAll(workshop, bodies);
        log.info("a rush of {} took {} ms", bodies.size(), (System.nanoTime() - start) / 1_000_000);

        assertEquals(20, countStatus(responses, 201));
        assertEquals(40, countStatus(responses, 409));
        JsonNode list = list(workshop);
        assertEquals(20, list.size(), list.toString());
        long previous = 0;
        for (JsonNode registration : list) {
            long id = registration.get("id").asLong();
            assertTrue(id > previous, "D15: ascending ids, also after a rush: " + list);
            previous = id;
        }
    }

    @Test
    void aRushWithOneEmailRegistersOnce() throws Exception {
        long workshop = acceptedWorkshop();
        List<String> bodies = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            bodies.add(registrationJson("Ada", i % 2 == 0 ? "ada@example.com" : "ADA@Example.com"));
        }
        List<HttpResponse<String>> responses = postAll(workshop, bodies);
        assertEquals(1, countStatus(responses, 201));
        assertEquals(19, countStatus(responses, 409));
        assertEquals(1, list(workshop).size());
    }

    // Fails if the status comes from before the lock: the withdrawal commits while the registration waits for the row.
    @Test
    void aRegistrationWaitingForTheLockSeesTheWithdrawal() throws Exception {
        long workshop = acceptedWorkshop();
        CompletableFuture<HttpResponse<String>> waiting;
        try (Connection c = jdbc.getDataSource().getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement lock = c.prepareStatement("SELECT id FROM talk WHERE id = ? FOR UPDATE")) {
                lock.setLong(1, workshop);
                lock.executeQuery();
            }
            waiting = http.sendAsync(request("POST", "/workshops/" + workshop + "/registrations",
                    registrationJson("Ada", "ada@example.com"), "application/json"), HttpResponse.BodyHandlers.ofString());
            awaitASessionWaitingForALock();
            try (PreparedStatement withdraw = c.prepareStatement("UPDATE talk SET status = 'WITHDRAWN' WHERE id = ?")) {
                withdraw.setLong(1, workshop);
                withdraw.executeUpdate();
            }
            c.commit();
        }
        HttpResponse<String> res = waiting.get(10, TimeUnit.SECONDS);
        assertEquals(409, res.statusCode(), res.body());
        assertEquals(0, list(workshop).size());
    }

    private void awaitASessionWaitingForALock() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer waiting = jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity "
                    + "WHERE datname = current_database() AND wait_event_type = 'Lock'", Integer.class);
            if (waiting != null && waiting > 0) {
                return;
            }
            Thread.sleep(20);
        }
        fail("the registration never waited for the lock");
    }

    private List<HttpResponse<String>> postAll(long workshop, List<String> bodies) throws Exception {
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (String body : bodies) {
            futures.add(http.sendAsync(request("POST", "/workshops/" + workshop + "/registrations", body, "application/json"),
                    HttpResponse.BodyHandlers.ofString()));
        }
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (CompletableFuture<HttpResponse<String>> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        return responses;
    }

    private static long countStatus(List<HttpResponse<String>> responses, int status) {
        return responses.stream().filter(r -> r.statusCode() == status).count();
    }

    private static void assertJson(HttpResponse<String> res) {
        String contentType = res.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("application/json"), contentType);
    }

    private long acceptedWorkshop() throws Exception {
        long workshop = newTalk(newSpeaker(), "WORKSHOP");
        setStatus(workshop, "ACCEPTED");
        return workshop;
    }

    private void setStatus(long talk, String status) {
        jdbc.update("UPDATE talk SET status = ? WHERE id = ?", status, talk);
    }

    private long newSpeaker() throws Exception {
        return idOf(send("POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}", "application/json"));
    }

    private long newTalk(long speaker, String format) throws Exception {
        return idOf(send("POST", "/talks",
                "{\"title\":\"A talk\",\"format\":\"" + format + "\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}",
                "application/json"));
    }

    private static long idOf(HttpResponse<String> res) throws Exception {
        assertEquals(201, res.statusCode(), res.body());
        return JSON.readTree(res.body()).get("id").asLong();
    }

    private JsonNode list(long workshop) throws Exception {
        HttpResponse<String> res = get(workshop);
        assertEquals(200, res.statusCode(), res.body());
        return JSON.readTree(res.body());
    }

    private HttpResponse<String> get(long workshop) throws Exception {
        return send("GET", "/workshops/" + workshop + "/registrations", null, "application/json");
    }

    private HttpResponse<String> register(long workshop, String name, String email) throws Exception {
        return post(workshop, registrationJson(name, email));
    }

    private static String registrationJson(String name, String email) throws Exception {
        return JSON.writeValueAsString(JSON.createObjectNode().put("name", name).put("email", email));
    }

    private HttpResponse<String> post(long workshop, String body) throws Exception {
        return send("POST", "/workshops/" + workshop + "/registrations", body, "application/json");
    }

    private HttpResponse<String> send(String method, String path, String body, String contentType) throws Exception {
        return http.send(request(method, path, body, contentType), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String body, String contentType) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", contentType)
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
    }
}
