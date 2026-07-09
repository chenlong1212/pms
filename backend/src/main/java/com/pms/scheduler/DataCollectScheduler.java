package com.pms.scheduler;

import com.pms.config.DeviceProperties;
import com.pms.service.DataCollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataCollectScheduler {

    private final DataCollectService dataCollectService;
    private final DeviceProperties deviceProperties;

    @Scheduled(fixedDelayString = "${device.schedule.fixed-rate-ms}", initialDelay = 10000)
    public void scheduledCollect() {
        if (!deviceProperties.getSchedule().isEnabled()) {
            return;
        }
        dataCollectService.collectData();
    }
}
