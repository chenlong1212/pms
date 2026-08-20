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

@Service
@RequiredArgsConstructor
public class BiomassService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

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
        List<BigDecimal> weightList = new ArrayList<>();
        List<BigDecimal> biomassList = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            String dateStr = d.format(DATE_FORMATTER);
            dates.add(dateStr);

            BiomassRecord correction = correctionMap.get(dateStr);
            if (correction != null) {
                // 有校正记录，直接使用
                countList.add(correction.getFishCount());
                weightList.add(correction.getAvgWeightKg());
                biomassList.add(correction.getBiomassKg());
                continue;
            }

            // 无校正记录，用指数模型推算
            if (setupOpt.isPresent()) {
                PondSetup setup = setupOpt.get();
                long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(setup.getStockDate(), d);
                int totalDays = calcTotalDays(setup);
                if (daysElapsed < 0 || (setup.getHarvestDate() != null && d.isAfter(setup.getHarvestDate()))) {
                    // 推算日期在放养日之前，或已过收获日期，补零
                    countList.add(0);
                    weightList.add(BigDecimal.ZERO);
                    biomassList.add(BigDecimal.ZERO);
                } else {
                    int count = estimateFishCount(setup, daysElapsed);
                    BigDecimal weight = estimateWeight(setup, daysElapsed);
                    countList.add(count);
                    weightList.add(weight);
                    biomassList.add(weight.multiply(BigDecimal.valueOf(count))
                            .setScale(2, RoundingMode.HALF_UP));
                }
            } else {
                // 无放养参数也无校正，填零
                countList.add(0);
                weightList.add(BigDecimal.ZERO);
                biomassList.add(BigDecimal.ZERO);
            }
        }

        BiomassTrendVO vo = new BiomassTrendVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        vo.setDays(days);
        vo.setDates(dates);
        vo.setBiomass(biomassList);
        vo.setCount(countList);
        vo.setAvgWeight(weightList);
        return vo;
    }

    /**
     * 指数衰减/增长模型：
     * fishCount(t) = N0 * (Nt/N0)^(t/T)
     */
    private int estimateFishCount(PondSetup setup, long daysElapsed) {
        int n0 = setup.getInitialFishCount();
        Integer nt = setup.getFinalFishCount();

        if (nt == null) {
            // 无最终数量，假设线性减少：每天减少固定量
            int totalDays = calcTotalDays(setup);
            if (totalDays <= 0) return n0;
            int dailyChange = (n0 - 0) / totalDays; // 假设最终归零
            int count = n0 - (int) (dailyChange * daysElapsed);
            return Math.max(0, count);
        }

        if (nt <= 0 || nt == n0) return n0;

        double ratio = (double) nt / n0;
        int totalDays = calcTotalDays(setup);
        double exponent = (double) daysElapsed / totalDays;
        return Math.max(0, (int) Math.round(n0 * Math.pow(ratio, exponent)));
    }

    /**
     * 指数增长模型：
     * weight(t) = w0 * (wt/w0)^(t/T)
     */
    private BigDecimal estimateWeight(PondSetup setup, long daysElapsed) {
        BigDecimal w0 = setup.getInitialWeightKg();
        BigDecimal wt = setup.getFinalWeightKg();

        if (wt == null) {
            // 无最终重量，假设线性增长至初始值的 10 倍
            int totalDays = calcTotalDays(setup);
            if (totalDays <= 0) return w0;
            BigDecimal target = w0.multiply(BigDecimal.TEN);
            BigDecimal dailyGrowth = target.subtract(w0).divide(
                    BigDecimal.valueOf(totalDays), 6, RoundingMode.HALF_UP);
            return w0.add(dailyGrowth.multiply(BigDecimal.valueOf(daysElapsed)))
                    .setScale(4, RoundingMode.HALF_UP);
        }

        if (wt.compareTo(w0) <= 0) return w0;

        double ratio = wt.doubleValue() / w0.doubleValue();
        int totalDays = calcTotalDays(setup);
        double exponent = (double) daysElapsed / totalDays;
        double result = w0.doubleValue() * Math.pow(ratio, exponent);
        return BigDecimal.valueOf(result).setScale(4, RoundingMode.HALF_UP);
    }

    private int calcTotalDays(PondSetup setup) {
        if (setup.getHarvestDate() == null) return 365;
        return (int) java.time.temporal.ChronoUnit.DAYS.between(setup.getStockDate(), setup.getHarvestDate());
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
