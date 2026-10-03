package com.chat.agent.config;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets a browser on another origin call this API.
 *
 * Off unless cors.allowed-origins lists something, so local development is untouched: there
 * the Vite dev server proxies /chat, the browser stays on one origin, and CORS never applies.
 * Only set this when the UI is genuinely served from a different host or port than the API.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private final List<String> allowedOrigins;

    public CorsConfig(@Value("${cors.allowed-origins:http://analytics-agent-frontend.s3-website-us-east-1.amazonaws.com/, http://localhost:5173}") String allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.isEmpty()) {
            log.info("CORS disabled: the UI is expected on the same origin as this API");
            return;
        }

        log.info("CORS enabled for {}", allowedOrigins);
        registry.addMapping("/**")
                // exact origins, never "*": a wildcard would let any site on the internet
                // read this data from a signed-in user's browser
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type")
                .maxAge(3600);
    }
}
