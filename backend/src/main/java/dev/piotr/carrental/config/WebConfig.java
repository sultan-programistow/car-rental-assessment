package dev.piotr.carrental.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is OFF by default: in local dev and in a single-origin deployment the
 * frontend talks to the backend on the same origin (Vite proxy / same
 * domain), so no CORS exists at all. When backend and frontend are deployed
 * cross-origin (Vercel projects on separate domains), set
 * CARRENTAL_ALLOWED_ORIGINS to the frontend origin(s). No wildcard ever.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${carrental.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins.isBlank() ? new String[0] : allowedOrigins.split(",");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length > 0) {
            registry.addMapping("/api/**")
                    .allowedOrigins(allowedOrigins)
                    .allowedMethods("GET", "POST", "DELETE")
                    .allowedHeaders("Content-Type")
                    .allowCredentials(false);
        }
    }
}
