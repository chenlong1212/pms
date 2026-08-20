-- Flyway migration V7: 池塘放养参数表，用于指数增长模型自动推算生物量

CREATE TABLE pond_setup (
    pond_id            TINYINT         NOT NULL,
    stock_date         DATE            NOT NULL,
    initial_fish_count INT             NOT NULL,
    initial_weight_kg  DECIMAL(10, 4)  NOT NULL DEFAULT 0.0500,
    harvest_date       DATE            NULL,
    final_fish_count   INT             NULL,
    final_weight_kg    DECIMAL(10, 4)  NULL,
    created_at         DATETIME        NOT NULL,
    updated_at         DATETIME        NOT NULL,
    PRIMARY KEY (pond_id),
    CONSTRAINT fk_setup_pond FOREIGN KEY (pond_id) REFERENCES pond (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
