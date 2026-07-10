-- Flyway migration V2: 鱼塘 + 数量/平均重量表 + 生物量视图（数据见 deploy/seed_biomass_2026.sql）
-- 生物量 = 数量 × 平均重量，由 biomass_record 视图自动计算

CREATE TABLE pond (
    id           TINYINT      NOT NULL,
    name         VARCHAR(32)  NOT NULL,
    fish_species VARCHAR(16)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fish_count_record (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    pond_id     TINYINT  NOT NULL,
    record_date DATE     NOT NULL,
    fish_count  INT      NOT NULL,
    created_at  DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pond_date (pond_id, record_date),
    INDEX idx_pond_date (pond_id, record_date),
    CONSTRAINT fk_count_pond FOREIGN KEY (pond_id) REFERENCES pond (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE avg_weight_record (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    pond_id       TINYINT        NOT NULL,
    record_date   DATE           NOT NULL,
    avg_weight_kg DECIMAL(10, 4) NOT NULL,
    created_at    DATETIME       NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pond_date (pond_id, record_date),
    INDEX idx_pond_date (pond_id, record_date),
    CONSTRAINT fk_weight_pond FOREIGN KEY (pond_id) REFERENCES pond (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE VIEW biomass_record AS
SELECT
    c.id,
    c.pond_id,
    c.record_date,
    ROUND(c.fish_count * a.avg_weight_kg, 2) AS biomass_kg,
    c.fish_count,
    c.created_at
FROM fish_count_record c
INNER JOIN avg_weight_record a
    ON c.pond_id = a.pond_id AND c.record_date = a.record_date;
