-- 生产环境数据库初始化
-- Windows 用法: mysql -u root -p < deploy\init-prod.sql
-- 或在 MySQL 客户端中 source 本文件

CREATE DATABASE IF NOT EXISTS pms_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pms_prod;

CREATE TABLE IF NOT EXISTS device_data_record (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    device_id        VARCHAR(32)   NOT NULL,
    dox              DECIMAL(10,2) NOT NULL,
    ph               DECIMAL(10,2) NOT NULL,
    thw              DECIMAL(10,2) NOT NULL,
    collect_time_str VARCHAR(32)   NOT NULL,
    created_at       DATETIME      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_collect_time (device_id, collect_time_str),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
