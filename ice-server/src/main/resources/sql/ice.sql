-- 达梦数据库不支持这种创建数据库语法，请在达梦管理工具中手动创建数据库

-- ----------------------------
-- Table structure for ice_app
-- ----------------------------
CREATE TABLE IF NOT EXISTS "ice_app" (
  "id" BIGINT NOT NULL IDENTITY(1,1),
  "name" VARCHAR(200) DEFAULT NULL,
  "info" VARCHAR(500) DEFAULT '',
  "status" SMALLINT NOT NULL DEFAULT 1,
  "create_at" TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_at" TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY ("id")
);

-- ----------------------------
-- Table structure for ice_base
-- ----------------------------
CREATE TABLE IF NOT EXISTS "ice_base" (
  "id" BIGINT NOT NULL IDENTITY(1,1),
  "name" VARCHAR(200) DEFAULT NULL,
  "app" INT NOT NULL,
  "scenes" VARCHAR(1000) DEFAULT NULL,
  "status" SMALLINT NOT NULL DEFAULT 1,
  "conf_id" BIGINT DEFAULT NULL,
  "time_type" SMALLINT DEFAULT 1,
  "start" TIMESTAMP(3) DEFAULT NULL,
  "end" TIMESTAMP(3) DEFAULT NULL,
  "debug" SMALLINT NOT NULL DEFAULT 1,
  "priority" BIGINT DEFAULT 1,
  "create_at" TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_at" TIMESTAMP(3) NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "update_base_index" ON "ice_base" ("update_at");

-- ----------------------------
-- Table structure for ice_conf
-- ----------------------------
CREATE TABLE IF NOT EXISTS "ice_conf" (
  "id" BIGINT NOT NULL IDENTITY(1,1),
  "app" INT NOT NULL,
  "name" VARCHAR(50) DEFAULT NULL,
  "son_ids" VARCHAR(1000) DEFAULT NULL,
  "type" SMALLINT NOT NULL DEFAULT 6,
  "status" SMALLINT NOT NULL DEFAULT 1,
  "inverse" SMALLINT NOT NULL DEFAULT 0,
  "conf_name" VARCHAR(1000) DEFAULT '',
  "conf_field" VARCHAR(5000) DEFAULT '',
  "forward_id" BIGINT DEFAULT NULL,
  "time_type" SMALLINT NOT NULL DEFAULT 1,
  "start" TIMESTAMP(3) DEFAULT NULL,
  "end" TIMESTAMP(3) DEFAULT NULL,
  "debug" SMALLINT NOT NULL DEFAULT 1,
  "error_state" SMALLINT DEFAULT NULL,
  "create_at" TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_at" TIMESTAMP(3) NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "update_conf_index" ON "ice_conf" ("update_at");

-- ----------------------------
-- Table structure for ice_conf_update
-- ----------------------------
CREATE TABLE IF NOT EXISTS "ice_conf_update" (
  "id" BIGINT NOT NULL IDENTITY(1,1),
  "app" INT NOT NULL,
  "ice_id" BIGINT NOT NULL,
  "conf_id" BIGINT NOT NULL,
  "name" VARCHAR(50) DEFAULT NULL,
  "son_ids" VARCHAR(1000) DEFAULT NULL,
  "type" SMALLINT NOT NULL DEFAULT 6,
  "status" SMALLINT NOT NULL DEFAULT 1,
  "inverse" SMALLINT NOT NULL DEFAULT 0,
  "conf_name" VARCHAR(1000) DEFAULT '',
  "conf_field" VARCHAR(5000) DEFAULT '',
  "forward_id" BIGINT DEFAULT NULL,
  "time_type" SMALLINT NOT NULL DEFAULT 1,
  "start" TIMESTAMP(3) DEFAULT NULL,
  "end" TIMESTAMP(3) DEFAULT NULL,
  "debug" SMALLINT NOT NULL DEFAULT 1,
  "error_state" SMALLINT DEFAULT NULL,
  "create_at" TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  "update_at" TIMESTAMP(3) NOT NULL,
  PRIMARY KEY ("id")
);

CREATE INDEX "update_at_conf_index" ON "ice_conf_update" ("update_at");

-- ----------------------------
-- Table structure for ice_push_history
-- ----------------------------
CREATE TABLE IF NOT EXISTS "ice_push_history" (
  "id" BIGINT NOT NULL IDENTITY(1,1),
  "app" INT NOT NULL,
  "ice_id" BIGINT DEFAULT NULL,
  "reason" VARCHAR(500) DEFAULT NULL,
  "push_data" TEXT,
  "operator" VARCHAR(50) DEFAULT NULL,
  "create_at" TIMESTAMP(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY ("id")
);