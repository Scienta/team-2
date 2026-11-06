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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Every talk in a list carries its own rating count and average, against a real database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TalkListRatingsTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void listsShowEachTalksOwnRatingsAndRankThem() throws Exception {
        String speaker = idOf(send("POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}"));
        String unrated = newTalk(speaker);
        String halfUp = newTalk(speaker);
        String top = newTalk(speaker);
        rate(halfUp, 1, 4);
        rate(halfUp, 2, 4);
        rate(halfUp, 3, 4);
        rate(halfUp, 4, 5);
        rate(top, 1, 5);

        for (String path : Arrays.asList("/talks?speakerId=" + speaker, "/speakers/" + speaker + "/talks")) {
            String body = send("GET", path, null).body();
            assertEquals(Arrays.asList(unrated, halfUp, top), ids(body), body);
            assertRatings(body, unrated, "null", 0);
            assertRatings(body, halfUp, "4.3", 4);
            assertRatings(body, top, "5.0", 1);
        }

        String ranked = send("GET", "/talks?sort=rating&speakerId=" + speaker, null).body();
        assertEquals(Arrays.asList(top, halfUp, unrated), ids(ranked), ranked);
    }

    private static void assertRatings(String list, String talk, String average, int count) {
        Matcher m = Pattern.compile("\"id\":" + talk + ",[^}]*\"averageRating\":" + Pattern.quote(average)
                + ",\"ratingCount\":" + count + "[,}]").matcher(list);
        assertTrue(m.find(), "talk " + talk + " should have average " + average + " and count " + count + ": " + list);
    }

    private static List<String> ids(String list) {
        List<String> ids = new ArrayList<>();
        Matcher m = ID.matcher(list);
        while (m.find()) {
            ids.add(m.group(1));
        }
        return ids;
    }

    private String newTalk(String speaker) throws Exception {
        return idOf(send("POST", "/talks",
                "{\"title\":\"A talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}"));
    }

    private void rate(String talk, int reviewer, int score) throws Exception {
        HttpResponse<String> res = send("PUT", "/talks/" + talk + "/ratings/" + reviewer, "{\"score\":" + score + "}");
        assertEquals(200, res.statusCode(), res.body());
    }

    private static String idOf(HttpResponse<String> res) {
        assertEquals(201, res.statusCode(), res.body());
        Matcher m = ID.matcher(res.body());
        assertTrue(m.find(), res.body());
        return m.group(1);
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
