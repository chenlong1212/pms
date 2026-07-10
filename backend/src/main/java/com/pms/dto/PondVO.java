package com.pms.dto;

import com.pms.entity.Pond;
import lombok.Data;

@Data
public class PondVO {

    private Integer id;
    private String name;
    private String fishSpecies;

    public static PondVO from(Pond pond) {
        PondVO vo = new PondVO();
        vo.setId(pond.getId());
        vo.setName(pond.getName());
        vo.setFishSpecies(pond.getFishSpecies());
        return vo;
    }
}
