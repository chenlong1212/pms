package com.pms.dto;

import com.pms.entity.BiomassRecord;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class BiomassCorrectionVO {
    private Integer pondId;
    private String pondName;
    private String recordDate;
    private Integer fishCount;
    private BigDecimal avgWeightKg;
    private BigDecimal biomassKg;

    public static BiomassCorrectionVO from(BiomassRecord record, String pondName) {
        if (record == null) return null;
        BiomassCorrectionVO vo = new BiomassCorrectionVO();
        vo.setPondId(record.getPondId());
        vo.setPondName(pondName);
        vo.setRecordDate(record.getRecordDate().toString());
        vo.setFishCount(record.getFishCount());
        vo.setAvgWeightKg(record.getAvgWeightKg());
        vo.setBiomassKg(record.getBiomassKg());
        return vo;
    }
}
