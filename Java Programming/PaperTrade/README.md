# PaperTrade — Java Stock Trading & Portfolio Management Platform

> **PaperTrade is a paper-trading *simulation*.** All money is virtual and all prices are randomly
> simulated. It is **not** a real trading system and does **not** show real market data.

## 1. Problem statement
Simulate a basic stock-trading environment: register, view a live-changing market, buy/sell shares with
virtual money, track holdings, profit/loss and portfolio performance over time, with all data persisted in MySQL.

## 2. Features
- Register / login with validated input and **PBKDF2-HMAC-SHA256 salted password hashing** (JDK only)
- ₹10,00,000 virtual balance per new user (Indian digit grouping in the UI)
- 8 simulated stocks; prices move every 5 s via a **`ScheduledExecutorService`** random walk (never ≤ ₹1.00)
- Buy / sell with correct **weighted-average buy price**; each order is **one JDBC transaction** (commit / rollback)
- Rejected orders (insufficient balance / shares) are audited in `orders` with status `REJECTED`
- Holdings view with invested value, current value, P/L and P/L % per stock; portfolio totals
- Transaction history from MySQL + **export to `transaction_history_<name>.txt`** (File I/O)
- Portfolio snapshots (on login/logout/trade and by a **scheduled background job**) and a text performance report
- Every input validated; invalid input never crashes the app and no stack traces reach the user

## 3. Technologies
Java 21 · Maven · JDBC · MySQL 8 (MySQL Connector/J) · JUnit 5. No frameworks, no ORM.

## 4. Architecture
```
ui  ->  service  ->  dao  ->  database (JDBC/MySQL)
          |  \-> market (MarketDataProvider, MarketPriceSimulator)
          \-> model      util      exception
```
| Package | Responsibility |
|---|---|
| `model` | `User`, `Stock`, `Holding`, `Portfolio`, `Order` (+`BuyOrder`/`SellOrder`), `Transaction` |
| `dao` | One DAO per table; `PreparedStatement` + try-with-resources only |
| `service` | `AuthService`, `MarketService`, `TradingService`, `PortfolioService`, `TransactionService` |
| `market` | `MarketDataProvider` interface, `MarketPriceSimulator` (scheduler) |
| `ui` | `MainMenu`, `AuthMenu`, `TradingMenu`, `ConsoleInput` |
| `util` | `PasswordUtil`, `InputValidator`, `ConsoleFormatter` |
| `exception` | Custom exceptions (checked business errors; unchecked `DatabaseException`) |

**Concurrency design:** `Stock` is immutable; `MarketService` swaps instances atomically in a `ConcurrentHashMap`,
so the simulator thread and the UI thread never race. A trade reads the price once and uses it for the whole order.
In the database, `SELECT ... FOR UPDATE` locks the user and holding rows during a trade.

## 5. OOP concepts used
- **Encapsulation:** private fields + getters/setters; immutable `Stock`
- **Abstraction:** `MarketDataProvider` interface (services depend on it, tests can fake it)
- **Inheritance & polymorphism:** `Order` → `BuyOrder` / `SellOrder` override `getType()` and `cashImpact()`; `TradingService` applies `order.cashImpact()` without branching on cash direction
- **Composition:** `User` has a `Portfolio`; `Portfolio` has `Holding`s; `Order`/`Holding` have a `Stock`
- **Collections / Streams:** `ConcurrentHashMap`, `List`, `Optional`, stream reductions
- **Exceptions:** custom hierarchy rooted at `PaperTradeException`

## 6. Database schema
Tables: `users`, `stocks`, `holdings`, `orders`, `transactions`, `portfolio_history`
(see `database/schema.sql`). Primary/foreign keys, `UNIQUE(email)`, `UNIQUE(symbol)`, `UNIQUE(user_id, stock_id)`,
`NOT NULL`, `CHECK` constraints and indexes on `(user_id, timestamp)`.

## 7. Application flow
```
Main Menu -> Register | Login -> Dashboard -> Market | Buy | Sell | Portfolio |
                                              Performance | Transactions | Account | Export | Logout
          -> Exit
```

## 8. Console output (captured from a real run)
```
========================================
          PAPERTRADE DASHBOARD
========================================

Welcome, Naman

Cash Balance:    ₹10,00,000.00
Portfolio Value: ₹10,00,000.00
Total P/L:       ₹0.00  (+0.00%)
```
```
SUCCESS: Bought 10 x AAPL @ ₹220.45
Total cost: ₹2,204.50

ERROR: Insufficient balance.

Required:  ₹5,20,89,479.10
Available: ₹9,97,795.50

ERROR: Insufficient shares of TSLA.

Requested: 1
Owned:     0
```
```
==============================================================================
 SYMBOL    QTY       AVG BUY         PRICE       INVESTED          VALUE     P/L %
==============================================================================
 AAPL        6       ₹220.45       ₹220.45      ₹1,322.70      ₹1,322.70    +0.00%
==============================================================================
Cash Balance:       ₹9,98,677.30
Stock Value:        ₹1,322.70
Total Portfolio:    ₹10,00,000.00
```
(P/L is 0 here because the run lasted less than one 5-second price update.)

## 9. Setup
**Requirements:** JDK 21, Maven 3.9+, MySQL 8.x.

### MySQL
```bash
mysql -u root -p < database/schema.sql
mysql -u root -p < database/seed.sql
```
### Configuration
```bash
cp config.example.properties config.properties   # then edit db.username / db.password
```
`config.properties` is git-ignored. Optional: `market.update.seconds`, `snapshot.interval.seconds`.

### Run
```bash
mvn compile exec:java
```
(Use a UTF-8 terminal so `₹` renders correctly.)

### Test
```bash
mvn test
```
Unit tests (validation, hashing, P/L maths, formatter, simulator + concurrency) need no database.
`TradingIntegrationTest` (registration, duplicate email, login, buy, sell, insufficient balance/shares,
portfolio calculation, snapshots, export) runs against your MySQL and is **skipped automatically** if it is unreachable.
It creates uniquely named test users and deletes them afterwards.

## 10. Future improvements
Limit orders, pagination for long histories, a leaderboard, configurable volatility per stock, a REST layer.
