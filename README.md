# PMS - 水质监测系统

前后端分离的水质监测数据采集与展示系统。

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Vue 3 + TypeScript + Vite + Element Plus + ECharts |
| 后端 | Spring Boot 3.2 + Java 17 + MyBatis |
| 数据库 | MySQL 8 |
| 部署 | Nginx + Spring Boot jar |

## 项目结构

```
pms/
├── backend/          # Spring Boot 后端
├── frontend/         # Vue 3 前端
├── deploy/           # 部署配置（nginx、启动脚本、SQL）
└── README.md
```

## 环境说明

| 环境 | Profile | 数据库 | 前端 | 后端 |
|------|---------|--------|------|------|
| 本地 | `local` | `pms_local` | Vite :5173 | :8080 |
| 生产 | `prod` | `pms_prod` | Nginx | jar :8080 |

## 快速开始

### 1. 配置环境文件（首次克隆必做）

敏感配置（数据库密码、视频流地址）不提交到 GitHub，使用模板文件：

```bash
cd backend/src/main/resources
cp application-local.yaml.example application-local.yaml
cp application-prod.yaml.example application-prod.yaml
```

然后编辑 `application-local.yaml` 和 `application-prod.yaml`，填入真实的数据库密码和视频流地址。

> 如果这两个文件曾经被提交过，需要先从 Git 追踪中移除：
> `git rm --cached backend/src/main/resources/application-local.yaml backend/src/main/resources/application-prod.yaml`

### 2. 初始化数据库

```bash
mysql -u root -p123456 < deploy/init.sql
```

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run
```

后端默认端口 `8080`，定时任务每 10 分钟自动采集一次数据。

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务器端口 `5173`，API 请求自动代理到后端。

### 5. 访问

打开浏览器访问 http://localhost:5173

## API 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/device/latest` | GET | 获取最新数据 |
| `/api/device/history` | GET | 历史数据分页查询 |
| `/api/device/trend` | GET | 趋势图数据（参数 hours） |
| `/api/video/stream-url` | GET | 获取监控视频流地址 |

## 敏感配置管理

| 文件 | 是否提交 Git | 说明 |
|------|-------------|------|
| `application-local.yaml.example` | ✅ 提交 | 本地配置模板 |
| `application-prod.yaml.example` | ✅ 提交 | 生产配置模板 |
| `application-local.yaml` | ❌ 不提交 | 本地真实配置（含视频地址、密码） |
| `application-prod.yaml` | ❌ 不提交 | 生产真实配置 |

生产环境打包前，确保本机存在已填好的 `application-prod.yaml`，配置会打入 jar 包。也可以在启动时覆盖：

```cmd
java -jar pms-backend-1.0.0.jar --spring.profiles.active=prod --video.stream-url=你的m3u8地址
```

## 生产部署（Windows + Nginx）

### 1. 构建

```bash
# 后端
cd backend && mvn clean package -DskipTests

# 前端
cd frontend && npm install && npm run build
```

### 2. 部署前端到 Nginx

1. 将 `frontend/dist/` 目录下所有文件复制到 `C:/nginx/html/pms/`
2. 编辑 `C:/nginx/conf/nginx.conf`，在 `http {}` 块中引入配置：

```nginx
include C:/nginx/conf/pms.conf;
```

3. 将 `deploy/nginx-windows.conf` 的内容保存为 `C:/nginx/conf/pms.conf`

### 3. 启动 Nginx

```cmd
cd C:\nginx
nginx.exe
```

重载配置：`nginx.exe -s reload`

停止：`nginx.exe -s stop`

### 4. 启动后端

```cmd
deploy\start.bat start
```

或手动启动：

```cmd
java -jar backend\target\pms-backend-1.0.0.jar --spring.profiles.active=prod
```

### 5. 访问

浏览器打开 http://localhost

## 配置说明

后端配置文件位于 `backend/src/main/resources/`：

- `application.yaml` — 公共配置（设备 API 地址、定时任务间隔、MyBatis）
- `application-local.yaml` — 本地环境（数据库、视频流地址，不提交 Git）
- `application-prod.yaml` — 生产环境（数据库、视频流地址，不提交 Git）

## 推送到 GitHub

```bash
cd /path/to/pms
git init
git add .
git commit -m "init: 水质监测系统 - 数据采集与展示"
git branch -M master
git remote add origin https://github.com/chenlong1212/pms.git
git push -u origin master
```
