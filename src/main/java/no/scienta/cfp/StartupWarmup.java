package no.scienta.cfp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Warms a freshly started instance up by sending it requests over loopback, so the harness's first burst after a
 * deploy doesn't meet a cold JVM (classes not loaded, nothing compiled, pool not used) and time out.
 * Every request is read-only or rejected before it reaches the database: the warm-up never writes.
 */
@Component
public class StartupWarmup {

    private static final Logger log = LoggerFactory.getLogger(StartupWarmup.class);

    private static final List<String[]> REQUESTS = List.of(
            new String[] {"GET", "/health", null},
            new String[] {"GET", "/speakers/0", null},
            new String[] {"GET", "/talks/0", null},
            new String[] {"GET", "/talks?speakerId=0", null},
            new String[] {"POST", "/speakers", "{\"name\":\"\",\"email\":\"warm-up\"}"},
            new String[] {"POST", "/talks", "{}"});

    private final Environment environment;
    private final int rounds;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public StartupWarmup(Environment environment, @Value("${cfp.warmup.rounds:30}") int rounds) {
        this.environment = environment;
        this.rounds = rounds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Integer port = environment.getProperty("local.server.port", Integer.class);
        if (port != null && rounds > 0) {
            warmUp("http://localhost:" + port);
        }
    }

    /** Sends {@code rounds} rounds of the warm-up requests; returns how many got an answer. Never throws. */
    int warmUp(String baseUrl) {
        long started = System.nanoTime();
        int answered = 0;
        try {
            for (int i = 0; i < rounds; i++) {
                for (String[] r : REQUESTS) {
                    HttpRequest.BodyPublisher body = r[2] == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(r[2]);
                    HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + r[1]))
                            .timeout(Duration.ofSeconds(10))
                            .header("Content-Type", "application/json")
                            .method(r[0], body)
                            .build();
                    http.send(request, HttpResponse.BodyHandlers.discarding());
                    answered++;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("warm-up stopped after {} requests: {}", answered, e.toString());
        }
        log.info("warm-up: {} requests in {} ms", answered, (System.nanoTime() - started) / 1_000_000);
        return answered;
    }
}
