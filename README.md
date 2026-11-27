# 定向越野助手（Orienteering Assistant）

一个面向校园定向越野活动的完整课设项目：**Android 客户端 + Spring Boot 服务端**，支持扫码打卡、GPS 轨迹记录、积分赛/团队赛计分、赛事组织与成绩汇总。

> 校园定向越野课程设计项目

---

## 一、功能概览

### 运动员端
- **扫码打卡**：扫描检查点二维码完成打卡（二维码内容格式 `ORI|CP|检查点代码`），只识别本系统二维码
- **NFC 打卡**：预留 NFC 标签打卡能力（`NfcUtils`）
- **实时计时**：比赛中显示已用时 / 倒计时，超时自动结束
- **积分赛计分**：累计检查点分值，超过有效时间后按每分钟扣分
- **团队赛**：必达点（按顺序）、自由点（全队共享），按队伍汇总
- **GPS 轨迹**：前台服务持续采集轨迹，WGS84 转百度 BD09 坐标后在地图上绘制
- **本地离线**：SQLite 存赛事、检查点、成绩、打卡记录，断网也能比赛，联网后上传
- **断点续赛**：比赛未结束时记录保留，可继续

### 组织者端
- 创建赛事（名称、模式、日期、限时、检查点、起点/终点、预约时间）
- 生成 **6 位数字加入码**，运动员凭码自助参赛
- 成员管理：拉人、移除、分队
- 成绩汇总：个人成绩、全员成绩、团队赛况
- 成绩导出为文件（FileProvider 分享）

---

## 二、技术栈

| 层次 | 选型 |
| --- | --- |
| 客户端 | Android（Java）、minSdk 23 / targetSdk 35、AppCompat + Material |
| 扫码 | `com.journeyapps:zxing-android-embedded:4.3.0` |
| 地图 | 百度地图 SDK 9.0.0（轨迹、训练模式） |
| 本地存储 | SQLite（`SQLiteOpenHelper`，6 个版本迭代） |
| 网络 | `HttpURLConnection` + `org.json`，零第三方依赖，同步调用需放子线程 |
| 鉴权 | Bearer Token（32 字节随机串，7 天过期，服务端可作废） |
| 口令 | PBKDF2-HMAC-SHA256（12 万次迭代 + 随机盐，仅使用 JDK 内置算法） |
| 服务端 | Spring Boot 3.5 + Spring Data JPA + H2（文件库 `./ori-data`） |
| 构建 | Gradle，国内走阿里云镜像 + 百度地图官方 Maven 仓库 |

代码规模：**47 个 Java 文件，约 6997 行**。

---

## 三、目录结构

```
.
├── OrienteeringAssistant/                 # Android 客户端
│   └── app/src/main/
│       ├── java/com/example/orienteeringassistant/
│       │   ├── LoginActivity / RegisterActivity      # 登录注册
│       │   ├── MainActivity                          # 双 Tab 主页
│       │   ├── CompetitionListActivity / ...Edit      # 赛事列表、创建、编辑
│       │   ├── CheckpointListActivity / ...Edit       # 检查点管理、二维码生成
│       │   ├── RaceSelectActivity / RaceActivity      # 比赛选择、比赛中
│       │   ├── RaceResultActivity / RecordsActivity   # 成绩、历史记录
│       │   ├── TrainingActivity                       # 训练模式（地图 + 轨迹）
│       │   ├── JoinedListActivity / MemberManage...   # 云端：我的赛事、成员、全员成绩、团队赛况
│       │   ├── db/  DBHelper、SessionManager          # SQLite 与登录态
│       │   ├── model/  Competition、Checkpoint、RaceRecord、Checkin、TrackPoint
│       │   ├── service/ TrackService                  # 轨迹前台服务
│       │   ├── util/ ApiClient、QrUtils、NfcUtils、GeoUtils
│       │   └── widget/ SlideFinishView                # 滑动结束比赛控件
│       └── res/ layout、drawable、values、menu、xml
└── OrienteeringServer/                    # Spring Boot 服务端
    └── src/main/
        ├── java/com/ori/server/
        │   ├── OriServerApplication
        │   ├── entity/  User、Competition、Checkpoint、Enrollment、RaceRecord、CheckinLog、TrackPoint
        │   ├── repo/    7 个 JPA Repository
        │   └── web/     ApiController（全部 REST 接口）
        └── resources/application.yml
```

---

## 四、数据库设计

### 客户端 SQLite（`orienteering.db`，v6）

| 表 | 作用 | 关键字段 |
| --- | --- | --- |
| `users` | 本地账号 | username(唯一)、password、created_at |
| `competitions` | 赛事 | name、mode、status、time_limit、valid_minutes、penalty_per_min、join_key、cloud_id |
| `checkpoints` | 检查点 | competition_id、code、seq、points、type(1起点/2检查点/3终点)、required、lat、lng，`UNIQUE(competition_id, code)` |
| `race_records` | 成绩 | start_time、end_time、elapsed、score、valid、finished、uploaded、cloud_race_id |
| `checkins` | 打卡记录 | race_id、checkpoint_id、code、seq、时间戳、是否有效 |
| `track_points` | 轨迹点 | race_id、lat、lng、ts |

> 升级策略：`ALTER TABLE` 增量加字段，不清空历史数据。

### 服务端 H2

`users` / `competitions` / `checkpoints` / `enrollments` / `race_records` / `checkins` / `track_points`，与客户端模型一一对应，`ddl-auto: update` 自动建表。

---

## 五、接口一览（`/api`）

统一返回 `{ok: true/false, msg, ...}`，列表返回 `{ok: true, data: [...]}`。

**除 `/register`、`/login` 外，所有接口必须带 `Authorization: Bearer <token>`**；身份一律以令牌为准，请求体里的 `userId` / `ownerId` 不再被信任。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/register` | 注册 |
| POST | `/login` | 登录（返回 token 与过期时间） |
| POST | `/logout` | 登出（作废当前令牌） |
| GET | `/me` | 当前登录用户资料（校验令牌） |
| GET / POST | `/users/{id}` | 查询 / 修改资料 |
| POST | `/competitions` | 创建赛事（含检查点、初始成员） |
| POST | `/competitions/join` | 凭加入码参赛 |
| POST | `/competitions/batch` | 批量创建 |
| POST / DELETE | `/competitions/{id}` | 更新 / 删除赛事 |
| GET | `/competitions/owned`、`/joined`、`/{id}` | 我发起的 / 我参加的 / 赛事详情 |
| POST / DELETE / GET | `/competitions/{id}/members` | 成员增删查 |
| POST / GET | `/competitions/{id}/teams` | 分队、队伍列表 |
| POST | `/results` | 上传成绩（含打卡记录、轨迹） |
| GET | `/competitions/{id}/results`、`/results/{raceId}/checkins`、`/results/{raceId}/track`、`/users/{userId}/results` | 成绩查询 |

---

## 六、运行说明

### 服务端
```bash
cd OrienteeringServer
./gradlew bootRun            # 或 ./gradlew build && java -jar build/libs/*.jar
```
默认端口 **8090**，数据库文件生成在启动目录 `ori-data.mv.db`。

数据库口令通过环境变量注入，代码里不写死：
```bash
ORI_DB_PASSWORD=你的口令 ./gradlew bootRun
```
已有库改口令需先执行 `ALTER USER sa SET PASSWORD '...'`，或删除旧库重新初始化。

### 客户端
1. Android Studio 打开 `OrienteeringAssistant/`
2. 在 `AndroidManifest.xml` 中把 `YOUR_BAIDU_MAP_AK` 换成自己的百度地图 AK
3. 服务端地址：默认 `http://192.168.1.100:8090`，可在登录页修改（保存在 SharedPreferences）
4. 真机运行，授予相机 / 定位 / 通知权限

> 服务端为 HTTP 明文，客户端已开启 `usesCleartextTraffic`，仅用于内网/演示环境。

---

## 七、设计要点

- **端侧优先**：扫码解码、计分、轨迹全部在手机本地完成，服务端只接收结构化数据，弱网/无网可用，服务器压力小
- **坐标转换**：`GeoUtils` 实现 WGS84 → GCJ02 → BD09 两步转换，保证 GPS 点与百度地图对齐
- **前台服务**：`TrackService` 以 `foregroundServiceType=location` 常驻，防止比赛中切屏被系统回收
- **二维码防伪**：统一前缀 `ORI|CP|`，其他二维码直接判为无效
- **成绩只增不删**：历史参赛记录不可删，符合赛事留档需求

## 八、安全设计

| 风险项 | 处理方式 |
| --- | --- |
| 口令明文存储 | 服务端只存 PBKDF2-HMAC-SHA256 哈希（12 万次迭代 + 16 字节随机盐），校验用恒定时间比较 |
| 口令明文传输后落库 | 历史明文口令在首次登录校验通过后自动改写为哈希，无感迁移 |
| 接口无鉴权 | 全局拦截器强制 Bearer Token；令牌 32 字节随机、7 天过期、单端登录、可主动作废 |
| 客户端本地留口令 | 本地 `users` 表已删除（v7 升级时 `DROP TABLE`），手机端只保存令牌，不保存密码 |
| 越权改数据（IDOR） | 身份取自令牌：改资料仅本人、改/删赛事仅发起者、读详情要求已报名 |
| 暴力破解 | 同一用户名 10 分钟失败 5 次锁定 10 分钟 |
| 超长输入 / 数据膨胀 | 用户名、姓名、赛事名、描述、检查点、成员、打卡、轨迹点全部限长限量 |
| 非法坐标与数值 | 经纬度超界的轨迹点与检查点直接丢弃，分值/时长/积分做范围钳制 |
| 敏感配置外泄 | 百度地图 AK 用占位符，服务器地址用占位符，数据库口令走环境变量 |
| 备份拖库 | `allowBackup=false`，关闭 adb 备份通道 |
| 报错泄露内部信息 | 关闭堆栈与异常消息回显 |
| H2 控制台 | 显式关闭 |

## 九、仍需注意

- 通信仍为 HTTP 明文（校园内网演示所需），正式部署建议套 Nginx + HTTPS
- H2 文件库适合课设与小型赛事，生产建议换 MySQL 并定时备份
- 令牌存在 SharedPreferences，未做 Android Keystore 加密（课设范围内可接受）
- ZXing 逆光/远距离识别率有限，可升级 WeChatQRCode

---
