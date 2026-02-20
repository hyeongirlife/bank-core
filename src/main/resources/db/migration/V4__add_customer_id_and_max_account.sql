-- account 테이블에 고객 ID 추가
ALTER TABLE account ADD COLUMN customer_id BIGINT NOT NULL AFTER id;
CREATE INDEX idx_account_customer ON account(customer_id);

-- product 테이블에 1인 최대 계좌 수 추가 (0 = 무제한)
ALTER TABLE product ADD COLUMN max_account_per_customer INT NOT NULL DEFAULT 0 AFTER interest_rate;
