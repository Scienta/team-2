package no.scienta.cfp;

import java.io.IOException;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One log line per request (method, path, status, duration), so the harness's traffic shows up in the container logs.
 * Bodies and headers are left out.
 */
@Component
public class RequestLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean thrown = true;
        try {
            chain.doFilter(request, response);
            thrown = false;
        } finally {
            String query = request.getQueryString();
            String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
            // An exception escaping the chain becomes a 500 later, after this filter has returned.
            int status = thrown ? 500 : response.getStatus();
            long millis = (System.nanoTime() - start) / 1_000_000;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), path, status, millis);
        }
    }
}
