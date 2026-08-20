package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class FeedingStrategyRequest {
    /** 以小数保存，例如 2.5% 传 0.025。 */
    private BigDecimal dailyRate;
    private Integer mealsPerDay;
    private List<String> feedTimes;
}
