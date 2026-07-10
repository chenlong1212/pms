-- Flyway migration V1: 创建设备数据采集记录表
CREATE TABLE device_data_record (
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
