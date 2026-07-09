-- 本地环境数据库
CREATE DATABASE IF NOT EXISTS pms_local DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 生产环境数据库
CREATE DATABASE IF NOT EXISTS pms_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 表结构（本地和生产库各执行一次，或切换库后执行）
USE pms_local;

CREATE TABLE IF NOT EXISTS device_data_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    device_id        VARCHAR(32)  NOT NULL,
    dox              DECIMAL(10,2) NOT NULL,
    ph               DECIMAL(10,2) NOT NULL,
    thw              DECIMAL(10,2) NOT NULL,
    collect_time_str VARCHAR(32)  NOT NULL,
    created_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_collect_time (device_id, collect_time_str),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE pms_prod;

CREATE TABLE IF NOT EXISTS device_data_record (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    device_id        VARCHAR(32)  NOT NULL,
    dox              DECIMAL(10,2) NOT NULL,
    ph               DECIMAL(10,2) NOT NULL,
    thw              DECIMAL(10,2) NOT NULL,
    collect_time_str VARCHAR(32)  NOT NULL,
    created_at       DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_collect_time (device_id, collect_time_str),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
