# 公共配置目录

| 子目录/文件 | 说明 |
|-------------|------|
| `docker/` | MySQL、Redis、RabbitMQ（`docker compose up -d`） |
| `env/.env.example` | 环境变量模板（含联调服务器注释） |
| `env/application-dev-local.yml.example` | 本地 IDEA 数据库连接 |
| `env/application-dev-server.yml.example` | ECS 联调（`39.101.67.167`，127.0.0.1 连 Docker） |
| `nginx/nginx.conf.example` | 演示服务器 Nginx（80 → 前端静态 + /api 反代） |

## 环境对照

| 环境 | 前端 | 后端 |
|------|------|------|
| 本地 | localhost:8000 | localhost:8080/api/v1 |
| 联调服务器 | 39.101.67.167 | 39.101.67.167:8080/api/v1 |

```powershell
docker compose -f config/docker/docker-compose.yml up -d
```
