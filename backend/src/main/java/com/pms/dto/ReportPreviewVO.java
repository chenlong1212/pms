package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class ReportPreviewVO {
    private Integer pondId;
    private String pondName;
    private String fishSpecies;
    private String reportType;
    private String startDate;
    private String endDate;
    private WaterQuality waterQuality = new WaterQuality();
    private Biomass biomass;
    private Feeding feeding = new Feeding();
    private DataQuality dataQuality = new DataQuality();

    @Data
    public static class Metric {
        private BigDecimal avg;
        private BigDecimal min;
        private BigDecimal max;
    }

    @Data
    public static class WaterQuality {
        private int sampleCount;
        private Metric dox;
        private Metric ph;
        private Metric temperature;
    }

    @Data
    public static class Biomass {
        private Integer fishCount;
        private BigDecimal avgWeightKg;
        private BigDecimal biomassKg;
    }

    @Data
    public static class Feeding {
        private BigDecimal actualKg;
        private BigDecimal recommendedKg;
        private BigDecimal deviationPercent;
    }

    @Data
    public static class DataQuality {
        private boolean ready;
        private List<String> missingFields = new ArrayList<>();
        private List<String> warnings = new ArrayList<>();
    }
}
