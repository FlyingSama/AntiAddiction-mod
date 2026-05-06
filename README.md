# Minecraft 防沉迷合规系统

> 基于 Claude Code 多 Agent 协作开发的 Minecraft 未成年人防沉迷系统  
> 适配 **Minecraft 1.21.11** | **Fabric (Yarn)** | **NeoForge (Mojang)**  
> 后端：**Node.js + SQLite** | 部署于 **CentOS 9** | **authmc.fy1ng.cn**

---

## 功能概览

| 功能 | 说明 |
|------|------|
| 实名认证 | 游戏启动前强制弹出认证界面，输入姓名+身份证号验证身份 |
| 年龄判断 | 从身份证号提取出生日期，自动计算年龄，区分成年/未成年 |
| 防沉迷限制 | 未成年人仅可在 20:00-21:00 时段游玩（周五、六、日及节假日） |
| 游戏日管理 | Web 后台日历式管理，支持节假日配置、调休标记、差异化时段 |
| 实时同步 | Mod 端每 30 秒轮询后端，规则变更即时生效 |
| IP 归属地 | 登录日志自动获取 IP 地理位置（ipip.net + Pconline 双源容灾） |
| 30秒倒计时 | 到时自动保存并退出游戏 |
| 管理后台 | 仪表盘、游戏日日历、用户管理、登录日志、系统设置 |
| 演示模式 | `/demo` 无需登录的完整功能演示页面 |

---

## 项目结构

```
antiaddiction-mod/
├── fabric/                          # Fabric Mod (Yarn 映射)
│   └── src/main/java/com/antiaddiction/
│       ├── AntiAddictionMod.java
│       ├── AntiAddictionClient.java
│       ├── mixin/
│       │   └── MixinTitleScreen.java
│       ├── data/  (PlayerDataManager, DemoDatabase)
│       ├── screen/
│       │   ├── VerificationScreen.java      # 实名认证界面
│       │   ├── TimeRestrictionScreen.java   # 防沉迷限制界面
│       │   └── CalendarScreen.java          # 游戏日日历界面
│       ├── time/
│       │   ├── PlayTimeChecker.java         # 时间判断引擎
│       │   └── RuntimeChecker.java          # 运行时强制检查+倒计时
│       └── network/
│           ├── ApiClient.java               # 后端通信（fetch/sync/report）
│           └── LogCache.java                # 离线日志缓存
│
├── neoforge/                        # NeoForge Mod (Mojang 映射)
│   └── src/main/java/com/antiaddiction/
│       └── (同 Fabric 结构，Mojang 类名)
│
├── backend/                         # Node.js 后台服务
│   ├── server.js                    # Express API + 鉴权 + IP查询
│   ├── package.json
│   └── public/
│       ├── index.html               # 管理后台 SPA
│       ├── login.html               # 登录页
│       └── demo.html                # 演示面板（无需登录）
│
└── README.md
```

---

## 快速开始

### 构建 Mod

**前提条件：** JDK 21

```bash
# Fabric
cd fabric
./gradlew build
# 输出: build/libs/antiaddiction-fabric-1.0.0.jar

# NeoForge
cd neoforge
./gradlew build
# 输出: build/libs/antiaddiction-neoforge-1.0.0.jar
```

### 部署后端

```bash
cd backend
npm install
node server.js
# 访问 http://localhost:3000
# 默认管理员: admin / admin123
```

### 连接 Mod 与后端

在 `.minecraft/config/` 下创建 `antiaddiction.properties`：

```properties
backend_url=http://your-server:3000
```

---

## API 端点

### 公开 API（Mod 客户端调用）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/rules` | 获取游戏日规则 + 默认时段 `{default_start_hour, default_end_hour, days:[...]}` |
| GET | `/api/users` | 获取注册用户列表（实名认证用） |
| POST | `/api/report` | 上报会话事件 `{userName, minor, action}` |
| POST | `/api/report/batch` | 批量上报 |

### 管理 API（需登录）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/admin/login` | 管理员登录 |
| GET | `/api/admin/users` | 用户管理 |
| POST | `/api/admin/users` | 新增用户 |
| GET | `/api/admin/game-days/calendar` | 日历视图数据 |
| PUT | `/api/admin/game-days/:date` | 编辑某天规则 |
| PUT | `/api/admin/game-days/batch-time` | 批量设置默认时段 |
| GET | `/api/admin/logs` | 登录日志查询 |

---

## 技术要点

### 渲染稳定性

Minecraft 1.21.11 中 `drawTextWithShadow` 为 Yarn 桥接方法（b-flagged），调用会触发 `0xC0000005` 原生 JVM 崩溃。经过系统的二分搜索调试，定位根因并迁移至主 API `drawCenteredTextWithShadow`（a-flagged）。CalendarScreen 通过 `Class.forName` 反射延迟加载，避免类加载时的 JVM 验证崩溃。

### 调试历程

- 通过最小化 mod → 增量添加特性 → 逐个验证的二分搜索策略
- 排查路径：TextFieldWidget → drawTextWithShadow(崩溃) → drawText(无显示) → drawCenteredTextWithShadow(稳定)
- CalendarScreen 反射加载方案确保启动稳定
- 颜色值从 6 位升级至 ARGB 8 位格式解决文字不可见问题

### 默认规则引擎

采用 `default_config`（全局默认时段）+ `game_days`（每日例外数据）的 Plan B 架构。API 返回封装结构 `{default_start_hour, default_end_hour, days}`，Mod 端优先使用后端数据，无数据时回退至本地默认规则（周五六日）。

---

## 许可证

MIT License
