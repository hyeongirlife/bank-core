CREATE TABLE base_rate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_date DATE NOT NULL,
    rate DECIMAL(5,4) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_base_rate_business_date (business_date)
);

CREATE TABLE spread_rate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(20) NOT NULL,
    business_date DATE NOT NULL,
    rate DECIMAL(5,4) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_spread_rate_product_code FOREIGN KEY (product_code) REFERENCES product(code),
    UNIQUE KEY uk_spread_rate_product_date (product_code, business_date)
);

CREATE INDEX idx_spread_rate_lookup ON spread_rate(product_code, business_date, is_active);

CREATE TABLE preferential_rate (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_code VARCHAR(20) NOT NULL,
    condition_code VARCHAR(50) NOT NULL,
    business_date DATE NOT NULL,
    rate DECIMAL(5,4) NOT NULL,
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_preferential_rate_product_code FOREIGN KEY (product_code) REFERENCES product(code),
    UNIQUE KEY uk_preferential_rate_product_condition_date (product_code, condition_code, business_date)
);

CREATE INDEX idx_preferential_rate_lookup ON preferential_rate(product_code, business_date, is_active);

CREATE TABLE interest_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    business_date DATE NOT NULL,
    base_rate DECIMAL(5,4) NOT NULL,
    spread_rate DECIMAL(5,4) NOT NULL,
    preferential_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
    applied_rate DECIMAL(5,4) NOT NULL,
    balance_snapshot DECIMAL(18,2) NOT NULL,
    interest_amount DECIMAL(18,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interest_log_account_id FOREIGN KEY (account_id) REFERENCES account(id),
    UNIQUE KEY uk_interest_log_account_business_date (account_id, business_date)
);

CREATE INDEX idx_interest_log_business_date ON interest_log(business_date);
