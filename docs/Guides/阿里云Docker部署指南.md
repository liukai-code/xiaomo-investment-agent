# 阿里云 Docker 部署指南

域名：`mofin.cloud`

## 前置条件

- 阿里云 ECS 实例（推荐 2 核 4G 及以上）
- 操作系统：Alibaba Cloud Linux / CentOS 7+
- 已注册域名 `mofin.cloud`，并完成域名备案
- 已将项目代码推送到 Git 仓库
- Docker 已安装

---

## 一、域名解析

在阿里云控制台 → 域名 → 解析设置，添加以下记录：

| 记录类型 | 主机记录 | 记录值 | 说明 |
|---------|---------|--------|------|
| A | @ | REDACTED_SERVER_IP | mofin.cloud → 门户首页 |
| A | chat | REDACTED_SERVER_IP | chat.mofin.cloud → AI 会话应用 |
| A | www | REDACTED_SERVER_IP | www.mofin.cloud → 跳转门户首页 |

### 三级域名分配建议

参考 DeepSeek 的结构：

| 域名 | 用途 | 说明 |
|------|------|------|
| `mofin.cloud` | 门户首页 | 产品介绍、功能展示、注册引导等静态页面 |
| `chat.mofin.cloud` | AI 会话应用 | 用户日常使用的主应用（前端 + 后端一体） |
| `api.mofin.cloud` | API 接口 | 前后端分离时的后端 API 地址（预留） |

> 当前后端一体架构下，`chat.mofin.cloud` 同时承载前端页面和 API，无需分离。

---

## 二、服务器初始化

### 2.1 SSH 登录

```bash
ssh root@REDACTED_SERVER_IP
```

### 2.2 确认 Docker 环境

```bash
docker --version
docker compose version
```

如果 docker compose 命令不可用，安装插件：

```bash
yum install -y docker-compose-plugin
```

### 2.3 配置 Docker 镜像加速

```bash
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://你的加速地址.mirror.aliyuncs.com"]
}
EOF
systemctl daemon-reload
systemctl restart docker
```

> 加速地址在阿里云控制台「容器镜像服务 → 镜像工具 → 镜像加速器」获取。

---

## 三、部署应用

### 3.1 克隆代码

```bash
cd /opt
git clone https://github.com/你的用户名/你的仓库.git my-claude-code
cd my-claude-code
```

### 3.2 配置环境变量

```bash
cp .env.example .env
vi .env
```

填入实际值：

```env
DB_USER=postgres
DB_PASSWORD=你的PostgreSQL密码
DB_NAME=postgres          # 服务器上已有的数据库名，默认 postgres
REDIS_PASSWORD=           # 服务器上Redis若无密码则留空
ANTHROPIC_API_KEY=你的API密钥
ANTHROPIC_BASE_URL=https://api.anthropic.com
DASHSCOPE_MCP_URL=
CONFIG_ENCRYPTION_KEY=
ADMIN_PASSWORD=你的管理员密码
```

> 数据库和 Redis 使用服务器上已有的容器（postgres16 / redis7），不需要再创建新的。

### 3.3 构建并启动

```bash
docker compose up -d --build
```

首次构建需要 5-10 分钟。查看构建进度：

```bash
docker compose logs -f app
```

### 3.4 验证容器运行

```bash
docker compose ps
```

确认 app 容器显示 `Up` 状态。验证应用响应：

```bash
curl -s http://127.0.0.1:4545 | head -5
```

看到 HTML 输出说明启动成功。

---

## 四、Nginx 反向代理 + HTTPS

### 4.1 安装 Nginx

```bash
yum install -y nginx
systemctl enable nginx
```

### 4.2 配置 Nginx

```bash
vi /etc/nginx/conf.d/mofin.conf
```

```nginx
# AI 会话应用（主应用，前端 + 后端一体）
server {
    listen 80;
    server_name chat.mofin.cloud;

    location / {
        proxy_pass http://127.0.0.1:4545;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 流式传输（深度分析等长连接必须关闭缓冲）
        proxy_http_version 1.1;
        proxy_set_header Connection "";
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
    }
}

# 门户首页（静态页面）
server {
    listen 80;
    server_name mofin.cloud www.mofin.cloud;

    root /var/www/portal;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

测试并启动：

```bash
nginx -t
systemctl start nginx
```

验证：浏览器访问 `http://chat.mofin.cloud` 应能看到应用，`http://mofin.cloud` 显示门户页面。

### 4.3 创建门户目录（预留）

```bash
mkdir -p /var/www/portal
echo "<h1>mofin.cloud - Coming Soon</h1>" > /var/www/portal/index.html
```

后续部署门户页面时，将构建产物放到 `/var/www/portal/` 即可。

### 4.4 申请 HTTPS 证书

```bash
yum install -y certbot python3-certbot-nginx
```

申请证书（门户 + 会话应用）：

```bash
certbot --nginx -d mofin.cloud -d www.mofin.cloud -d chat.mofin.cloud
```

按提示操作：
1. 输入邮箱
2. 同意服务条款：`Y`
3. 是否分享邮箱：`N`

设置自动续期：

```bash
echo "0 3 * * * certbot renew --quiet && systemctl reload nginx" | crontab -
```

验证：浏览器访问 `https://chat.mofin.cloud` 和 `https://mofin.cloud`。

---

## 五、阿里云安全组

ECS 控制台 → 安全组 → 入方向规则：

| 协议 | 端口范围 | 源地址 | 说明 |
|------|---------|--------|------|
| TCP | 80 | 0.0.0.0/0 | HTTP |
| TCP | 443 | 0.0.0.0/0 | HTTPS |
| TCP | 22 | 你的本地IP/32 | SSH（限制来源 IP，不要用 0.0.0.0/0） |

> 不要开放 4545、5432、6379 到公网。这些端口仅 Docker 内部和本机 Nginx 使用。

---

## 六、常用运维命令

### 日常操作

```bash
docker compose logs -f app        # 查看应用日志
docker compose restart app        # 重启应用
docker compose down               # 停止所有服务
docker compose down -v            # 停止并删除数据卷（⚠️ 清除数据库）
```

### 更新部署

```bash
cd /opt/my-claude-code
git pull
docker compose up -d --build
```

### 数据库备份

```bash
# 备份（使用服务器上已有的 postgres16 容器）
docker exec postgres16 pg_dump -U postgres postgres > backup_$(date +%Y%m%d).sql

# 恢复
cat backup_20260718.sql | docker exec -i postgres16 psql -U postgres postgres
```

### Nginx 相关

```bash
systemctl reload nginx           # 重载配置
systemctl status nginx           # 查看状态
certbot renew --dry-run          # 测试证书续期
```

### 查看资源占用

```bash
docker stats --no-stream
```

---

## 七、常见问题

### 数据库连接超时

检查服务器上已有的 PostgreSQL 容器是否运行：

```bash
docker ps | grep postgres
```

确认 `.env` 中 `DB_PASSWORD` 与已有 PostgreSQL 容器的密码一致。

### 前端页面空白

```bash
docker compose logs app | grep "Started"
```

看到 `Started MyClaudeCodeApplication` 说明后端启动成功。清浏览器缓存后重试。

### Nginx 502 Bad Gateway

```bash
docker compose ps
curl -s http://127.0.0.1:4545 | head -1
```

检查容器是否运行、应用是否响应。

### 域名无法访问

1. `ping chat.mofin.cloud` — DNS 是否生效
2. 安全组是否开放 80/443
3. `systemctl status nginx` — Nginx 是否运行
4. 域名是否已备案

### 内存不足

docker-compose.yml 中 app 服务添加：

```yaml
environment:
  - JAVA_OPTS=-Xmx512m -Xms256m
```

Dockerfile ENTRYPOINT 改为：

```dockerfile
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
```
