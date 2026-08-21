package com.pms.service;

import com.pms.entity.BiomassRecord;
import com.pms.entity.PondSetup;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.PondMapper;
import com.pms.mapper.PondSetupMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * 每日生物量生成器 —— 对齐 D:\gp 项目 simulation_service.py 的数值处理方法。
 *
 * 核心思路（与 D:\gp 一致）：
 * 1. 只有一个日数据表 pond_daily_metric（视图 biomass_record），真实与模拟不区分，
 *    生成的数据直接落库，所有读取只查这张表；
 * 2. 迭代式日增长：今天 = 基于前一天【存库值】推进，而非从放养日重放；
 *    - 数量：count = prev_count × 0.9997（每日递减）
 *    - 均重：weight = prev_weight × (1 + 分档增长率 × 池塘系数 × 每日随机扰动)
 *    - 生物量：由视图 biomass_record 按 count × avg_weight / 1000 自动计算
 * 3. 分档增长率对齐 D:\gp：≤400g→1.3333% / ≤500g→0.8333% / ≤600g→0.75% / >600g→0.05%；
 * 4. 差异化：每池塘独立增长系数 + 独立稳定随机种子，使三个池塘曲线明显不同；
 * 5. 人工校正（BiomassCorrectionDialog）写入同一张表，有记录的日子以校正值为准，
 *    后续日期从校正值继续迭代（同 D:\gp 从最新存库值继续模拟）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BiomassGenerationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 鱼群数量每日递减率（对齐 D:\gp COUNT_DECREASE_FACTOR） */
    private static final double COUNT_DECREASE_FACTOR = 0.9997;
    /** 日增长率随机扰动幅度：rate × (1 ± NOISE_AMPLITUDE/2)，即约 ±5% */
    private static final double NOISE_AMPLITUDE = 0.10;

    /** 每池塘增长系数（用于让三条曲线明显分化，可在此调整） */
    private static final Map<Integer, Double> POND_GROWTH_COEFF = Map.of(
            1, 1.00,   // 一号塘：标准
            2, 1.20,   // 二号塘：较快
            3, 0.80    // 三号塘：较慢
    );

    private final PondMapper pondMapper;
    private final PondSetupMapper pondSetupMapper;
    private final BiomassRecordMapper biomassRecordMapper;

    /** 对所有有放养参数的池塘补齐数据到今天（幂等：只补缺失日期，不覆盖已有记录） */
    public void generateAll() {
        pondMapper.findAll().forEach(pond -> {
            try {
                generateForPond(pond.getId());
            } catch (Exception e) {
                log.warn("生成池塘 {} 生物量数据失败: {}", pond.getId(), e.getMessage());
            }
        });
    }

    /** 对指定池塘从放养日迭代生成到今天，缺哪天补哪天 */
    public void generateForPond(int pondId) {
        Optional<PondSetup> setupOpt = pondSetupMapper.findByPondId(pondId);
        if (setupOpt.isEmpty()) {
            return;
        }
        PondSetup setup = setupOpt.get();
        LocalDate stockDate = setup.getStockDate();
        LocalDate today = LocalDate.now(ZONE);
        if (stockDate == null || stockDate.isAfter(today)) {
            return;
        }

        double count = setup.getInitialFishCount();
        double weightG = setup.getInitialWeightKg().multiply(BigDecimal.valueOf(1000)).doubleValue();
        double coeff = POND_GROWTH_COEFF.getOrDefault(pondId, 1.0);
        // 每塘独立稳定种子：保证每日扰动可复现，且各塘扰动序列互不相同
        Random rand = new Random(pondId * 1_000_003L);
        LocalDateTime now = LocalDateTime.now(ZONE);

        for (LocalDate cursor = stockDate; !cursor.isAfter(today); cursor = cursor.plusDays(1)) {
            String dateStr = cursor.format(DATE);
            BiomassRecord existing = biomassRecordMapper.findByPondAndDate(pondId, dateStr);
            if (existing != null) {
                // 已有记录（含人工校正）：以存库值为准，并作为后续迭代的起点
                count = existing.getFishCount();
                weightG = existing.getAvgWeightKg().multiply(BigDecimal.valueOf(1000)).doubleValue();
                continue;
            }
            if (!cursor.isEqual(stockDate)) {
                // 非放养日：基于前一天存库值迭代推进
                count = count * COUNT_DECREASE_FACTOR;
                double rate = massGrowthRate(weightG) * coeff
                        * (1.0 + (rand.nextDouble() - 0.5) * NOISE_AMPLITUDE);
                weightG = weightG * (1.0 + rate);
            }
            // 放养日（无记录）或迭代日：写入单表
            int fishCount = (int) Math.max(1, Math.round(count));
            BigDecimal avgWeightKg = BigDecimal.valueOf(weightG)
                    .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
            biomassRecordMapper.upsertDailyMetric(pondId, dateStr, fishCount, avgWeightKg, now);
            // 以存库值（四舍五入后）为下一次迭代的起点，与 D:\gp 读取前一天存库值保持一致
            count = fishCount;
            weightG = avgWeightKg.multiply(BigDecimal.valueOf(1000)).doubleValue();
        }
    }

    /**
     * 分档增长率（对齐 D:\gp get_mass_growth_rate）：
     *   ≤400g→1.3333% / ≤500g→0.8333% / ≤600g→0.75% / >600g→0.2%
     * 注：D:\gp 对 >600g 使用 0.0005（0.05%/日，针对成鱼金鲳鱼），若照搬，
     * 三个池塘长到 600g 后都会几乎停长、曲线在尾部收敛到一起，无法满足"三塘曲线
     * 尽可能不一样"。故此处将 >600g 放宽到 0.002（0.2%/日，仍远慢于幼鱼期），
     * 使各塘曲线在整个养殖周期持续增长并保持分化；≤600g 以下分档与 D:\gp 完全一致。
     */
    private static double massGrowthRate(double weightG) {
        if (weightG <= 400) {
            return 0.013333;
        }
        if (weightG <= 500) {
            return 0.008333;
        }
        if (weightG <= 600) {
            return 0.0075;
        }
        return 0.002;
    }
}
