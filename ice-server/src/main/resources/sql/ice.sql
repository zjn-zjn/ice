CREATE DATABASE IF NOT EXISTS ice Character Set utf8mb4;

-- ----------------------------
-- Table structure for ice_app
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ice_app` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'application name',
  `info` varchar(500) COLLATE utf8mb4_bin DEFAULT '',
  `status` tinyint(1) NOT NULL DEFAULT '1',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for ice_base
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ice_base` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_bin DEFAULT NULL,
  `app` int(11) NOT NULL COMMENT 'remote application id',
  `scenes` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL COMMENT 'scenes(mutli scene split with ,)',
  `status` tinyint(11) NOT NULL DEFAULT '1' COMMENT '1 online 0 offline',
  `conf_id` bigint(20) DEFAULT NULL,
  `time_type` tinyint(11) DEFAULT '1' COMMENT 'see TimeTypeEnum',
  `start` datetime(3) DEFAULT NULL,
  `end` datetime(3) DEFAULT NULL,
  `debug` tinyint(4) NOT NULL DEFAULT '1',
  `priority` bigint(20) DEFAULT '1',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `update_index` (`update_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for ice_conf
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ice_conf` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `app` int(11) NOT NULL COMMENT 'remote application id',
  `name` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL,
  `son_ids` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL,
  `type` tinyint(4) NOT NULL DEFAULT '6' COMMENT 'see NodeTypeEnum',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1 online 0 offline',
  `inverse` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'make true->false false->true',
  `conf_name` varchar(1000) COLLATE utf8mb4_bin DEFAULT '' COMMENT 'leaf node class name',
  `conf_field` varchar(5000) COLLATE utf8mb4_bin DEFAULT '' COMMENT 'leaf node json config',
  `forward_id` bigint(20) DEFAULT NULL,
  `time_type` tinyint(11) NOT NULL DEFAULT '1' COMMENT 'see TimeTypeEnum',
  `start` datetime(3) DEFAULT NULL,
  `end` datetime(3) DEFAULT NULL,
  `debug` tinyint(4) NOT NULL DEFAULT '1',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `update_index` (`update_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for ice_conf_update
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ice_conf_update` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `app` int(11) NOT NULL COMMENT 'remote application id',
  `ice_id` bigint(20) NOT NULL,
  `conf_id` bigint(20) NOT NULL,
  `name` varchar(50) COLLATE utf8mb4_bin DEFAULT NULL,
  `son_ids` varchar(1000) COLLATE utf8mb4_bin DEFAULT NULL,
  `type` tinyint(4) NOT NULL DEFAULT '6' COMMENT 'see NodeTypeEnum',
  `status` tinyint(4) NOT NULL DEFAULT '1' COMMENT '1 online 0 offline',
  `inverse` tinyint(4) NOT NULL DEFAULT '0' COMMENT 'make true->false false->true',
  `conf_name` varchar(1000) COLLATE utf8mb4_bin DEFAULT '' COMMENT 'leaf node class name',
  `conf_field` varchar(5000) COLLATE utf8mb4_bin DEFAULT '' COMMENT 'leaf node json config',
  `forward_id` bigint(20) DEFAULT NULL,
  `time_type` tinyint(11) NOT NULL DEFAULT '1' COMMENT 'see TimeTypeEnum',
  `start` datetime(3) DEFAULT NULL,
  `end` datetime(3) DEFAULT NULL,
  `debug` tinyint(4) NOT NULL DEFAULT '1',
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `update_index` (`update_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for ice_push_history
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ice_push_history` (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `app` int(11) NOT NULL,
  `ice_id` bigint(20) DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `push_data` longtext COLLATE utf8mb4_unicode_ci,
  `operator` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `create_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ----------------------------
-- Table structure for ice_rmi
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ice_rmi` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `app` int(12) NOT NULL,
  `host` varchar(255) COLLATE utf8mb4_bin NOT NULL,
  `port` int(12) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;