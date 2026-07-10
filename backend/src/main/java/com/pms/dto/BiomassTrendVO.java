package com.pms.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class BiomassTrendVO {

    private Integer pondId;
    private String pondName;
    private String fishSpecies;
    private Integer days;
    private List<String> dates;
    private List<BigDecimal> biomass;
    private List<Integer> count;
    private List<BigDecimal> avgWeight;
}
