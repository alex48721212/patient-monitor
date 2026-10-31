-- H2(MySQL 모드)와 MySQL 양쪽에서 동작하는 스키마

DROP TABLE IF EXISTS alarm;
DROP TABLE IF EXISTS vital_sign;
DROP TABLE IF EXISTS patient;

CREATE TABLE patient (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(50)  NOT NULL,
    birth_date   DATE         NOT NULL,
    gender       CHAR(1)      NOT NULL,          -- 'M' / 'F'
    room_number  VARCHAR(20)  NOT NULL,
    diagnosis    VARCHAR(200),
    status       VARCHAR(20)  NOT NULL DEFAULT 'ADMITTED',  -- ADMITTED / DISCHARGED
    admitted_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE vital_sign (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id        BIGINT        NOT NULL,
    heart_rate        INT           NOT NULL,   -- bpm
    systolic_bp       INT           NOT NULL,   -- mmHg
    diastolic_bp      INT           NOT NULL,   -- mmHg
    spo2              INT           NOT NULL,   -- %
    temperature       DECIMAL(4,1)  NOT NULL,   -- °C
    respiratory_rate  INT           NOT NULL,   -- 회/분
    measured_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vital_patient FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE
);

CREATE INDEX idx_vital_patient_time ON vital_sign (patient_id, measured_at);

CREATE TABLE alarm (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id       BIGINT        NOT NULL,
    vital_sign_id    BIGINT        NOT NULL,
    vital_type       VARCHAR(30)   NOT NULL,   -- HEART_RATE, SPO2 ...
    measured_value   DECIMAL(6,1)  NOT NULL,
    severity         VARCHAR(10)   NOT NULL,   -- WARNING / CRITICAL
    message          VARCHAR(200)  NOT NULL,
    acknowledged     BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_alarm_patient FOREIGN KEY (patient_id) REFERENCES patient(id) ON DELETE CASCADE,
    CONSTRAINT fk_alarm_vital   FOREIGN KEY (vital_sign_id) REFERENCES vital_sign(id) ON DELETE CASCADE
);

CREATE INDEX idx_alarm_ack_time ON alarm (acknowledged, created_at);
