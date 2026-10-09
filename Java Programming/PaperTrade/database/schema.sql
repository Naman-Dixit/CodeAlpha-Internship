CREATE DATABASE IF NOT EXISTS papertrade;
USE papertrade;

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100)  NOT NULL,
    email         VARCHAR(150)  NOT NULL,
    password_hash VARCHAR(255)  NOT NULL,
    balance       DECIMAL(15,2) NOT NULL DEFAULT 1000000.00,
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_balance CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS stocks (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    symbol        VARCHAR(10)   NOT NULL,
    company_name  VARCHAR(100)  NOT NULL,
    current_price DECIMAL(15,2) NOT NULL,
    sector        VARCHAR(50)   NOT NULL,
    CONSTRAINT uq_stocks_symbol UNIQUE (symbol),
    CONSTRAINT chk_stocks_price CHECK (current_price > 0)
);

CREATE TABLE IF NOT EXISTS holdings (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    user_id           INT           NOT NULL,
    stock_id          INT           NOT NULL,
    quantity          INT           NOT NULL,
    average_buy_price DECIMAL(15,4) NOT NULL,
    CONSTRAINT uq_holdings_user_stock UNIQUE (user_id, stock_id),
    CONSTRAINT fk_holdings_user  FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_holdings_stock FOREIGN KEY (stock_id) REFERENCES stocks(id),
    CONSTRAINT chk_holdings_qty CHECK (quantity > 0)
);

CREATE TABLE IF NOT EXISTS orders (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT           NOT NULL,
    stock_id   INT           NOT NULL,
    order_type ENUM('BUY','SELL')        NOT NULL,
    quantity   INT           NOT NULL,
    price      DECIMAL(15,2) NOT NULL,
    status     ENUM('EXECUTED','REJECTED') NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_orders_user  FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_orders_stock FOREIGN KEY (stock_id) REFERENCES stocks(id),
    INDEX idx_orders_user (user_id, created_at)
);

CREATE TABLE IF NOT EXISTS transactions (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    user_id          INT           NOT NULL,
    stock_id         INT           NOT NULL,
    transaction_type ENUM('BUY','SELL') NOT NULL,
    quantity         INT           NOT NULL,
    price            DECIMAL(15,2) NOT NULL,
    total_amount     DECIMAL(18,2) NOT NULL,
    `timestamp`      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_user  FOREIGN KEY (user_id)  REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_tx_stock FOREIGN KEY (stock_id) REFERENCES stocks(id),
    INDEX idx_tx_user_time (user_id, `timestamp`)
);

CREATE TABLE IF NOT EXISTS portfolio_history (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT           NOT NULL,
    portfolio_value DECIMAL(18,2) NOT NULL COMMENT 'market value of holdings',
    cash_balance    DECIMAL(18,2) NOT NULL,
    total_value     DECIMAL(18,2) NOT NULL,
    `timestamp`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ph_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_ph_user_time (user_id, `timestamp`)
);
