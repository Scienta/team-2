package no.scienta.cfp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        TestDatabase.fresh(registry);
    }

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void logsMethodPathAndStatusOfEveryRequest(CapturedOutput output) throws Exception {
        assertEquals(200, get("/health?probe=1"));
        assertEquals(404, get("/no-such-path"));

        assertTrue(output.getOut().contains("GET /health?probe=1 -> 200 ("), output.getOut());
        assertTrue(output.getOut().contains("GET /no-such-path -> 404 ("), output.getOut());
    }

    @Test
    void logsAnEscapingExceptionAsA500(CapturedOutput output) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/talks");
        FilterChain failing = (rq, rs) -> {
            throw new ServletException("boom");
        };

        assertThrows(ServletException.class, () -> new RequestLogFilter().doFilter(req, new MockHttpServletResponse(), failing));

        assertTrue(output.getOut().contains("POST /talks -> 500 ("), output.getOut());
    }

    private int get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
