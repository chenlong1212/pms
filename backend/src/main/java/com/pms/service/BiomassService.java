package com.pms.service;

import com.pms.dto.BiomassTrendVO;
import com.pms.dto.PondVO;
import com.pms.entity.BiomassRecord;
import com.pms.entity.Pond;
import com.pms.mapper.BiomassRecordMapper;
import com.pms.mapper.PondMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BiomassService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

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
}
