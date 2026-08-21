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

        // 单表读取：pond_daily_metric（视图 biomass_record）即唯一数据源，
        // 历史与当日数据由 BiomassGenerationService 回填/每日补齐，不再实时模拟
        Map<LocalDate, BiomassRecord> byDate = new LinkedHashMap<>();
        for (BiomassRecord r : biomassRecordMapper.findTrendData(
                pondId, startDate.format(DATE_FORMATTER), today.format(DATE_FORMATTER))) {
            byDate.put(r.getRecordDate(), r);
        }

        List<String> dates = new ArrayList<>();
        List<Integer> countList = new ArrayList<>();
        List<BigDecimal> weightKgList = new ArrayList<>();
        List<BigDecimal> biomassList = new ArrayList<>();

        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            dates.add(d.format(DATE_FORMATTER));
            BiomassRecord r = byDate.get(d);
            if (r == null) {
                countList.add(0);
                weightKgList.add(BigDecimal.ZERO);
                biomassList.add(BigDecimal.ZERO);
            } else {
                countList.add(r.getFishCount());
                weightKgList.add(r.getAvgWeightKg());
                biomassList.add(r.getBiomassKg());
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
        vo.setAvgWeight(weightKgList);
        return vo;
    }

    /**
     * 取某池塘指定日期的"有效生物量"，只读单表（pond_daily_metric / biomass_record 视图）。
     * 优先取当天记录；当天缺失时回退到最近一条历史记录。用于生产报告预览与投喂策略。
     *
     * @return 当日（或最近）生物量记录；池塘不存在或无任何记录时返回 null
     */
    public BiomassRecord getEffectiveBiomass(int pondId, LocalDate date) {
        requirePond(pondId);
        String dateStr = date.format(DATE_FORMATTER);
        BiomassRecord record = biomassRecordMapper.findByPondAndDate(pondId, dateStr);
        if (record != null) {
            return record;
        }
        return biomassRecordMapper.findLatestBefore(pondId, dateStr);
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
