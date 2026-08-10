/*
 * Copyright 2024 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

CREATE TABLE IF NOT EXISTS `system_config`
(
    `id`           INT(11) NOT NULL AUTO_INCREMENT,
    `conf_key`     VARCHAR(128)   DEFAULT NULL,
    `conf_value`   VARCHAR(15000) DEFAULT NULL,
    `gmt_create`   DATETIME       DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME       DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `conf_key` (`conf_key`)
);

CREATE TABLE IF NOT EXISTS `bcdns_service`
(
    `id`           INT(11) PRIMARY KEY NOT NULL AUTO_INCREMENT,
    `domain_space` VARCHAR(128)        NOT NULL,
    `parent_space` VARCHAR(128),
    `owner_oid`    VARCHAR(255)        NOT NULL,
    `type`         VARCHAR(32)         NOT NULL,
    `state`        INT                 NOT NULL,
    `properties`   BLOB,
    `gmt_create`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `domain_space_cert`
(
    `id`                INT(11)      NOT NULL AUTO_INCREMENT,
    `domain_space`      VARCHAR(128) DEFAULT NULL,
    `parent_space`      VARCHAR(128) DEFAULT NULL,
    `owner_oid_hex`     VARCHAR(255) NOT NULL,
    `description`       VARCHAR(128) DEFAULT NULL,
    `domain_space_cert` BLOB,
    `gmt_create`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `bta`
(
    `id`              INT(11) PRIMARY KEY NOT NULL AUTO_INCREMENT,
    `product`         VARCHAR(64) DEFAULT NULL,
    `domain`          VARCHAR(128)        NOT NULL,
    `bta_version`     INT(11)     DEFAULT NULL,
    `subject_version` INT(11)     DEFAULT NULL,
    `raw_bta`         BLOB,
    `gmt_create`      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`    DATETIME    DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `tpbta`
(
    `id`                        INT(11) PRIMARY KEY NOT NULL AUTO_INCREMENT,
    `version`                   INT(11)      DEFAULT NULL,
    `sender_domain`             VARCHAR(128)        NOT NULL,
    `bta_subject_version`       INT(11)      DEFAULT NULL,
    `sender_id`                 VARCHAR(64)  DEFAULT NULL,
    `receiver_domain`           VARCHAR(128) DEFAULT NULL,
    `receiver_id`               VARCHAR(64)  DEFAULT NULL,
    `tpbta_version`             INT(11)      DEFAULT NULL,
    `ptc_verify_anchor_version` INT(11)      DEFAULT NULL,
    `raw_tpbta`                 BLOB,
    `gmt_create`                DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified`              DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `validated_consensus_states`
(
    `id`           INT(11) PRIMARY KEY NOT NULL AUTO_INCREMENT,
    `cs_version`   INT(11)             NOT NULL,
    `domain`       VARCHAR(128)        NOT NULL,
    `height`       BIGINT UNSIGNED     NOT NULL,
    `hash`         VARCHAR(64) DEFAULT NULL,
    `parent_hash`  VARCHAR(64) DEFAULT NULL,
    `raw_vcs`      BLOB,
    `gmt_create`   DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `gmt_modified` DATETIME    DEFAULT CURRENT_TIMESTAMP
);
