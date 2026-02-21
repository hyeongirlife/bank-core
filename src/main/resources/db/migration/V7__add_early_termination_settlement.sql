ALTER TABLE account
    ADD COLUMN maturity_date DATE NULL AFTER closed_at;

ALTER TABLE `transaction`
    MODIFY COLUMN type ENUM('DEPOSIT','WITHDRAWAL','TRANSFER_IN','TRANSFER_OUT','INTEREST_SETTLEMENT')
    NOT NULL COMMENT '거래 유형';

CREATE TABLE interest_settlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    settlement_type ENUM('EARLY_TERMINATION') NOT NULL,
    business_date DATE NOT NULL,
    interest_amount DECIMAL(18,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_interest_settlement_account_id FOREIGN KEY (account_id) REFERENCES account(id),
    UNIQUE KEY uk_interest_settlement_account_type (account_id, settlement_type)
);

CREATE INDEX idx_interest_settlement_business_date ON interest_settlement(business_date);
