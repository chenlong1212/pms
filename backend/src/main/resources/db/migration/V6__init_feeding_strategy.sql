CREATE TABLE feeding_strategy (
    pond_id        TINYINT       NOT NULL,
    daily_rate     DECIMAL(5, 4) NOT NULL DEFAULT 0.0250,
    meals_per_day  TINYINT       NOT NULL DEFAULT 2,
    feed_time_1    TIME          NOT NULL DEFAULT '08:00:00',
    feed_time_2    TIME          NULL DEFAULT '17:00:00',
    feed_time_3    TIME          NULL,
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NOT NULL,
    PRIMARY KEY (pond_id),
    CONSTRAINT fk_feeding_strategy_pond FOREIGN KEY (pond_id) REFERENCES pond (id),
    CONSTRAINT chk_feeding_strategy_rate CHECK (daily_rate BETWEEN 0.0200 AND 0.0300),
    CONSTRAINT chk_feeding_strategy_meals CHECK (meals_per_day BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO feeding_strategy
    (pond_id, daily_rate, meals_per_day, feed_time_1, feed_time_2, feed_time_3, created_at, updated_at)
SELECT id, 0.0250, 2, '08:00:00', '17:00:00', NULL, NOW(), NOW()
FROM pond;
