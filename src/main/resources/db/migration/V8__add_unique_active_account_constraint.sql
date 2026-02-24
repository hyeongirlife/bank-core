ALTER TABLE account
    ADD COLUMN active_product_code VARCHAR(20)
        GENERATED ALWAYS AS (
            CASE
                WHEN status = 'ACTIVE' THEN product_code
                ELSE NULL
            END
        ) STORED;

CREATE UNIQUE INDEX ux_account_customer_active_product
    ON account (customer_id, active_product_code);
