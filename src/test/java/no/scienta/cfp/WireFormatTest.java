package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.SpringVersion;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Pins what users see on the wire, so a framework upgrade can't change it unnoticed: JSON keys and their order, the
 * date formats the talks spec fixes, error bodies, trailing slashes and lenient JSON parsing. Asserts on raw response
 * strings, not a JSON library, so it doesn't change when the JSON library does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WireFormatTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final Pattern KEY = Pattern.compile("\"(\\w+)\":");
    private static final String TIMESTAMP = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00";
    private static final List<String> TALK_KEYS = Arrays.asList("id", "title", "format", "level", "speakerId",
            "durationMinutes", "status", "submittedAt", "submittedOn", "averageRating", "ratingCount", "coSpeakerIds");
    private static final List<String> ERROR_KEYS = Arrays.asList("timestamp", "status", "error", "message", "path");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void talkKeysAndDateFormats() throws Exception {
        String speaker = newSpeaker();
        HttpResponse<String> created = send("POST", "/talks", talk(speaker, "PRESENTATION"));
        assertEquals(201, created.statusCode(), created.body());
        String talk = id(created.body());
        assertTalk(created.body());
        assertTalk(send("GET", "/talks/" + talk, null).body());
        assertTalk(listed(send("GET", "/talks?speakerId=" + speaker, null).body()));
        assertTalk(listed(send("GET", "/speakers/" + speaker + "/talks", null).body()));
    }

    @Test
    void speakerAndRatingKeys() throws Exception {
        HttpResponse<String> created = send("POST", "/speakers", "{\"name\":\"Ada\",\"email\":\"" + email() + "\"}");
        assertEquals(Arrays.asList("id", "name", "email", "bio"), keys(created.body()));
        assertTrue(created.body().endsWith(",\"bio\":null}"), created.body());
        String talk = id(send("POST", "/talks", talk(id(created.body()), "WORKSHOP")).body());
        HttpResponse<String> rated = send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":4}");
        assertEquals(200, rated.statusCode(), rated.body());
        assertEquals(Arrays.asList("talkId", "reviewerId", "score", "ratedAtDisplay"), keys(rated.body()));
        assertTrue(send("GET", "/talks/" + talk, null).body().contains("\"averageRating\":4.0,\"ratingCount\":1,"));
    }

    @Test
    void jsonIsUtf8() throws Exception {
        HttpResponse<String> res = send("GET", "/speakers/" + newSpeaker(), null);
        assertEquals("application/json;charset=UTF-8", res.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    void unknownPropertiesAreIgnored() throws Exception {
        String body = "{\"title\":\"T\",\"format\":\"LIGHTNING_10\",\"level\":\"BEGINNER\",\"speakerId\":" + newSpeaker()
                + ",\"durationMinutes\":999}";
        HttpResponse<String> res = send("POST", "/talks", body);
        assertEquals(201, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"durationMinutes\":10,"), res.body());
    }

    @Test
    void commentsAndTrailingTokensAreAccepted() throws Exception {
        HttpResponse<String> created = send("POST", "/speakers",
                "/* from the mobile client */ {\"name\":\"Ada\",\"email\":\"" + email() + "\"}");
        assertEquals(201, created.statusCode(), created.body());
        String talk = id(send("POST", "/talks", talk(id(created.body()), "WORKSHOP")).body());
        HttpResponse<String> rated = send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":3} x");
        assertEquals(200, rated.statusCode(), rated.body());
    }

    // speakers spec: /speakers/ and /speakers/{id}/ work just like the URLs without one
    @Test
    void trailingSlashesWorkLikeWithout() throws Exception {
        HttpResponse<String> created = send("POST", "/speakers/", "{\"name\":\"Ada\",\"email\":\"" + email() + "\"}");
        assertEquals(201, created.statusCode(), created.body());
        String speaker = id(created.body());
        String talk = id(send("POST", "/talks", talk(speaker, "WORKSHOP")).body());
        for (String path : Arrays.asList("/speakers/" + speaker, "/speakers/" + speaker + "/talks", "/talks/" + talk)) {
            HttpResponse<String> without = send("GET", path, null);
            HttpResponse<String> with = send("GET", path + "/", null);
            assertEquals(200, with.statusCode(), path + "/ " + with.body());
            assertEquals(without.body(), with.body(), path + "/");
        }
    }

    @Test
    void ourErrorReasonsAreExact() throws Exception {
        String speaker = newSpeaker();
        String talk = id(send("POST", "/talks", talk(speaker, "WORKSHOP")).body());
        assertError(send("GET", "/talks/999999", null), 404, "talk not found");
        assertError(send("POST", "/talks", talk(speaker, "KEYNOTE")), 400,
                "format must be one of [WORKSHOP, PRESENTATION, LIGHTNING_20, LIGHTNING_10]");
        assertError(send("PUT", "/talks/" + talk + "/co-speakers/" + speaker, null), 400,
                "the main speaker can't also be a co-speaker");
        assertError(send("PUT", "/talks/" + talk + "/ratings/1", "{\"score\":6}"), 400, "score must be 1-5");
    }

    @Test
    void frameworkErrorsKeepStatusAndKeys() throws Exception {
        String talk = id(send("POST", "/talks", talk(newSpeaker(), "WORKSHOP")).body());
        assertError(send("POST", "/talks", "not json"), 400, null);
        assertError(send("PUT", "/talks/" + talk + "/ratings/1", null), 400, null);
        assertError(http.send(HttpRequest.newBuilder(uri("/talks")).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString()), 415, null);
        assertError(send("GET", "/talks/abc", null), 400, null);
        assertError(send("DELETE", "/talks", null), 405, null);
        assertError(send("GET", "/nope", null), 404, null);
    }

    @Test
    void validationErrorsListTheField() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers", "{\"name\":\"\",\"email\":\"" + email() + "\"}");
        assertEquals(400, res.statusCode(), res.body());
        assertTrue(res.body().matches("\\{\"timestamp\":\"" + TIMESTAMP + "\",\"status\":400,\"error\":\"Bad Request\","
                + "\"message\":\".*\",\"errors\":\\[.*\\],\"path\":\"/speakers\"}"), res.body());
        assertTrue(res.body().contains("\"defaultMessage\":\"must not be blank\",\"objectName\":\"speakerForm\","
                + "\"field\":\"name\",\"rejectedValue\":\"\""), res.body());
    }

    // feature request spring-boot-4: the reported versions must be the running ones, never hard-coded
    @Test
    void actuatorInfoReportsTheRunningVersions() throws Exception {
        HttpResponse<String> res = send("GET", "/actuator/info", null);
        assertEquals(200, res.statusCode());
        assertEquals("{\"versions\":{\"java\":\"" + System.getProperty("java.version") + "\",\"springBoot\":\""
                + SpringBootVersion.getVersion() + "\",\"springFramework\":\"" + SpringVersion.getVersion() + "\"}}",
                res.body());
    }

    private static void assertTalk(String body) {
        assertEquals(TALK_KEYS, keys(body), body);
        Matcher m = Pattern.compile("\"submittedAt\":\"(" + TIMESTAMP + ")\",\"submittedOn\":\"(\\d{4}-\\d{2}-\\d{2})\"")
                .matcher(body);
        assertTrue(m.find(), body);
        assertEquals(m.group(1).substring(0, 10), m.group(2), body);
        assertTrue(body.contains("\"averageRating\":null,\"ratingCount\":0,\"coSpeakerIds\":[]"), body);
    }

    private static void assertError(HttpResponse<String> res, int status, String message) {
        assertEquals(status, res.statusCode(), res.body());
        assertEquals(ERROR_KEYS, keys(res.body()), res.body());
        assertTrue(res.body().matches("\\{\"timestamp\":\"" + TIMESTAMP + "\",\"status\":" + status + ",.*"), res.body());
        if (message != null) {
            assertTrue(res.body().contains("\"message\":\"" + message + "\","), res.body());
        }
    }

    private static String listed(String body) {
        assertTrue(body.startsWith("[{") && body.endsWith("}]") && !body.contains("},{"), body);
        return body.substring(1, body.length() - 1);
    }

    private static List<String> keys(String body) {
        List<String> keys = new ArrayList<>();
        Matcher m = KEY.matcher(body);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static String talk(String speaker, String format) {
        return "{\"title\":\"Talk\",\"format\":\"" + format + "\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}";
    }

    private static String email() {
        return "ada-" + UUID.randomUUID() + "@example.com";
    }

    private String newSpeaker() throws Exception {
        HttpResponse<String> res = send("POST", "/speakers", "{\"name\":\"Ada\",\"email\":\"" + email() + "\"}");
        assertEquals(201, res.statusCode(), res.body());
        return id(res.body());
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
