package com.pms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExternalDeviceResponse {

    private String devid;
    private BigDecimal dox;
    private BigDecimal ph;
    private BigDecimal thw;

    @JsonProperty("collectTimeStr")
    private String collectTimeStr;
}
