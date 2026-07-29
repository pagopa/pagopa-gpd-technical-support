CREATE SCHEMA IF NOT EXISTS apd;

CREATE SEQUENCE IF NOT EXISTS apd.payment_pos_seq START WITH 1 INCREMENT BY 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS apd.payment_opt_seq START WITH 1 INCREMENT BY 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS apd.transfer_seq START WITH 1 INCREMENT BY 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS apd.payment_opt_metadata_seq START WITH 1 INCREMENT BY 1 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS apd.transfer_metadata_seq START WITH 1 INCREMENT BY 1 CACHE 1;

CREATE TABLE IF NOT EXISTS apd.payment_position (
  id bigint PRIMARY KEY,
  iupd varchar(64) NOT NULL,
  organization_fiscal_code varchar(32) NOT NULL,
  service_type varchar(16) NOT NULL,
  status varchar(32) NOT NULL,
  inserted_date timestamp NOT NULL,
  archived boolean NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS apd.payment_option (
  id bigint PRIMARY KEY,
  payment_position_id bigint NOT NULL REFERENCES apd.payment_position(id),
  organization_fiscal_code varchar(32) NOT NULL,
  nav varchar(32) NOT NULL,
  iuv varchar(32) NOT NULL,
  status varchar(32) NOT NULL,
  payment_plan_id varchar(64),
  archived boolean NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS apd.transfer (
  id bigint PRIMARY KEY,
  payment_option_id bigint NOT NULL REFERENCES apd.payment_option(id)
);

CREATE TABLE IF NOT EXISTS apd.payment_option_metadata (
  id bigint PRIMARY KEY,
  payment_option_id bigint NOT NULL REFERENCES apd.payment_option(id),
  key varchar(64),
  value varchar(255)
);

CREATE TABLE IF NOT EXISTS apd.transfer_metadata (
  id bigint PRIMARY KEY,
  transfer_id bigint NOT NULL REFERENCES apd.transfer(id),
  key varchar(64),
  value varchar(255)
);

CREATE INDEX IF NOT EXISTS payment_position_inserted_date_idx
  ON apd.payment_position(inserted_date);
CREATE INDEX IF NOT EXISTS payment_option_payment_position_id_idx
  ON apd.payment_option(payment_position_id);
