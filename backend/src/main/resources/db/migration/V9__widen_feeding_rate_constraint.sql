-- Flyway migration V9: 放宽投喂比例校验范围（适配 gp 项目 1.5%–3.7% 的分组投喂率）
ALTER TABLE feeding_strategy
    DROP CONSTRAINT chk_feeding_strategy_rate,
    ADD CONSTRAINT chk_feeding_strategy_rate CHECK (daily_rate BETWEEN 0.0100 AND 0.0500);
