package com.pms.mapper;

import com.pms.entity.Pond;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PondMapper {

    List<Pond> findAll();

    Pond findById(int id);
}
