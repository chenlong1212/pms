-- Flyway migration V3: 投喂记录表（数据见 deploy/seed_feeding_*.sql，如有需要）

CREATE TABLE feeding_record (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    pond_id    TINYINT      NOT NULL,
    feed_date  DATE         NOT NULL,
    feed_time  TIME         NOT NULL,
    remark     VARCHAR(256) NULL,
    created_at DATETIME     NOT NULL,
    updated_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_pond_feed (pond_id, feed_date DESC, feed_time DESC),
    CONSTRAINT fk_feeding_pond FOREIGN KEY (pond_id) REFERENCES pond (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
