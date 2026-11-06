package no.scienta.cfp;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.UrlHandlerFilter;

@Configuration
public class WebConfig {

    /**
     * The speakers spec wants /speakers/ and /speakers/{id}/ to work like the URLs without the slash. Spring 6 no
     * longer matches them, so the slash is stripped before routing (not redirected: that would break POST).
     */
    @Bean
    public FilterRegistrationBean<UrlHandlerFilter> trailingSlashFilter() {
        FilterRegistrationBean<UrlHandlerFilter> registration =
                new FilterRegistrationBean<>(UrlHandlerFilter.trailingSlashHandler("/**").wrapRequest().build());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Error bodies said application/json;charset=UTF-8 on Boot 2.7. Tomcat 11 resets the charset when an error is
     * forwarded to /error, and Boot's encoding filter skips that dispatch, so it is forced again there.
     */
    @Bean
    public FilterRegistrationBean<CharacterEncodingFilter> errorDispatchEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter("UTF-8", true) {
            @Override
            protected boolean shouldNotFilterErrorDispatch() {
                return false;
            }
        };
        FilterRegistrationBean<CharacterEncodingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setDispatcherTypes(DispatcherType.ERROR);
        return registration;
    }
}
