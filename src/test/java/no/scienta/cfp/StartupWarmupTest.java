package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.ServerSocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The start-up warm-up: every request gets an answer, nothing is written, and an unreachable server doesn't stop the
 * app. It already ran once when this context started.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "cfp.warmup.rounds=3")
class StartupWarmupTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    @Autowired
    StartupWarmup warmup;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyWarmUpRequestIsAnsweredAndNothingIsWritten() {
        assertEquals(3 * 6, warmup.warmUp("http://localhost:" + port));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM speaker", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM talk", Integer.class));
    }

    @Test
    void anUnreachableServerStopsTheWarmUpWithoutThrowing() throws Exception {
        int closed;
        try (ServerSocket s = new ServerSocket(0)) {
            closed = s.getLocalPort();
        }
        assertEquals(0, warmup.warmUp("http://localhost:" + closed));
    }
}
