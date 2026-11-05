package dev.piotr.carrental;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class CarRentalApplication {

    /**
     * Database NEON-style credentials arriving as a DATABASE_URL env var, e.g.
     * postgresql://user:password@ep-xyz.eu-central-1.aws.neon.tech/carrental?sslmode=require
     * (including URL-encoded user/password) — otherwise the Spring config in
     * application.yml is used unchanged (local docker-compose defaults).
     */
    public static void main(String[] args) {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            URI uri = URI.create(databaseUrl);
            var userInfoDecoded = new String(java.net.URLDecoder.decode(
                    nullSafe(uri.getUserInfo()), StandardCharsets.UTF_8));
            String user = userInfoDecoded.split(":", 2)[0];
            String password = userInfoDecoded.contains(":") ? userInfoDecoded.split(":", 2)[1] : "";
            String hostPart = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            System.setProperty("spring.datasource.url", "jdbc:postgresql://" + hostPart +
                    uri.getPath() + (uri.getQuery() == null ? "" : "?" + uri.getQuery()));
            System.setProperty("spring.datasource.username", user);
            System.setProperty("spring.datasource.password", password);
        }
        SpringApplication.run(CarRentalApplication.class, args);
    }

    private static String nullSafe(String s) {
        return s == null ? ":" : s;
    }
}
