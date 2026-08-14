CREATE TABLE production_report (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    pond_id             TINYINT        NOT NULL,
    report_type         VARCHAR(16)    NOT NULL,
    start_date          DATE           NOT NULL,
    end_date            DATE           NOT NULL,
    title               VARCHAR(128)   NOT NULL,
    status              VARCHAR(16)    NOT NULL,
    data_snapshot       JSON           NOT NULL,
    content             MEDIUMTEXT     NOT NULL,
    model_provider      VARCHAR(32)    NULL,
    model_name          VARCHAR(128)   NULL,
    created_at          DATETIME       NOT NULL,
    updated_at          DATETIME       NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_report_pond_date (pond_id, end_date DESC),
    CONSTRAINT fk_report_pond FOREIGN KEY (pond_id) REFERENCES pond (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
