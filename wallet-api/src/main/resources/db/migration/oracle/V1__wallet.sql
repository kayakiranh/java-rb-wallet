-- TR: Flyway bu dosyayı RBWALLET şemasında bir kez çalıştırır; tablolar elle oluşturulmaz.
-- EN: Oracle-specific initial schema. It is separate from the former SQL Server migration.
-- TR: Oracle'da şema kullanıcıya aittir. NUMBER(18,2) para, NUMBER(19) long alanlar içindir.
-- UUID değerleri Java'da üretilir; sequence/identity gerektirmez.
CREATE TABLE users (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 name NVARCHAR2(100) NOT NULL,
 email VARCHAR2(254 CHAR) NOT NULL UNIQUE,
 password_hash VARCHAR2(100 CHAR) NOT NULL
);
CREATE TABLE wallets (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 user_id VARCHAR2(36 CHAR) NOT NULL UNIQUE REFERENCES users(id),
 -- TR: NUMBER Oracle anahtar sözcüğüdür; kolon adı wallet_number olarak seçildi.
 wallet_number VARCHAR2(32 CHAR) NOT NULL UNIQUE,
 balance NUMBER(18,2) DEFAULT 0 NOT NULL,
 reserved NUMBER(18,2) DEFAULT 0 NOT NULL,
 version NUMBER(19) DEFAULT 0 NOT NULL,
 CONSTRAINT ck_wallet_funds CHECK(balance>=0 AND reserved>=0 AND reserved<=balance)
);
CREATE TABLE wallet_transactions (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 wallet_id VARCHAR2(36 CHAR) NOT NULL REFERENCES wallets(id),
 recipient_wallet_id VARCHAR2(36 CHAR) REFERENCES wallets(id),
 request_key VARCHAR2(36 CHAR) NOT NULL,
 kind VARCHAR2(20 CHAR) NOT NULL,
 status VARCHAR2(20 CHAR) NOT NULL,
 amount NUMBER(18,2) NOT NULL CHECK(amount>0),
 target VARCHAR2(50 CHAR) NOT NULL,
 failure_reason NVARCHAR2(300),
 -- TR: UTC epoch millis, Java Instant ile dönüştürülür; sunucunun tarih formatına bağlı değildir.
 created_at NUMBER(19) NOT NULL,
 next_attempt_at NUMBER(19) NOT NULL,
 recovery_attempts NUMBER(10) DEFAULT 0 NOT NULL,
 version NUMBER(19) DEFAULT 0 NOT NULL,
 CONSTRAINT uq_wallet_request UNIQUE(wallet_id,request_key),
 CONSTRAINT ck_transaction_kind CHECK(kind IN ('TopUp','Transfer','BillPayment')),
 CONSTRAINT ck_transaction_status CHECK(status IN ('Pending','Completed','Rejected'))
);
CREATE INDEX ix_pending_operations ON wallet_transactions(status,next_attempt_at);
CREATE TABLE notifications (
 id VARCHAR2(36 CHAR) PRIMARY KEY,
 transaction_id VARCHAR2(36 CHAR) NOT NULL REFERENCES wallet_transactions(id),
 user_id VARCHAR2(36 CHAR) NOT NULL REFERENCES users(id),
 attempts NUMBER(10) DEFAULT 0 NOT NULL,
 delivered_at NUMBER(19),
 next_attempt_at NUMBER(19) NOT NULL,
 last_error VARCHAR2(100 CHAR),
 CONSTRAINT uq_notification UNIQUE(transaction_id,user_id)
);
CREATE INDEX ix_pending_notifications ON notifications(delivered_at,next_attempt_at);
