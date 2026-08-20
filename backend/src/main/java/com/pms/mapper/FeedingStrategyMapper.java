package com.pms.mapper;

import com.pms.entity.FeedingStrategySetting;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FeedingStrategyMapper {

    @Select("""
            SELECT pond_id, daily_rate, meals_per_day,
                   TIME_FORMAT(feed_time_1, '%H:%i') AS feed_time_1,
                   TIME_FORMAT(feed_time_2, '%H:%i') AS feed_time_2,
                   TIME_FORMAT(feed_time_3, '%H:%i') AS feed_time_3,
                   created_at, updated_at
            FROM feeding_strategy
            WHERE pond_id = #{pondId}
            """)
    FeedingStrategySetting findByPondId(@Param("pondId") int pondId);

    @Insert("""
            INSERT INTO feeding_strategy
                (pond_id, daily_rate, meals_per_day, feed_time_1, feed_time_2, feed_time_3, created_at, updated_at)
            VALUES
                (#{pondId}, #{dailyRate}, #{mealsPerDay}, #{feedTime1}, #{feedTime2}, #{feedTime3}, #{createdAt}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                daily_rate = VALUES(daily_rate),
                meals_per_day = VALUES(meals_per_day),
                feed_time_1 = VALUES(feed_time_1),
                feed_time_2 = VALUES(feed_time_2),
                feed_time_3 = VALUES(feed_time_3),
                updated_at = VALUES(updated_at)
            """)
    int upsert(FeedingStrategySetting setting);
}
