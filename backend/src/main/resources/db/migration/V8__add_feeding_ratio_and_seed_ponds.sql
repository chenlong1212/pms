-- Flyway migration V8: 加入投喂比例字段 + 初始化 3 个池塘的放养参数（对齐 gp 项目）
-- 每个池塘设不同的初始鱼数和初始平均重量，用于离散日增长模型推算生物量

-- 1. 创建 pond 表（若不存在）并插入 3 个池塘基础数据
CREATE TABLE IF NOT EXISTS pond (
    id           TINYINT      NOT NULL,
    name         VARCHAR(32)  NOT NULL,
    fish_species VARCHAR(16)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO pond (id, name, fish_species) VALUES
    (1, '池塘 A', '金鲳鱼'),
    (2, '池塘 B', '金鲳鱼'),
    (3, '池塘 C', '金鲳鱼');

-- 2. 为 pond_setup 表添加 feeding_ratio 列（仅当列尚不存在时）
-- MySQL 不支持 ADD COLUMN IF NOT EXISTS，用 information_schema 判断后动态执行
SET @added = 0;
SELECT COUNT(*) INTO @added
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND table_name   = 'pond_setup'
   AND column_name  = 'feeding_ratio';

SET @sql = IF(@added = 0,
    'ALTER TABLE pond_setup ADD COLUMN feeding_ratio DECIMAL(5,4) NOT NULL DEFAULT 0.0160 AFTER initial_weight_kg',
    'SELECT ''feeding_ratio column already exists'' AS msg');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 同步 3 个池塘的放养参数
-- 池塘A：中型体型，中等投喂率（类比 gp 01-08号网箱 1.6%）
-- 池塘B：较大体型，较高投喂率（类比 gp 09-13号网箱 3.7%）
-- 池塘C：小型体型，较低投喂率（类比 gp 17-19号网箱 1.5%）
UPDATE pond_setup SET
    stock_date            = '2025-10-01',
    initial_fish_count    = 68000,
    initial_weight_kg     = 0.6000,
    feeding_ratio         = 0.0160,
    harvest_date          = '2026-12-31',
    final_fish_count      = 400,
    final_weight_kg       = 1.2000,
    updated_at            = NOW()
WHERE pond_id = 1;

UPDATE pond_setup SET
    stock_date            = '2025-10-01',
    initial_fish_count    = 67000,
    initial_weight_kg     = 0.5300,
    feeding_ratio         = 0.0370,
    harvest_date          = '2026-12-31',
    final_fish_count      = 380,
    final_weight_kg       = 0.8000,
    updated_at            = NOW()
WHERE pond_id = 2;

UPDATE pond_setup SET
    stock_date            = '2025-10-01',
    initial_fish_count    = 65000,
    initial_weight_kg     = 0.4600,
    feeding_ratio         = 0.0150,
    harvest_date          = '2026-12-01',
    final_fish_count      = 2000,
    final_weight_kg       = 0.3000,
    updated_at            = NOW()
WHERE pond_id = 3;
