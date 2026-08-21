-- Flyway migration V11: 调整三塘生物量差异化参数后，清空旧的日数据
-- 由应用启动时的 BiomassSimScheduler 按新模型参数重新生成（幂等回填）
DELETE FROM pond_daily_metric;
