package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FeedingStrategyVO {

    private Integer pondId;
    private String pondName;
    private String fishSpecies;
    private BigDecimal biomassKg;
    private BigDecimal avgWeightKg;
    private BigDecimal dailyRate;
    private BigDecimal dailyFeedKg;
    private Integer mealsPerDay;
    private List<String> feedTimes;
    private List<BigDecimal> mealAmountsKg;
    private List<FeedingPlanVO> plans;
    private String summaryText;
    private boolean available;

    @Data
    public static class FeedingPlanVO {
        private int mealsPerDay;
        private String description;
        private List<BigDecimal> amountsKg;
    }
}
