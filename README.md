# PMS - 池塘生产管理系统

前后端分离的智慧鱼塘系统：水质监测、生物量趋势、投喂记录、监控视频，以及 LLM 运维助手。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts + hls.js |
| 后端 | Spring Boot 3.2 + Java 17 + MyBatis + Flyway |
| 数据库 | MySQL 8 |
| 部署 | Windows + Nginx + NSSM + Spring Boot jar |

## 架构

### 本地开发

```
浏览器 → Vite :5173/pms/ → 代理 /api/ → Spring Boot :8080 → MySQL pms_local
```

### 生产环境

```
浏览器
  ↓
Nginx
  ├── /pms/  → 静态前端（C:/nginx/html/pms/）
  └── /api/  → 反向代理 → NSSM 托管的 jar :8080 → MySQL pms_prod
```

| 环境 | Profile | 数据库 | 前端 | 后端 |
|------|---------|--------|------|------|
| 本地 | `local`（默认） | `pms_local` | Vite `:5173/pms/` | `:8080` |
| 生产 | `prod` | `pms_prod` | Nginx `/pms/` | NSSM → `:8080` |

## 项目结构

```
pms/
├── backend/                              # Spring Boot 后端
│   └── src/main/
│       ├── java/com/pms/
│       │   ├── controller/               # device / biomass / feeding / video / chat
│       │   ├── service/
│       │   ├── llm/                      # DeepSeek 工具调用助手
│       │   ├── scheduler/                # 水质定时采集
│       │   └── ...
│       └── resources/
│           ├── application.yaml          # 公共配置（设备、视频、LLM、Flyway）
│           ├── application-local.yaml    # 本地库连接
│           ├── application-prod.yaml     # 生产库连接
│           ├── db/migration/             # Flyway 迁移 V1–V4
│           ├── mapper/                   # MyBatis XML
│           └── prompts/                  # LLM 系统提示词
├── frontend/                             # Vue 3 前端（base: /pms/）
│   └── src/
│       ├── api/
│       ├── components/                   # 趋势图、投喂、视频、聊天等
│       └── App.vue
└── README.md
```

---

## 一、本地开发

### 1. 准备数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms_local DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

表结构由 Flyway 在后端首次启动时自动创建，无需手跑建表脚本。

本地配置见 `backend/src/main/resources/application-local.yaml`（库名、账号密码）。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- 默认 `spring.profiles.active=local`
- 端口 `8080`
- 定时任务默认每 10 分钟采集一次设备数据

### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问：http://localhost:5173/pms/

---

## 二、数据库迁移（Flyway）

迁移目录：`backend/src/main/resources/db/migration/`

| 脚本 | 说明 |
|------|------|
| `V1__init_device_data_record.sql` | 水质设备数据表 |
| `V2__init_pond_and_biomass_record.sql` | 池塘 + 生物量 |
| `V3__init_feeding_record.sql` | 投喂记录 |
| `V4__merge_biomass_and_refactor_feeding.sql` | 生物量合并与投喂重构 |

规则：

- 命名：`V{序号}__{描述}.sql`，只写增量 DDL，不写 `CREATE DATABASE`
- 已执行版本记在 `flyway_schema_history`，不会重复跑
- 本地 / 生产共用同一套脚本；重启后端即可生效

新增变更时加下一个序号文件，提交后本地重启或生产重启 jar。

---

## 三、生产部署

### 首次准备（服务器一次性）

1. 建库：

```cmd
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS pms_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

2. 配置 Nginx：`/pms/` 指向 `C:\nginx\html\pms\`，`/api/` 反代到 `127.0.0.1:8080`
3. 用 NSSM 注册服务 `PMS_BACKEND`，工作目录 `C:\Projects\pms\backend`，参数大致为：

```text
-jar pms-backend-1.0.0.jar --spring.profiles.active=prod
```

生产库连接在 `application-prod.yaml`，打进 jar 后随包发布。

### 日常发版

```bash
# 开发机
cd frontend && npm run build
cd ../backend && mvn clean package -DskipTests

# 将 frontend/dist/* 覆盖到 C:\nginx\html\pms\
# 将 backend/target/pms-backend-1.0.0.jar 覆盖到 C:\Projects\pms\backend\

# 服务器
cd C:\nginx && nginx.exe -s reload
nssm restart PMS_BACKEND
```

访问示例：`http://<服务器>/pms/`

### 验证

| 地址 | 说明 |
|------|------|
| `/pms/` | 前端页面 |
| `/api/device/latest` | 最新水质（经 Nginx） |

迁移历史：

```cmd
mysql -u root -p -e "USE pms_prod; SELECT * FROM flyway_schema_history;"
```

---

## 四、配置说明

| 文件 | 用途 |
|------|------|
| `application.yaml` | 公共：设备 API、采集间隔、视频流、LLM、端口、Flyway |
| `application-local.yaml` | 本地数据源 `pms_local` |
| `application-prod.yaml` | 生产数据源 `pms_prod` |

常用公共项（在 `application.yaml`）：

```yaml
device:
  api:
    base-url: http://...
    device-id: "..."
  schedule:
    enabled: true
    fixed-rate-ms: 600000    # 10 分钟

video:
  stream-url: https://...m3u8?...

llm:
  base-url: https://api.deepseek.com
  model: deepseek-chat

spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
```

LLM 密钥不写入受 Git 管理的配置。首次运行时复制模板并填写真实密钥：

```bash
cd backend
cp src/main/resources/application-secrets.example.yaml src/main/resources/application-secrets.yaml
```

`src/main/resources/application-secrets.yaml` 已加入 `.gitignore`，应用启动时会自动从 classpath 加载。构建生产包前，需要在构建环境中准备该文件。

---

## 五、主要 API

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/device/latest` | GET | 最新水质 |
| `/api/device/history` | GET | 历史分页（`page`, `size`, `startTime`, `endTime`） |
| `/api/device/trend` | GET | 水质趋势（`hours`，默认 24） |
| `/api/biomass/ponds` | GET | 池塘列表 |
| `/api/biomass/trend` | GET | 生物量趋势 |
| `/api/feeding/ponds` | GET | 投喂相关池塘 |
| `/api/feeding/records` | GET/POST | 投喂记录列表 / 新增 |
| `/api/feeding/records/{id}` | PUT/DELETE | 更新 / 删除 |
| `/api/feeding/strategy` | GET | 投喂策略 |
| `/api/video/stream-url` | GET | 监控流地址 |
| `/api/chat` | POST | LLM 运维助手 |

---

## 六、Git

```bash
git add .
git commit -m "your message"
git push origin develop
```

仓库：https://github.com/chenlong1212/pms.git
