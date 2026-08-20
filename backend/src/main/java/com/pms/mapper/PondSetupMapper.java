package com.pms.mapper;

import com.pms.entity.PondSetup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface PondSetupMapper {

    Optional<PondSetup> findByPondId(@Param("pondId") int pondId);

    void insert(PondSetup setup);

    void update(PondSetup setup);
}
