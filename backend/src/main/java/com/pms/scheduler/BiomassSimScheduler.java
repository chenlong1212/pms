package com.pms.scheduler;

import com.pms.service.BiomassGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 生物量每日生成调度：
 * - 应用启动时执行一次回填/补齐（把各池塘从放养日到今天缺的日期补上，幂等）；
 * - 每天 00:10 自动补当天的数据（对齐 D:\gp 的 run_daily_simulation）。
 */
@Component
@RequiredArgsConstructor
public class BiomassSimScheduler implements ApplicationRunner {

    private final BiomassGenerationService biomassGenerationService;

    @Override
    public void run(ApplicationArguments args) {
        biomassGenerationService.generateAll();
    }

    @Scheduled(cron = "0 10 0 * * ?")
    public void dailyEnsure() {
        biomassGenerationService.generateAll();
    }
}
