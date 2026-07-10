-- 生物量模拟数据：2026 年全年（含未来日期）
-- 一塘一鱼：一号塘=草鱼，二号塘=鲤鱼，三号塘=鲫鱼
-- 仅模拟「数量」和「平均重量」，生物量由数据库视图自动计算（数量 × 平均重量）
--
-- 执行方式：
--   mysql -u root -p --default-character-set=utf8mb4 pms_local < deploy/seed_biomass_2026.sql
--
-- Windows 生产环境推荐（避免中文乱码）：
--   mysql -u root -p --default-character-set=utf8mb4 pms_prod
--   mysql> source C:/Projects/pms/deploy/seed_biomass_2026.sql

SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

DELETE FROM fish_count_record;
DELETE FROM avg_weight_record;
DELETE FROM pond;

INSERT INTO pond (id, name, fish_species) VALUES
(1, '一号塘', '草鱼'),
(2, '二号塘', '鲤鱼'),
(3, '三号塘', '鲫鱼');

-- 一号塘：草鱼 — 3 月投苗、夏秋季快速生长、6 月/10 月分批捕捞
INSERT INTO fish_count_record (pond_id, record_date, fish_count, created_at)
WITH RECURSIVE daily AS (
    SELECT
        CAST('2026-01-01' AS DATE) AS record_date,
        1500 AS fish_count,
        CAST(0.55 AS DECIMAL(12, 4)) AS avg_weight,
        1 AS seq
    UNION ALL
    SELECT
        d.record_date + INTERVAL 1 DAY,
        GREATEST(
            100,
            d.fish_count
                + CASE WHEN d.record_date = '2026-03-15' THEN 500 ELSE 0 END
                - CASE WHEN d.record_date = '2026-10-20' THEN FLOOR(d.fish_count * 0.30) ELSE 0 END
                - CASE WHEN d.record_date = '2026-06-25' THEN FLOOR(d.fish_count * 0.08) ELSE 0 END
                - GREATEST(0, FLOOR(d.fish_count * 0.00015))
        ),
        CASE
            WHEN d.record_date = '2026-03-15' THEN
                (d.fish_count * d.avg_weight + 500 * 0.05) / (d.fish_count + 500)
            WHEN d.record_date IN ('2026-06-25', '2026-10-20') THEN d.avg_weight
            ELSE d.avg_weight * (
                1 + CASE MONTH(d.record_date + INTERVAL 1 DAY)
                    WHEN 6 THEN 0.0016 WHEN 7 THEN 0.0018 WHEN 8 THEN 0.0017
                    WHEN 5 THEN 0.0012 WHEN 9 THEN 0.0010 WHEN 4 THEN 0.0009
                    WHEN 3 THEN 0.0006 WHEN 10 THEN 0.0007
                    ELSE 0.0003
                END + SIN(d.seq * 0.31) * 0.00008
            )
        END,
        d.seq + 1
    FROM daily d
    WHERE d.record_date < '2026-12-31'
)
SELECT
    1,
    record_date,
    fish_count,
    TIMESTAMP(record_date, '08:00:00')
FROM daily;

INSERT INTO avg_weight_record (pond_id, record_date, avg_weight_kg, created_at)
WITH RECURSIVE daily AS (
    SELECT
        CAST('2026-01-01' AS DATE) AS record_date,
        1500 AS fish_count,
        CAST(0.55 AS DECIMAL(12, 4)) AS avg_weight,
        1 AS seq
    UNION ALL
    SELECT
        d.record_date + INTERVAL 1 DAY,
        GREATEST(
            100,
            d.fish_count
                + CASE WHEN d.record_date = '2026-03-15' THEN 500 ELSE 0 END
                - CASE WHEN d.record_date = '2026-10-20' THEN FLOOR(d.fish_count * 0.30) ELSE 0 END
                - CASE WHEN d.record_date = '2026-06-25' THEN FLOOR(d.fish_count * 0.08) ELSE 0 END
                - GREATEST(0, FLOOR(d.fish_count * 0.00015))
        ),
        CASE
            WHEN d.record_date = '2026-03-15' THEN
                (d.fish_count * d.avg_weight + 500 * 0.05) / (d.fish_count + 500)
            WHEN d.record_date IN ('2026-06-25', '2026-10-20') THEN d.avg_weight
            ELSE d.avg_weight * (
                1 + CASE MONTH(d.record_date + INTERVAL 1 DAY)
                    WHEN 6 THEN 0.0016 WHEN 7 THEN 0.0018 WHEN 8 THEN 0.0017
                    WHEN 5 THEN 0.0012 WHEN 9 THEN 0.0010 WHEN 4 THEN 0.0009
                    WHEN 3 THEN 0.0006 WHEN 10 THEN 0.0007
                    ELSE 0.0003
                END + SIN(d.seq * 0.31) * 0.00008
            )
        END,
        d.seq + 1
    FROM daily d
    WHERE d.record_date < '2026-12-31'
)
SELECT
    1,
    record_date,
    ROUND(avg_weight, 4),
    TIMESTAMP(record_date, '08:00:00')
FROM daily;

-- 二号塘：鲤鱼 — 4 月补苗、5 月春捕、夏季生长、11 月冬捕
INSERT INTO fish_count_record (pond_id, record_date, fish_count, created_at)
WITH RECURSIVE daily AS (
    SELECT
        CAST('2026-01-01' AS DATE) AS record_date,
        2200 AS fish_count,
        CAST(0.32 AS DECIMAL(12, 4)) AS avg_weight,
        1 AS seq
    UNION ALL
    SELECT
        d.record_date + INTERVAL 1 DAY,
        GREATEST(
            150,
            d.fish_count
                + CASE WHEN d.record_date = '2026-04-10' THEN 800 ELSE 0 END
                - CASE WHEN d.record_date = '2026-05-18' THEN FLOOR(d.fish_count * 0.15) ELSE 0 END
                - CASE WHEN d.record_date = '2026-11-12' THEN FLOOR(d.fish_count * 0.35) ELSE 0 END
                - GREATEST(0, FLOOR(d.fish_count * 0.00012))
        ),
        CASE
            WHEN d.record_date = '2026-04-10' THEN
                (d.fish_count * d.avg_weight + 800 * 0.04) / (d.fish_count + 800)
            WHEN d.record_date IN ('2026-05-18', '2026-11-12') THEN d.avg_weight
            ELSE d.avg_weight * (
                1 + CASE MONTH(d.record_date + INTERVAL 1 DAY)
                    WHEN 7 THEN 0.0014 WHEN 8 THEN 0.0015 WHEN 6 THEN 0.0012
                    WHEN 5 THEN 0.0009 WHEN 9 THEN 0.0008 WHEN 4 THEN 0.0007
                    WHEN 3 THEN 0.0005 WHEN 10 THEN 0.0006
                    ELSE 0.00025
                END + SIN(d.seq * 0.23) * 0.00007
            )
        END,
        d.seq + 1
    FROM daily d
    WHERE d.record_date < '2026-12-31'
)
SELECT
    2,
    record_date,
    fish_count,
    TIMESTAMP(record_date, '08:00:00')
FROM daily;

INSERT INTO avg_weight_record (pond_id, record_date, avg_weight_kg, created_at)
WITH RECURSIVE daily AS (
    SELECT
        CAST('2026-01-01' AS DATE) AS record_date,
        2200 AS fish_count,
        CAST(0.32 AS DECIMAL(12, 4)) AS avg_weight,
        1 AS seq
    UNION ALL
    SELECT
        d.record_date + INTERVAL 1 DAY,
        GREATEST(
            150,
            d.fish_count
                + CASE WHEN d.record_date = '2026-04-10' THEN 800 ELSE 0 END
                - CASE WHEN d.record_date = '2026-05-18' THEN FLOOR(d.fish_count * 0.15) ELSE 0 END
                - CASE WHEN d.record_date = '2026-11-12' THEN FLOOR(d.fish_count * 0.35) ELSE 0 END
                - GREATEST(0, FLOOR(d.fish_count * 0.00012))
        ),
        CASE
            WHEN d.record_date = '2026-04-10' THEN
                (d.fish_count * d.avg_weight + 800 * 0.04) / (d.fish_count + 800)
            WHEN d.record_date IN ('2026-05-18', '2026-11-12') THEN d.avg_weight
            ELSE d.avg_weight * (
                1 + CASE MONTH(d.record_date + INTERVAL 1 DAY)
                    WHEN 7 THEN 0.0014 WHEN 8 THEN 0.0015 WHEN 6 THEN 0.0012
                    WHEN 5 THEN 0.0009 WHEN 9 THEN 0.0008 WHEN 4 THEN 0.0007
                    WHEN 3 THEN 0.0005 WHEN 10 THEN 0.0006
                    ELSE 0.00025
                END + SIN(d.seq * 0.23) * 0.00007
            )
        END,
        d.seq + 1
    FROM daily d
    WHERE d.record_date < '2026-12-31'
)
SELECT
    2,
    record_date,
    ROUND(avg_weight, 4),
    TIMESTAMP(record_date, '08:00:00')
FROM daily;

-- 三号塘：鲫鱼 — 3 月投苗、9 月/12 月分批捕捞
INSERT INTO fish_count_record (pond_id, record_date, fish_count, created_at)
WITH RECURSIVE daily AS (
    SELECT
        CAST('2026-01-01' AS DATE) AS record_date,
        6000 AS fish_count,
        CAST(0.10 AS DECIMAL(12, 4)) AS avg_weight,
        1 AS seq
    UNION ALL
    SELECT
        d.record_date + INTERVAL 1 DAY,
        GREATEST(
            500,
            d.fish_count
                + CASE WHEN d.record_date = '2026-03-25' THEN 1200 ELSE 0 END
                - CASE WHEN d.record_date = '2026-09-08' THEN FLOOR(d.fish_count * 0.25) ELSE 0 END
                - CASE WHEN d.record_date = '2026-12-15' THEN FLOOR(d.fish_count * 0.30) ELSE 0 END
                - GREATEST(0, FLOOR(d.fish_count * 0.00018))
        ),
        CASE
            WHEN d.record_date = '2026-03-25' THEN
                (d.fish_count * d.avg_weight + 1200 * 0.02) / (d.fish_count + 1200)
            WHEN d.record_date IN ('2026-09-08', '2026-12-15') THEN d.avg_weight
            ELSE d.avg_weight * (
                1 + CASE MONTH(d.record_date + INTERVAL 1 DAY)
                    WHEN 6 THEN 0.0012 WHEN 7 THEN 0.0013 WHEN 8 THEN 0.0012
                    WHEN 5 THEN 0.0008 WHEN 9 THEN 0.0007 WHEN 4 THEN 0.0006
                    WHEN 3 THEN 0.0004 WHEN 10 THEN 0.0005
                    ELSE 0.0002
                END + SIN(d.seq * 0.19) * 0.00006
            )
        END,
        d.seq + 1
    FROM daily d
    WHERE d.record_date < '2026-12-31'
)
SELECT
    3,
    record_date,
    fish_count,
    TIMESTAMP(record_date, '08:00:00')
FROM daily;

INSERT INTO avg_weight_record (pond_id, record_date, avg_weight_kg, created_at)
WITH RECURSIVE daily AS (
    SELECT
        CAST('2026-01-01' AS DATE) AS record_date,
        6000 AS fish_count,
        CAST(0.10 AS DECIMAL(12, 4)) AS avg_weight,
        1 AS seq
    UNION ALL
    SELECT
        d.record_date + INTERVAL 1 DAY,
        GREATEST(
            500,
            d.fish_count
                + CASE WHEN d.record_date = '2026-03-25' THEN 1200 ELSE 0 END
                - CASE WHEN d.record_date = '2026-09-08' THEN FLOOR(d.fish_count * 0.25) ELSE 0 END
                - CASE WHEN d.record_date = '2026-12-15' THEN FLOOR(d.fish_count * 0.30) ELSE 0 END
                - GREATEST(0, FLOOR(d.fish_count * 0.00018))
        ),
        CASE
            WHEN d.record_date = '2026-03-25' THEN
                (d.fish_count * d.avg_weight + 1200 * 0.02) / (d.fish_count + 1200)
            WHEN d.record_date IN ('2026-09-08', '2026-12-15') THEN d.avg_weight
            ELSE d.avg_weight * (
                1 + CASE MONTH(d.record_date + INTERVAL 1 DAY)
                    WHEN 6 THEN 0.0012 WHEN 7 THEN 0.0013 WHEN 8 THEN 0.0012
                    WHEN 5 THEN 0.0008 WHEN 9 THEN 0.0007 WHEN 4 THEN 0.0006
                    WHEN 3 THEN 0.0004 WHEN 10 THEN 0.0005
                    ELSE 0.0002
                END + SIN(d.seq * 0.19) * 0.00006
            )
        END,
        d.seq + 1
    FROM daily d
    WHERE d.record_date < '2026-12-31'
)
SELECT
    3,
    record_date,
    ROUND(avg_weight, 4),
    TIMESTAMP(record_date, '08:00:00')
FROM daily;
