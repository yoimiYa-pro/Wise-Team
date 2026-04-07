package com.teampm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Risk risk = new Risk();
    private Load load = new Load();

    @Data
    public static class Jwt {
        private String secret = "dev-secret";
        private long accessMinutes = 30;
        private long refreshDays = 7;
    }

    @Data
    public static class Cors {
        private String origins = "http://localhost:5173";
    }

    @Data
    public static class Risk {
        private double beta0 = -2;
        private double beta1 = 3.5;
        private double beta2 = 0.4;
        private double beta3 = 1.2;
        private double redThreshold = 0.7;
        private double orangeThreshold = 0.4;
    }

    @Data
    public static class Load {
        private double smoothingAlpha = 0.4;
    }
}
