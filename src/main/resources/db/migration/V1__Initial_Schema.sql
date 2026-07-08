-- Coin of the Realm: initial banking schema (v3)
-- Flyway applies the configured table prefix via the ${tablePrefix} placeholder.

CREATE TABLE ${tablePrefix}banks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    owner_uuid VARCHAR(36) NOT NULL,
    world_name VARCHAR(255),
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_${tablePrefix}banks_owner ON ${tablePrefix}banks(owner_uuid);
CREATE INDEX idx_${tablePrefix}banks_world ON ${tablePrefix}banks(world_name);
CREATE INDEX idx_${tablePrefix}banks_owner_world ON ${tablePrefix}banks(owner_uuid, world_name);

CREATE TABLE ${tablePrefix}account_memberships (
    account_name VARCHAR(255) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (account_name, player_uuid)
);

CREATE INDEX idx_${tablePrefix}account_memberships_account ON ${tablePrefix}account_memberships(account_name);
CREATE INDEX idx_${tablePrefix}account_memberships_player ON ${tablePrefix}account_memberships(player_uuid);

CREATE TABLE ${tablePrefix}daily_transactions (
    account_name VARCHAR(255) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    date VARCHAR(10) NOT NULL,
    deposit_total INT NOT NULL DEFAULT 0,
    withdraw_total INT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_name, player_uuid, date)
);

CREATE INDEX idx_${tablePrefix}daily_transactions_account_date ON ${tablePrefix}daily_transactions(account_name, date);
CREATE INDEX idx_${tablePrefix}daily_transactions_player_date ON ${tablePrefix}daily_transactions(player_uuid, date);

CREATE TABLE ${tablePrefix}emerald_region_stats (
    region_id VARCHAR(255) NOT NULL PRIMARY KEY,
    mined_total INT NOT NULL DEFAULT 0,
    loot_total INT NOT NULL DEFAULT 0,
    mob_total INT NOT NULL DEFAULT 0,
    trading_total INT NOT NULL DEFAULT 0,
    bank_in_total INT NOT NULL DEFAULT 0,
    bank_out_total INT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL
);

CREATE TABLE ${tablePrefix}emerald_exchange_rates (
    region_id VARCHAR(255) NOT NULL PRIMARY KEY,
    current_rate INT NOT NULL,
    updated_at BIGINT NOT NULL
);
