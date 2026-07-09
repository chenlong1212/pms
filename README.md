# PMS - 智慧鱼塘系统

前后端分离的水质监测数据采集与展示系统，支持实时数据展示、历史趋势、监控视频播放。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts + hls.js |
| 后端 | Spring Boot 3.2 + Java 17 + MyBatis + Flyway |
| 数据库 | MySQL 8 |
| 部署 | Windows + Nginx + NSSM + Spring Boot jar |

## 架构说明

### 本地开发

```
浏览器 → Vite :5173/pms/  →  代理 /api/  →  Spring Boot :8080  →  MySQL pms_local
```

### 生产环境

```
浏览器
  ↓
Nginx :8001
  ├── /pms/   →  静态前端（C:/nginx/html/pms/）
  └── /api/   →  反向代理  →  NSSM 托管的 jar :8080  →  MySQL pms_prod
```

| 环境 | Profile | 数据库 | 前端 | 后端 |
|------|---------|--------|------|------|
| 本地 | `local` | `pms_local` | Vite `:5173/pms/` | `:8080` |
| 生产 | `prod` | `pms_prod` | Nginx `:8001/pms/` | NSSM → `:8080` |

## 项目结构

```
pms/
├── backend/                         # Spring Boot 后端
│   └── src/main/resources/
│       ├── application.yaml         # 公共配置
│       ├── application-local.yaml   # 本地配置（不提交 Git）
│       ├── application-prod.yaml    # 生产配置（不提交 Git）
│       ├── application-local.yaml.example
│       ├── application-prod.yaml.example
│       └── db/migration/            # Flyway 数据库迁移脚本
│           ├── V1__init_device_data_record.sql
│           └── V2__xxx.sql          # 新增表/字段
├── frontend/                        # Vue 3 前端
├── deploy/
│   ├── init.sql                     # 本地数据库初始化（保留供参考）
│   ├── init-prod.sql                # 生产数据库初始化（保留供参考）
│   ├── nginx-windows.conf           # Windows Nginx 配置参考
│   ├── nssm-install.bat             # 注册后端 Windows 服务
│   └── nssm-uninstall.bat           # 卸载后端 Windows 服务
└── README.md
```

---

## 一、本地开发

### 1. 克隆项目并配置环境文件

敏感配置（数据库密码、视频流地址）不提交到 GitHub，首次克隆后从模板创建：

```bash
cd backend/src/main/resources
cp application-local.yaml.example application-local.yaml
cp application-prod.yaml.example application-prod.yaml
```

编辑 `application-local.yaml`，填入本地数据库密码和视频流地址：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pms_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: "123456"

video:
  stream-url: https://你的视频流地址.m3u8?proto=https&source=open
```

### 2. 初始化本地数据库

```bash
mysql -u root -p < deploy/init.sql
```

会创建 `pms_local` 和 `pms_prod` 两个库及表结构。本地开发使用 `pms_local`。

> **注意：** 首次启动后端时 Flyway 会自动对数据库执行基线（baseline）和迁移脚本，无需手动建表。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- 端口：`8080`
- 定时任务每 10 分钟自动采集一次设备数据
- Flyway 会在启动时自动执行数据库迁移

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

- 端口：`5173`
- API 请求自动代理到 `localhost:8080`

### 5. 访问

打开浏览器：http://localhost:5173/pms/

---

## 二、数据库迁移（Flyway）

项目使用 [Flyway](https://flywaydb.org/) 管理数据库结构变更，确保本地和生产环境表结构一致。

### 工作原理

```
db/migration/
├── V1__init_device_data_record.sql    ← 初始建表
├── V2__xxx.sql                        ← 新增表或字段
└── V3__yyy.sql                        ← 更多变更
```

- **增量脚本**：每个文件只描述变更（CREATE TABLE、ALTER TABLE），不写 CREATE DATABASE
- **版本管理**：`V{序号}__{描述}.sql` 命名规则，Flyway 自动按顺序执行
- **幂等性**：Flyway 通过 `flyway_schema_history` 表跟踪已执行的脚本，不会重复执行
- **本地生产一致**：同一套脚本在本地和生产环境自动执行，表结构始终保持同步

### 新增表或字段

1. 在 `backend/src/main/resources/db/migration/` 下创建新文件
2. 文件名格式：`V{下一个序号}__{描述}.sql`
3. 编写增量 SQL（不要写 CREATE DATABASE）
4. 提交到 Git，本地重启后端或生产重启 jar 即可自动生效

示例：

```sql
-- backend/src/main/resources/db/migration/V2__add_alert_config.sql
CREATE TABLE IF NOT EXISTS alert_config (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    device_id   VARCHAR(32)  NOT NULL,
    dox_min     DECIMAL(10,2),
    dox_max     DECIMAL(10,2),
    enabled     TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 已有数据库迁移

如果数据库已有数据但未使用 Flyway，启动时会自动执行基线（baseline），将现有表结构标记为已迁移，后续脚本从下一个版本号开始。

---

## 三、生产部署

### 步骤 1：初始化生产数据库

新建服务器上的 `pms_prod` 数据库（只需执行一次）：

```cmd
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

> **不需要执行 `init-prod.sql`**，Flyway 会在后端首次启动时自动创建所有表结构。

验证数据库已创建：

```cmd
mysql -u root -p -e "USE pms_prod; SHOW TABLES;"
```

首次启动后 Flyway 会自动建表，再次执行即可看到 `device_data_record` 等表。

---

### 步骤 2：配置生产环境并打包后端

在**开发机**上编辑 `application-prod.yaml`（打包前必须存在）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pms_prod?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: "生产环境密码"

logging:
  level:
    com.pms: INFO
  file:
    name: logs/pms.log

video:
  stream-url: https://你的视频流地址.m3u8?proto=https&source=open
```

打包：

```bash
cd backend
mvn clean package -DskipTests
```

产物：`backend/target/pms-backend-1.0.0.jar`

---

### 步骤 3：构建前端

```bash
cd frontend
npm install
npm run build
```

产物：`frontend/dist/` 目录（已配置 `base: '/pms/'`，适配 Nginx 子路径）

---

### 步骤 4：上传文件到服务器

**后端 jar：**

```
backend/target/pms-backend-1.0.0.jar  →  C:\pms\pms-backend-1.0.0.jar
```

服务器目录结构：

```
C:\Projects\pms\
└── pms-backend-1.0.0.jar
```

**前端静态文件：**

```
frontend/dist/ 下所有文件  →  C:\nginx\html\pms\
```

服务器目录结构：

```
C:\nginx\html\pms\
├── index.html
└── assets\
    └── ...
```

---

### 步骤 5：NSSM 托管后端

```bat
nssm install PMS_BACKEND C:\Java\jdk-17.0.12\bin\java.exe
nssm set PMS_BACKEND AppDirectory C:\Projects\pms\backend
nssm set PMS_BACKEND AppParameters "-jar pms-backend-1.0.0.jar --spring.profiles.active=prod"
```

```cmd
nssm set PMS_BACKEND AppStdout C:\Projects\pms\backend\logs\stdout.log
nssm set PMS_BACKEND AppStderr C:\Projects\pms\backend\logs\stderr.log
nssm set PMS_BACKEND AppRotateFiles 1
```

```cmd
nssm start PMS_BACKEND
nssm status PMS_BACKEND
```

**常用维护命令：**

| 操作 | 命令 |
|------|------|
| 查看状态 | `nssm status PMS_BACKEND` |
| 重启服务 | `nssm restart PMS_BACKEND` |
| 停止服务 | `nssm stop PMS_BACKEND` |
| 卸载服务 | `deploy\nssm-uninstall.bat` |

---

### 步骤 6：配置 Nginx

将 `deploy/nginx-windows.conf` 的内容作为 `C:\nginx\conf\nginx.conf`（或合并到现有配置）：

```nginx
worker_processes  1;

events {
    worker_connections  1024;
}

http {
    include       mime.types;
    default_type  application/octet-stream;

    sendfile        on;
    keepalive_timeout  65;

    server {
        listen       8001;
        server_name  146.56.204.72;

        # API 反向代理到 NSSM 托管的后端
        location /api/ {
            proxy_pass http://127.0.0.1:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # 前端静态文件
        location = /pms {
            return 301 /pms/;
        }

        location /pms/ {
            alias html/pms/;
            index index.html;
            try_files $uri $uri/ /pms/index.html;
        }
    }
}
```

---

### 步骤 7：启动 Nginx

```cmd
cd C:\nginx
nginx.exe
```

| 操作 | 命令 |
|------|------|
| 重载配置 | `nginx.exe -s reload` |
| 停止 | `nginx.exe -s stop` |

---

### 步骤 8：访问验证

| 地址 | 说明 |
|------|------|
| http://146.56.204.72:8001/pms/ | 前端页面 |
| http://146.56.204.72:8001/api/device/latest | API（经 Nginx 转发） |

---

## 四、日常更新维护

### 更新后端

```bash
# 开发机打包
cd backend && mvn clean package -DskipTests

# 上传 jar 覆盖 C:\pms\pms-backend-1.0.0.jar

# 服务器重启服务
nssm restart PMS_BACKEND
```

### 更新前端

```bash
# 开发机构建
cd frontend && npm run build

# 上传 dist/ 内容覆盖 C:\nginx\html\pms\

# 服务器重载 Nginx
cd C:\nginx && nginx.exe -s reload
```

### 数据库变更（新增表/字段）

```bash
# 1. 开发机创建迁移脚本
# backend/src/main/resources/db/migration/V3__xxx.sql

# 2. 提交到 Git
git add backend/src/main/resources/db/migration/
git commit -m "add xxx table"
git push origin develop

# 3. 服务器重启后端，Flyway 自动执行迁移
nssm restart PmsBackend

# 4. 验证迁移结果
mysql -u root -p -e "USE pms_prod; SELECT * FROM flyway_schema_history;"
```

---

## 五、敏感配置管理

| 文件 | 提交 Git | 说明 |
|------|----------|------|
| `application-local.yaml.example` | ✅ | 本地配置模板 |
| `application-prod.yaml.example` | ✅ | 生产配置模板 |
| `application-local.yaml` | ❌ | 本地真实配置 |
| `application-prod.yaml` | ❌ | 生产真实配置 |

`.gitignore` 已排除真实配置文件，只上传 `.example` 模板。

---

## 六、配置文件说明

| 文件 | 用途 |
|------|------|
| `application.yaml` | 公共配置（设备 API、定时任务间隔、MyBatis、Flyway） |
| `application-local.yaml` | 本地数据库、视频流地址 |
| `application-prod.yaml` | 生产数据库、视频流地址 |

关键配置项：

```yaml
# 设备数据采集
device:
  api:
    base-url: http://121.40.165.95:8556
    device-id: "11202305124"
  schedule:
    enabled: true
    fixed-rate-ms: 600000    # 10 分钟

# 监控视频流
video:
  stream-url: https://...m3u8?proto=https&source=open

# 数据库迁移（Flyway）
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true   # 已有数据库自动基线
    baseline-version: 0
```

---

## 七、API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/device/latest` | GET | 获取最新水质数据 |
| `/api/device/history` | GET | 历史数据分页（参数 page, size, startTime, endTime） |
| `/api/device/trend` | GET | 趋势图数据（参数 hours，默认 24） |
| `/api/video/stream-url` | GET | 获取监控视频流地址 |

---

## 八、推送到 GitHub

```bash
git add .
git commit -m "your message"
git push origin develop
```

仓库地址：https://github.com/chenlong1212/pms.git
