package com.pms.service;

import com.pms.dto.BiomassTrendVO;
import com.pms.dto.BiomassCorrectionRequest;
import com.pms.dto.BiomassCorrectionVO;
import com.pms.dto.PondVO;
import com.pms.entity.BiomassRecord;
import com.pms.entity.Pond;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.PondMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BiomassService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PondMapper pondMapper;
    private final BiomassRecordMapper biomassRecordMapper;

    public List<PondVO> getPonds() {
        return pondMapper.findAll().stream()
                .map(PondVO::from)
                .toList();
    }

    public BiomassTrendVO getTrend(int pondId, int days) {
        Pond pond = pondMapper.findById(pondId);
        if (pond == null) {
            throw new IllegalArgumentException("鱼塘不存在: " + pondId);
        }

        LocalDate today = LocalDate.now();
        String startDate = today.minusDays(days - 1L).format(DATE_FORMATTER);
        String endDate = today.format(DATE_FORMATTER);
        List<BiomassRecord> records = biomassRecordMapper.findTrendData(pondId, startDate, endDate);

        BiomassTrendVO vo = new BiomassTrendVO();
        vo.setPondId(pond.getId());
        vo.setPondName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        vo.setDays(days);
        vo.setDates(records.stream()
                .map(r -> r.getRecordDate().format(DATE_FORMATTER))
                .toList());
        vo.setBiomass(records.stream()
                .map(BiomassRecord::getBiomassKg)
                .toList());
        vo.setCount(records.stream()
                .map(BiomassRecord::getFishCount)
                .toList());
        vo.setAvgWeight(records.stream()
                .map(BiomassRecord::getAvgWeightKg)
                .toList());
        return vo;
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
