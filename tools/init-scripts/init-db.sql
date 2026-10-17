-- Initializes the three separate database contexts atomically at Docker runtime startup
CREATE DATABASE dfls_identity;
CREATE DATABASE dfls_account;
CREATE DATABASE dfls_transfer;