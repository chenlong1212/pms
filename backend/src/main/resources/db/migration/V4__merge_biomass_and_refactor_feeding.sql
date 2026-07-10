-- Flyway migration V4: 合并生物量日表 + 投喂记录改为按日一条（含投喂总量）

CREATE TABLE pond_daily_metric (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    pond_id       TINYINT        NOT NULL,
    record_date   DATE           NOT NULL,
    fish_count    INT            NOT NULL,
    avg_weight_kg DECIMAL(10, 4) NOT NULL,
    created_at    DATETIME       NOT NULL,
    updated_at    DATETIME       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pond_date (pond_id, record_date),
    INDEX idx_pond_date (pond_id, record_date),
    CONSTRAINT fk_metric_pond FOREIGN KEY (pond_id) REFERENCES pond (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO pond_daily_metric (pond_id, record_date, fish_count, avg_weight_kg, created_at, updated_at)
SELECT c.pond_id, c.record_date, c.fish_count, a.avg_weight_kg, c.created_at, c.created_at
FROM fish_count_record c
INNER JOIN avg_weight_record a
    ON c.pond_id = a.pond_id AND c.record_date = a.record_date;

DROP VIEW biomass_record;

DROP TABLE fish_count_record;
DROP TABLE avg_weight_record;

CREATE VIEW biomass_record AS
SELECT
    id,
    pond_id,
    record_date,
    ROUND(fish_count * avg_weight_kg, 2) AS biomass_kg,
    fish_count,
    avg_weight_kg,
    created_at
FROM pond_daily_metric;

-- 投喂记录：去掉时间，增加总量，每天每塘仅一条
ALTER TABLE feeding_record
    ADD COLUMN feed_total_kg DECIMAL(10, 4) NULL AFTER feed_date;

DELETE f1 FROM feeding_record f1
INNER JOIN feeding_record f2
    ON f1.pond_id = f2.pond_id AND f1.feed_date = f2.feed_date AND f1.id < f2.id;

UPDATE feeding_record SET feed_total_kg = 0 WHERE feed_total_kg IS NULL;

ALTER TABLE feeding_record
    DROP COLUMN feed_time,
    MODIFY feed_total_kg DECIMAL(10, 4) NOT NULL,
    ADD UNIQUE KEY uk_pond_feed_date (pond_id, feed_date);

ALTER TABLE feeding_record
    DROP INDEX idx_pond_feed,
    ADD INDEX idx_pond_feed_date (pond_id, feed_date DESC);
