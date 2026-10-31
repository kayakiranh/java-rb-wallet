-- TR: Bu dosya bir kez yönetici (SYSTEM) bağlantısıyla çalıştırılır; uygulama SYSTEM kullanmaz.
-- SQL Developer: Host localhost, Port 1521, Connection type Basic, Service name FREEPDB1.
-- Worksheet'te F5 (Run Script) ile çalıştırın. Önce aşağıdaki parola yer tutucusunu değiştirin.
-- EN: One-time local PDB user setup. No existing schema or data is dropped.
WHENEVER SQLERROR EXIT SQL.SQLCODE

-- TR: Yanlışlıkla CDB$ROOT içinde kullanıcı oluşturmaya çalışmayı engelle.
BEGIN
 IF SYS_CONTEXT('USERENV','CON_NAME') <> 'FREEPDB1' THEN
  RAISE_APPLICATION_ERROR(-20001, 'SYSTEM ile FREEPDB1 servisine baglanin.');
 END IF;
END;
/

CREATE USER RBWALLET IDENTIFIED BY "CHANGE_ME_UseYourOwnPassword"
 DEFAULT TABLESPACE USERS
 QUOTA 100M ON USERS;

-- TR: CREATE TABLE, Flyway ve uygulama tabloları içindir. DBA/RESOURCE rolü verilmez.
GRANT CREATE SESSION, CREATE TABLE TO RBWALLET;

-- TR: Sonraki adım: IDE'de WALLET_DB_PASSWORD değerine aynı parolayı girip WalletApplication'ı başlatın.
-- Flyway tablo/index/constraint'leri kendisi oluşturur. Bu scripti her açılışta tekrar çalıştırmayın.
