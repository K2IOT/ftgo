CREATE DATABASE IF NOT EXISTS ftgo_order;
CREATE DATABASE IF NOT EXISTS ftgo_consumer;
CREATE DATABASE IF NOT EXISTS ftgo_restaurant;
CREATE DATABASE IF NOT EXISTS ftgo_kitchen;
CREATE DATABASE IF NOT EXISTS ftgo_accounting;

CREATE USER IF NOT EXISTS 'ftgo_user'@'%'
  IDENTIFIED WITH mysql_native_password BY 'ftgo_password';
ALTER USER 'ftgo_user'@'%'
  IDENTIFIED WITH mysql_native_password BY 'ftgo_password';
GRANT ALL PRIVILEGES ON *.* TO 'ftgo_user'@'%';
FLUSH PRIVILEGES;
