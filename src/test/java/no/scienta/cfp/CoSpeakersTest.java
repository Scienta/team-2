package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Up to two co-speakers on a talk, shown in the order they were added. Status codes for the cases the spec leaves open
 * are the decisions in the co-speakers plan.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CoSpeakersTest {

    private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
    private static final long NO_SUCH_ID = 999999;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void aNewTalkHasNoCoSpeakers() throws Exception {
        long main = newSpeaker();
        HttpResponse<String> created = send("POST", "/talks", talkJson(main));
        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.body().contains("\"coSpeakerIds\":[]"), created.body());
    }

    @Test
    void coSpeakersShowInTheOrderTheyWereAdded() throws Exception {
        long main = newSpeaker();
        long talk = newTalk(main);
        long first = newSpeaker();
        long second = newSpeaker();

        assertCoSpeakers(send("PUT", path(talk, second), null), second);
        assertCoSpeakers(send("PUT", path(talk, first), null), second, first);
        assertCoSpeakers(send("GET", "/talks/" + talk, null), second, first);
        assertTrue(send("GET", "/talks", null).body().contains("\"coSpeakerIds\":[" + second + "," + first + "]"));
    }

    @Test
    void addingAnExistingCoSpeakerChangesNothing() throws Exception {
        long talk = newTalk(newSpeaker());
        long first = newSpeaker();
        long second = newSpeaker();
        send("PUT", path(talk, first), null);
        send("PUT", path(talk, second), null);

        assertCoSpeakers(send("PUT", path(talk, first), null), first, second);
    }

    @Test
    void aThirdCoSpeakerIsRefused() throws Exception {
        long talk = newTalk(newSpeaker());
        long first = newSpeaker();
        long second = newSpeaker();
        send("PUT", path(talk, first), null);
        send("PUT", path(talk, second), null);

        assertEquals(409, send("PUT", path(talk, newSpeaker()), null).statusCode());
        assertCoSpeakers(send("GET", "/talks/" + talk, null), first, second);
    }

    @Test
    void theMainSpeakerCannotCoSpeakOnTheirOwnTalk() throws Exception {
        long main = newSpeaker();
        long talk = newTalk(main);

        assertEquals(400, send("PUT", path(talk, main), null).statusCode());
        assertCoSpeakers(send("GET", "/talks/" + talk, null));
    }

    @Test
    void unknownTalkOrSpeakerIsNotFound() throws Exception {
        long talk = newTalk(newSpeaker());
        long speaker = newSpeaker();

        assertEquals(404, send("PUT", path(NO_SUCH_ID, speaker), null).statusCode());
        assertEquals(404, send("PUT", path(talk, NO_SUCH_ID), null).statusCode());
        assertEquals(404, send("DELETE", path(NO_SUCH_ID, speaker), null).statusCode());
        assertEquals(404, send("DELETE", path(talk, NO_SUCH_ID), null).statusCode());
    }

    @Test
    void removedCoSpeakerFreesTheSlotAndGoesLastWhenAddedAgain() throws Exception {
        long talk = newTalk(newSpeaker());
        long first = newSpeaker();
        long second = newSpeaker();
        send("PUT", path(talk, first), null);
        send("PUT", path(talk, second), null);

        assertCoSpeakers(send("DELETE", path(talk, first), null), second);
        assertCoSpeakers(send("PUT", path(talk, first), null), second, first);
    }

    @Test
    void removingSomeoneWhoIsNotACoSpeakerIsNotFound() throws Exception {
        long talk = newTalk(newSpeaker());
        long first = newSpeaker();
        send("PUT", path(talk, first), null);

        assertEquals(404, send("DELETE", path(talk, newSpeaker()), null).statusCode());
        assertCoSpeakers(send("GET", "/talks/" + talk, null), first);
    }

    @Test
    void coSpeakingDoesNotCountAsTheSpeakersOwnTalks() throws Exception {
        long busy = newSpeaker();
        long ownTalk = newTalk(busy);
        newTalk(busy);
        newTalk(busy);
        long otherTalk = newTalk(newSpeaker());

        assertCoSpeakers(send("PUT", path(otherTalk, busy), null), busy);

        assertEquals(409, send("POST", "/talks", talkJson(busy)).statusCode());
        String own = send("GET", "/speakers/" + busy + "/talks", null).body();
        assertEquals(3, count(own), own);
        assertTrue(own.contains("\"id\":" + ownTalk), own);
        assertTrue(!own.contains("\"id\":" + otherTalk + ","), own);
    }

    @Test
    void aCoSpeakerStillHasRoomForTheirOwnTalks() throws Exception {
        long speaker = newSpeaker();
        assertCoSpeakers(send("PUT", path(newTalk(newSpeaker()), speaker), null), speaker);
        assertCoSpeakers(send("PUT", path(newTalk(newSpeaker()), speaker), null), speaker);
        assertCoSpeakers(send("PUT", path(newTalk(newSpeaker()), speaker), null), speaker);

        newTalk(speaker);
        newTalk(speaker);
        newTalk(speaker);
    }

    @Test
    void concurrentAddsNeverExceedTwo() throws Exception {
        for (int round = 0; round < 5; round++) {
            long talk = newTalk(newSpeaker());
            List<Long> candidates = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                candidates.add(newSpeaker());
            }
            List<CompletableFuture<HttpResponse<String>>> sent = new ArrayList<>();
            for (long candidate : candidates) {
                sent.add(http.sendAsync(request("PUT", path(talk, candidate), null), HttpResponse.BodyHandlers.ofString()));
            }
            int added = 0;
            for (CompletableFuture<HttpResponse<String>> f : sent) {
                HttpResponse<String> res = f.join();
                assertTrue(res.statusCode() == 200 || res.statusCode() == 409, res.statusCode() + " " + res.body());
                if (res.statusCode() == 200) {
                    added++;
                }
            }
            assertEquals(2, added);
            assertEquals(2, coSpeakers(send("GET", "/talks/" + talk, null).body()).size());
        }
    }

    private void assertCoSpeakers(HttpResponse<String> res, long... expected) {
        assertEquals(200, res.statusCode(), res.body());
        List<Long> want = new ArrayList<>();
        for (long id : expected) {
            want.add(id);
        }
        assertEquals(want, coSpeakers(res.body()));
    }

    private static List<Long> coSpeakers(String talkJson) {
        Matcher m = Pattern.compile("\"coSpeakerIds\":\\[([0-9,]*)\\]").matcher(talkJson);
        assertTrue(m.find(), talkJson);
        List<Long> ids = new ArrayList<>();
        if (!m.group(1).isEmpty()) {
            for (String id : m.group(1).split(",")) {
                ids.add(Long.parseLong(id));
            }
        }
        return ids;
    }

    private static String path(long talk, long speaker) {
        return "/talks/" + talk + "/co-speakers/" + speaker;
    }

    private static String talkJson(long speaker) {
        return "{\"title\":\"A talk\",\"format\":\"PRESENTATION\",\"level\":\"BEGINNER\",\"speakerId\":" + speaker + "}";
    }

    private long newSpeaker() throws Exception {
        return idOf(send("POST", "/speakers",
                "{\"name\":\"Grace Hopper\",\"email\":\"grace-" + UUID.randomUUID() + "@example.com\"}"));
    }

    private long newTalk(long speaker) throws Exception {
        return idOf(send("POST", "/talks", talkJson(speaker)));
    }

    private static long idOf(HttpResponse<String> res) {
        assertEquals(201, res.statusCode(), res.body());
        Matcher m = ID.matcher(res.body());
        assertTrue(m.find(), res.body());
        return Long.parseLong(m.group(1));
    }

    private static int count(String body) {
        Matcher m = ID.matcher(body);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private HttpResponse<String> send(String method, String path, String json) throws Exception {
        return http.send(request(method, path, json), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest request(String method, String path, String json) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                .build();
    }
}
