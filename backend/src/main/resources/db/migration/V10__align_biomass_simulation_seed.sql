-- Flyway migration V10: 生物量模拟对齐 D:\gp
-- 1) 放养初始重量改为鱼苗级（0.05/0.08/0.12 kg），让分档增长率模型呈现出"持续增长 + 三塘分化"的曲线
-- 2) 清空旧的每日日数据（pond_daily_metric），由启动回填基于新种子重新生成

DELETE FROM pond_daily_metric;

UPDATE pond_setup SET
    initial_weight_kg = 0.0500,
    updated_at        = NOW()
WHERE pond_id = 1;

UPDATE pond_setup SET
    initial_weight_kg = 0.0800,
    updated_at        = NOW()
WHERE pond_id = 2;

UPDATE pond_setup SET
    initial_weight_kg = 0.1200,
    updated_at        = NOW()
WHERE pond_id = 3;
