package com.teampm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 绑定 {@code application.yml} 中 {@code app.*}：JWT、CORS、风险模型与负荷平滑等可调参数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Jwt jwt = new Jwt();
    private Cors cors = new Cors();
    private Risk risk = new Risk();
    private Load load = new Load();

    /** 访问/刷新令牌时效与 HMAC 密钥（生产务必覆盖默认值）。 */
    @Data
    public static class Jwt {
        private String secret = "dev-secret";
        private long accessMinutes = 30;
        private long refreshDays = 7;
    }

    /** 允许的前端 Origin，多个用英文逗号分隔。 */
    @Data
    public static class Cors {
        private String origins = "http://localhost:5173";
    }

    /** {@link com.teampm.algo.RiskModel} 使用的回归系数与红黄阈值。 */
    @Data
    public static class Risk {
        private double beta0 = -2;
        private double beta1 = 3.5;
        private double beta2 = 0.4;
        private double beta3 = 1.2;
        private double redThreshold = 0.7;
        private double orangeThreshold = 0.4;
    }

    /** 负荷/工时类指数平滑的 α。 */
    @Data
    public static class Load {
        private double smoothingAlpha = 0.4;
    }
}
