package com.pms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "device")
public class DeviceProperties {

    private Api api = new Api();
    private Schedule schedule = new Schedule();

    @Data
    public static class Api {
        private String baseUrl;
        private String deviceId;
    }

    @Data
    public static class Schedule {
        private boolean enabled = true;
        private long fixedRateMs = 600_000;
    }
}
