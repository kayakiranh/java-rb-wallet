-- TR: Flyway, EF Migration karşılığıdır; başarıyla uygulanan migration tekrar çalıştırılmaz.
-- UUID'ler varchar(36); para decimal(18,2); zaman UTC epoch millis olarak saklanır.
CREATE TABLE users (id varchar(36) PRIMARY KEY, name nvarchar(100) NOT NULL, email varchar(254) NOT NULL UNIQUE, password_hash varchar(100) NOT NULL);
CREATE TABLE wallets (
 id varchar(36) PRIMARY KEY, user_id varchar(36) NOT NULL UNIQUE REFERENCES users(id), number varchar(32) NOT NULL UNIQUE,
 balance decimal(18,2) NOT NULL DEFAULT 0, reserved decimal(18,2) NOT NULL DEFAULT 0, version bigint NOT NULL DEFAULT 0,
 CONSTRAINT ck_wallet_funds CHECK(balance>=0 AND reserved>=0 AND reserved<=balance));
CREATE TABLE wallet_transactions (
 id varchar(36) PRIMARY KEY, wallet_id varchar(36) NOT NULL REFERENCES wallets(id), recipient_wallet_id varchar(36) NULL REFERENCES wallets(id),
 request_key varchar(36) NOT NULL, kind varchar(20) NOT NULL, status varchar(20) NOT NULL,
 amount decimal(18,2) NOT NULL CHECK(amount>0), target varchar(50) NOT NULL, failure_reason nvarchar(300),
 created_at bigint NOT NULL, next_attempt_at bigint NOT NULL, recovery_attempts int NOT NULL DEFAULT 0, version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uq_wallet_request UNIQUE(wallet_id,request_key),
 CONSTRAINT ck_transaction_kind CHECK(kind IN ('TopUp','Transfer','BillPayment')),
 CONSTRAINT ck_transaction_status CHECK(status IN ('Pending','Completed','Rejected')));
CREATE INDEX ix_pending_operations ON wallet_transactions(status,next_attempt_at);
CREATE TABLE notifications (
 id varchar(36) PRIMARY KEY, transaction_id varchar(36) NOT NULL REFERENCES wallet_transactions(id), user_id varchar(36) NOT NULL REFERENCES users(id),
 attempts int NOT NULL DEFAULT 0, delivered_at bigint NULL, next_attempt_at bigint NOT NULL, last_error varchar(100),
 CONSTRAINT uq_notification UNIQUE(transaction_id,user_id));
CREATE INDEX ix_pending_notifications ON notifications(delivered_at,next_attempt_at);
