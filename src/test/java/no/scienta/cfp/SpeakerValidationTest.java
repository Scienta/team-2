package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * "A speaker needs a real name and a real email" (workflow/specs/speakers.md, D1 to D5), on registration and on the
 * admin update, without refusing anything the harness sends.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpeakerValidationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String[] MALFORMED_EMAILS =
            {"not-an-email", "@example.com", "ada@example.", "ada@.com", "@.", "a b@c.d", "a@b@c.d", "ada@localhost"};

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    // The POST /speakers bodies the harness sent (from its feedback transcripts), as sent
    @Test
    void everyBodyTheHarnessSentIsStillAccepted() throws Exception {
        JsonNode bodies;
        try (InputStream in = getClass().getResourceAsStream("/harness/speakers-post-bodies.json")) {
            bodies = JSON.readTree(in);
        }
        assertEquals(23, bodies.size());
        for (JsonNode body : bodies) {
            HttpResponse<String> res = send("POST", "/speakers", body.toString());
            assertEquals(201, res.statusCode(), body + " -> " + res.body());
        }
    }

    @Test
    void aMalformedEmailIs400AndNothingIsStored() throws Exception {
        String marker = "Malformed" + UUID.randomUUID().toString().substring(0, 8);
        for (String email : MALFORMED_EMAILS) {
            HttpResponse<String> res = send("POST", "/speakers", body(json(marker), json(email)));
            assertEquals(400, res.statusCode(), email);
            assertEquals("invalid email", JSON.readTree(res.body()).get("message").asText(), email);
        }
        assertEquals("[]", send("GET", "/speakers/search?q=" + marker, null).body());
    }

    @Test
    void anEmailWithSpacesAtTheEdgesIsStillAnEmailAndStoredAsTyped() throws Exception {
        String typed = "  x-" + UUID.randomUUID() + "@Example.TEST ";
        HttpResponse<String> res = send("POST", "/speakers", body(json("Ada Lovelace"), json(typed)));
        assertEquals(201, res.statusCode(), res.body());
        assertEquals(typed, JSON.readTree(res.body()).get("email").asText());
    }

    @Test
    void aNameOrEmailThatIsNotAStringIs400AndNothingIsStored() throws Exception {
        String email = email();
        for (String name : new String[] {"5", "true", "{\"first\":\"Ada\"}", "[\"Ada\"]"}) {
            assertEquals(400, send("POST", "/speakers", body(name, json(email))).statusCode(), name);
        }
        for (String badEmail : new String[] {"5", "false", "{\"at\":\"example.test\"}"}) {
            assertEquals(400, send("POST", "/speakers", body(json("Ada Lovelace"), badEmail)).statusCode(), badEmail);
        }
        assertEquals(201, send("POST", "/speakers", body(json("Ada Lovelace"), json(email))).statusCode());
    }

    @Test
    void aNameOfOnlyWhitespaceIs400() throws Exception {
        String email = email();
        for (String name : new String[] {"   ", "\t\n", " ", "    "}) {
            HttpResponse<String> res = send("POST", "/speakers", body(json(name), json(email)));
            assertEquals(400, res.statusCode(), "[" + name + "] " + res.body());
        }
        assertEquals(201, send("POST", "/speakers", body(json("Ada Lovelace"), json(email))).statusCode());
    }

    @Test
    void theAdminUpdateHasTheSameRules() throws Exception {
        String email = email();
        HttpResponse<String> created = send("POST", "/speakers", body(json("Ada Lovelace"), json(email)));
        assertEquals(201, created.statusCode(), created.body());
        String path = "/speakers/" + JSON.readTree(created.body()).get("id").asLong();

        for (String bad : MALFORMED_EMAILS) {
            assertEquals(400, send("PUT", path, body(json("Ada Lovelace"), json(bad))).statusCode(), bad);
        }
        assertEquals(400, send("PUT", path, body("5", json(email))).statusCode());
        assertEquals(400, send("PUT", path, body(json("Ada Lovelace"), "5")).statusCode());
        assertEquals(400, send("PUT", path, body(json(" "), json(email))).statusCode());
        assertEquals(400, send("PUT", path, "{\"name\":\"Ada Lovelace\",\"email\":\"" + email + "\",\"bio\":5}").statusCode());

        HttpResponse<String> unchanged = send("GET", path, null);
        assertEquals("Ada Lovelace", JSON.readTree(unchanged.body()).get("name").asText());
        assertEquals(email, JSON.readTree(unchanged.body()).get("email").asText());

        HttpResponse<String> fixed = send("PUT", path, body(json("Ada King"), json(email)));
        assertEquals(200, fixed.statusCode(), fixed.body());
        assertEquals("Ada King", JSON.readTree(fixed.body()).get("name").asText());
    }

    private static String email() {
        return "x-" + UUID.randomUUID() + "@example.test";
    }

    private static String json(String s) {
        return JSON.writeValueAsString(s);
    }

    private static String body(String nameJson, String emailJson) {
        return "{\"name\":" + nameJson + ",\"email\":" + emailJson + "}";
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
