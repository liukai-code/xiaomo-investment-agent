-- V2__create_user_config_table.sql
CREATE TABLE user_config (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  api_key_encrypted VARCHAR(512),
  base_url VARCHAR(256),
  model_name VARCHAR(128),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_config_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_config_user_id ON user_config(user_id);

COMMENT ON TABLE user_config IS '用户大模型API配置表';
COMMENT ON COLUMN user_config.api_key_encrypted IS '加密后的API Key';
COMMENT ON COLUMN user_config.base_url IS 'API基础URL';
COMMENT ON COLUMN user_config.model_name IS '模型名称';
