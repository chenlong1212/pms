package com.pms.service;

import com.pms.dto.BiomassTrendVO;
import com.pms.dto.BiomassCorrectionRequest;
import com.pms.dto.BiomassCorrectionVO;
import com.pms.dto.PondSetupRequest;
import com.pms.dto.PondSetupVO;
import com.pms.dto.PondVO;
import com.pms.entity.BiomassRecord;
import com.pms.entity.Pond;
import com.pms.entity.PondSetup;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.PondMapper;
import com.pms.mapper.PondSetupMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class BiomassService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    // 仿照 gp 项目的离散日增长模型参数
    private static final double COUNT_DECREASE_FACTOR = 0.9997;   // 每日鱼群数量递减率
    private static final double LENGTH_GROWTH_MIN   = 0.05;       // 体长日增长最小值(mm)
    private static final double LENGTH_GROWTH_MAX   = 0.08;       // 体长日增长最大值(mm)
    private static final double WIDTH_GROWTH_MIN    = 0.03;       // 体宽日增长最小值(mm)
    private static final double WIDTH_GROWTH_MAX    = 0.05;       // 体宽日增长最大值(mm)

    private final PondMapper pondMapper;
    private final BiomassRecordMapper biomassRecordMapper;
    private final PondSetupMapper pondSetupMapper;

    public List<PondVO> getPonds() {
        return pondMapper.findAll().stream()
                .map(PondVO::from)
                .toList();
    }

    public PondSetupVO getSetup(int pondId) {
        requirePond(pondId);
        Optional<PondSetup> setup = pondSetupMapper.findByPondId(pondId);
        Pond pond = pondMapper.findById(pondId);
        if (!setup.isPresent()) {
            PondSetupVO vo = new PondSetupVO();
            vo.setPondId(pond.getId());
            vo.setPondName(pond.getName());
            vo.setFishSpecies(pond.getFishSpecies());
            return vo;
        }
        PondSetup s = setup.get();
        PondSetupVO vo = new PondSetupVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        vo.setStockDate(s.getStockDate());
        vo.setInitialFishCount(s.getInitialFishCount());
        vo.setInitialWeightKg(s.getInitialWeightKg());
        vo.setHarvestDate(s.getHarvestDate());
        vo.setFinalFishCount(s.getFinalFishCount());
        vo.setFinalWeightKg(s.getFinalWeightKg());
        vo.setFeedingRatio(s.getFeedingRatio());
        return vo;
    }

    @Transactional
    public PondSetupVO saveSetup(PondSetupRequest request) {
        if (request == null || request.getPondId() == null) {
            throw new IllegalArgumentException("鱼塘不能为空");
        }
        Pond pond = requirePond(request.getPondId());

        if (request.getStockDate() == null) {
            throw new IllegalArgumentException("放养日期不能为空");
        }
        if (request.getInitialFishCount() == null || request.getInitialFishCount() <= 0) {
            throw new IllegalArgumentException("初始鱼群数量必须大于 0");
        }
        if (request.getInitialWeightKg() == null || request.getInitialWeightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("初始平均重量必须大于 0");
        }
        if (request.getInitialWeightKg().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("初始平均重量不能超过 100 kg/尾");
        }
        if (request.getFinalFishCount() != null && request.getFinalFishCount() <= 0) {
            throw new IllegalArgumentException("最终鱼群数量必须大于 0");
        }
        if (request.getFinalWeightKg() != null && request.getFinalWeightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("最终平均重量必须大于 0");
        }
        if (request.getFinalFishCount() != null && request.getInitialFishCount() != null
                && request.getFinalFishCount() > request.getInitialFishCount()) {
            throw new IllegalArgumentException("最终鱼群数量不能超过初始数量");
        }
        if (request.getHarvestDate() != null && request.getStockDate() != null
                && !request.getHarvestDate().isAfter(request.getStockDate())) {
            throw new IllegalArgumentException("收获日期必须晚于放养日期");
        }

        BigDecimal feedingRatio = request.getFeedingRatio() != null
                ? request.getFeedingRatio().setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(0.0160);

        LocalDateTime now = LocalDateTime.now(ZONE);
        PondSetup setup = new PondSetup();
        setup.setPondId(pond.getId());
        setup.setStockDate(request.getStockDate());
        setup.setInitialFishCount(request.getInitialFishCount());
        setup.setInitialWeightKg(request.getInitialWeightKg()
                .setScale(4, RoundingMode.HALF_UP));
        setup.setHarvestDate(request.getHarvestDate());
        setup.setFinalFishCount(request.getFinalFishCount());
        setup.setFinalWeightKg(request.getFinalWeightKg() != null
                ? request.getFinalWeightKg().setScale(4, RoundingMode.HALF_UP) : null);
        setup.setFeedingRatio(feedingRatio);
        setup.setCreatedAt(now);
        setup.setUpdatedAt(now);

        Optional<PondSetup> existing = pondSetupMapper.findByPondId(pond.getId());
        if (existing.isPresent()) {
            pondSetupMapper.update(setup);
        } else {
            pondSetupMapper.insert(setup);
        }

        return getSetup(pond.getId());
    }

    public BiomassTrendVO getTrend(int pondId, int days) {
        Pond pond = requirePond(pondId);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate startDate = today.minusDays(days - 1L);

        // 1. 查放养参数
        Optional<PondSetup> setupOpt = pondSetupMapper.findByPondId(pondId);

        // 2. 查人工校正记录，以日期为 key
        Map<String, BiomassRecord> correctionMap = new LinkedHashMap<>();
        List<BiomassRecord> corrections = biomassRecordMapper.findTrendData(
                pondId, startDate.format(DATE_FORMATTER), today.format(DATE_FORMATTER));
        for (BiomassRecord r : corrections) {
            correctionMap.put(r.getRecordDate().format(DATE_FORMATTER), r);
        }

        List<String> dates = new ArrayList<>();
        List<Integer> countList = new ArrayList<>();
        // 用克(g)存储日均重，避免小数精度丢失
        List<Integer> weightGList = new ArrayList<>();
        List<BigDecimal> biomassList = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            String dateStr = d.format(DATE_FORMATTER);
            dates.add(dateStr);

            BiomassRecord correction = correctionMap.get(dateStr);
            if (correction != null) {
                // 有校正记录，直接使用
                countList.add(correction.getFishCount());
                weightGList.add(correction.getAvgWeightKg().multiply(BigDecimal.valueOf(1000))
                        .intValue());
                biomassList.add(correction.getBiomassKg());
                continue;
            }

            // 无校正记录，用离散日增长模型推算
            if (setupOpt.isPresent()) {
                PondSetup setup = setupOpt.get();
                long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(setup.getStockDate(), d);
                if (daysElapsed < 0) {
                    countList.add(0);
                    weightGList.add(0);
                    biomassList.add(BigDecimal.ZERO);
                } else {
                    // 模拟到该天为止的状态
                    SimState state = simulateToDay(setup, daysElapsed);
                    countList.add(state.count);
                    weightGList.add(state.weightG);
                    // 生物量(kg) = 数量 × 单尾重量(g) / 1000
                    biomassList.add(BigDecimal.valueOf(state.count)
                            .multiply(BigDecimal.valueOf(state.weightG))
                            .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP));
                }
            } else {
                // 无放养参数也无校正，填零
                countList.add(0);
                weightGList.add(0);
                biomassList.add(BigDecimal.ZERO);
            }
        }

        // weightGList 转回 kg 返回
        List<BigDecimal> weightKgList = new ArrayList<>();
        for (int wG : weightGList) {
            weightKgList.add(BigDecimal.valueOf(wG).divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP));
        }

        BiomassTrendVO vo = new BiomassTrendVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        vo.setDays(days);
        vo.setDates(dates);
        vo.setBiomass(biomassList);
        vo.setCount(countList);
        vo.setAvgWeight(weightKgList);
        return vo;
    }

    /**
     * 取某池塘指定日期的"有效生物量"：优先取该日人工校正记录（pond_daily_metric，
     * 经 biomass_record 视图可见）；无记录时回退到放养参数 + 离散增长模型的模拟值。
     * 用于生产报告预览与投喂策略，保证"当前生物量"始终有值可展示。
     *
     * @return 当日生物量记录；池塘不存在、无放养参数或日期早于放养日时返回 null
     */
    public BiomassRecord getEffectiveBiomass(int pondId, LocalDate date) {
        requirePond(pondId);
        String dateStr = date.format(DATE_FORMATTER);
        BiomassRecord manual = biomassRecordMapper.findByPondAndDate(pondId, dateStr);
        if (manual != null) {
            return manual;
        }
        Optional<PondSetup> setupOpt = pondSetupMapper.findByPondId(pondId);
        if (setupOpt.isEmpty()) {
            return null;
        }
        PondSetup setup = setupOpt.get();
        long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(setup.getStockDate(), date);
        if (daysElapsed < 0) {
            return null;
        }
        SimState state = simulateToDay(setup, daysElapsed);
        BigDecimal avgWeightKg = BigDecimal.valueOf(state.weightG)
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP);
        BigDecimal biomassKg = BigDecimal.valueOf(state.count)
                .multiply(BigDecimal.valueOf(state.weightG))
                .divide(BigDecimal.valueOf(1000), 3, RoundingMode.HALF_UP);
        BiomassRecord simulated = new BiomassRecord();
        simulated.setPondId(pondId);
        simulated.setRecordDate(date);
        simulated.setFishCount(state.count);
        simulated.setAvgWeightKg(avgWeightKg);
        simulated.setBiomassKg(biomassKg);
        return simulated;
    }

    // ──────────────────────────────────────────────
    // 离散日增长模型（对齐 gp 项目 simulation_service.py）
    // ──────────────────────────────────────────────

    /** 模拟状态：鱼数 + 平均体重(克) */
    private static class SimState {
        int count;
        int weightG; // 平均体重，单位克
        SimState(int count, int weightG) {
            this.count = count;
            this.weightG = weightG;
        }
    }

    /**
     * 从放养日模拟到指定的第 daysElapsed 天（不含），返回当天结束时的状态。
     * 每次调用使用不同的 Random 实例，保证趋势线各天之间连续。
     */
    private SimState simulateToDay(PondSetup setup, long daysElapsed) {
        Random rand = new Random(setup.getStockDate().toEpochDay());
        int count = setup.getInitialFishCount();
        // 初始平均体重转为克
        int weightG = setup.getInitialWeightKg()
                .multiply(BigDecimal.valueOf(1000))
                .intValue();

        for (long day = 0; day < daysElapsed; day++) {
            // 1) 鱼群数量衰减
            count = (int) Math.max(0, Math.round(count * COUNT_DECREASE_FACTOR));

            // 2) 体重增长（基于当前体重分档）
            double growthRate = getMassGrowthRate(weightG);
            weightG = (int) Math.round(weightG * (1.0 + growthRate));
        }
        return new SimState(count, weightG);
    }

    /**
     * 根据当前单尾体重(克)返回日增长率：
     *   ≤ 400g → 1.3333%
     *   ≤ 500g → 0.8333%
     *   ≤ 600g → 0.75%
     *   >  600g → 0.05%
     */
    private static double getMassGrowthRate(int weightG) {
        if (weightG <= 400)  return 0.013333;
        if (weightG <= 500)  return 0.008333;
        if (weightG <= 600)  return 0.0075;
        return 0.0005;
    }

    public BiomassCorrectionVO getRecord(int pondId, String recordDate) {
        Pond pond = requirePond(pondId);
        String normalizedDate = normalizeDate(recordDate);
        return BiomassCorrectionVO.from(
                biomassRecordMapper.findByPondAndDate(pondId, normalizedDate), pond.getName());
    }

    @Transactional
    public BiomassCorrectionVO correct(BiomassCorrectionRequest request) {
        if (request == null || request.getPondId() == null) {
            throw new IllegalArgumentException("鱼塘不能为空");
        }
        Pond pond = requirePond(request.getPondId());
        String recordDate = normalizeDate(request.getRecordDate());
        if (request.getFishCount() == null || request.getFishCount() <= 0) {
            throw new IllegalArgumentException("鱼群数量必须大于 0");
        }
        if (request.getAvgWeightKg() == null || request.getAvgWeightKg().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("平均重量必须大于 0");
        }
        if (request.getAvgWeightKg().compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("平均重量不能超过 100 kg/尾");
        }

        LocalDateTime now = LocalDateTime.now(ZONE);
        biomassRecordMapper.upsertDailyMetric(request.getPondId(), recordDate, request.getFishCount(),
                request.getAvgWeightKg().setScale(4, java.math.RoundingMode.HALF_UP), now);
        return BiomassCorrectionVO.from(
                biomassRecordMapper.findByPondAndDate(request.getPondId(), recordDate), pond.getName());
    }

    private Pond requirePond(int pondId) {
        Pond pond = pondMapper.findById(pondId);
        if (pond == null) throw new IllegalArgumentException("鱼塘不存在: " + pondId);
        return pond;
    }

    private String normalizeDate(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("记录日期不能为空");
        LocalDate date;
        try {
            date = LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("记录日期格式必须为 yyyy-MM-dd");
        }
        if (date.isAfter(LocalDate.now(ZONE))) throw new IllegalArgumentException("记录日期不能晚于今天");
        return date.format(DATE_FORMATTER);
    }
}
