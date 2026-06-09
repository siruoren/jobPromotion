# Job Promotion Plugin

Jenkins 任务晋级插件 - 从源 Jenkins 实例晋级（同步）任务到当前 Jenkins。

> **版本**：`1.0.1-SNAPSHOT`（从 POM 元数据自动读取，可通过 `/job-promotion/doGetVersion` API 查询）

## 功能特性

- **多源 Jenkins 实例配置**：在 Manage Jenkins 中配置多个源 Jenkins 实例（名称、地址、凭据），每个实例支持连接测试按钮验证认证
- **凭据选择**：系统凭据下拉框支持直接新建凭据
- **API Token 认证**：使用用户名 + API Token（Secret Text 凭据）认证，凭据选择器支持直接新建凭据
- **实例选择**：晋级页面支持选择指定的源 Jenkins 实例进行晋级
- **任务晋级入口**：根目录和文件夹（Folder）左侧任务栏添加"Job Promotion"入口
- **权限控制**：
  - 目录页面：需要 CREATE 和 CONFIGURE 权限才显示菜单
  - 根目录页面：需要 ADMINISTER 权限才显示菜单
- **目录匹配**：目录页面自动使用当前目录路径，只显示源 Jenkins 对应目录下的任务
- **远程任务浏览**：输入目录路径加载源 Jenkins 对应目录下的任务列表，支持多层级嵌套
- **勾选晋级**：通过勾选要晋级的任务及目录任务下的所有子任务
- **幂等同步**：按源任务的 fullPath 幂等同步到当前目录任务或根目录下
- **晋级模式**：
  - 普通更新：跳过已存在的任务
  - 强制更新：覆盖已存在的任务配置
- **自动清理**：晋级后的任务自动清理乱码字符、清理定时策略（triggers）、清理代码监控策略
- **自动禁用**：晋级后的任务自动禁用
- **确认弹窗**：点击晋级按钮弹窗显示要晋级的任务（使用 fullPath），确认后执行
- **结果展示**：晋级结果弹窗显示每个任务的晋级状态（成功/失败/跳过）
- **审计日志**：根目录下记录所有晋级操作，支持分页浏览（每页20条），可设置日志保留天数
- **中英文国际化**：支持中文和英文界面，Jelly 和 JavaScript 层面都支持国际化
- **防御性 JSON 解析**：前端采用防御性 JSON 解析策略，确保后端返回非 JSON 内容也能优雅处理
- **线程池管理**：
  - 避免线程无限增长导致的内存泄漏
  - 防止多个用户同时操作时的线程阻塞
  - 统一管理并发任务，提高系统稳定性
  - 两个 Action 类共享同一个线程池实例
  - 通过 `submitWithAuth()` 方法确保安全上下文正确传播
- **菜单图标**：使用 Jenkins 核心内置下载图标（symbol-download），无需额外图标插件

## 系统要求

- Jenkins >= 2.479.2
- Java >= 17

## 构建与安装

```bash
# 构建
mvn clean package -DskipTests

# 生成的 HPI 文件在 target/job-promotion-1.0.0-SNAPSHOT.hpi
# 在 Jenkins -> Manage Plugins -> Advanced -> Upload Plugin 安装
```

## 配置步骤

1. **配置凭据**：Manage Jenkins → Credentials → 添加 "Secret text" 类型凭据，存储源 Jenkins 的 API Token（Jenkins > 用户 > 配置 > API Token）
2. **配置源 Jenkins 实例**：Manage Jenkins → System → Job Promotion → 添加源 Jenkins 实例（名称、URL、用户名、API Token 凭据）→ 点击 Test Connection 验证
3. **使用晋级**：
   - 在根目录或文件夹页面左侧点击"Job Promotion"
   - 选择源 Jenkins 实例
   - 目录页面：自动使用当前目录路径，直接点击"Load Jobs"加载远程任务列表
   - 根目录页面：输入源 Jenkins 上的目录路径（留空表示根目录），点击"Load Jobs"加载
   - 勾选要晋级的任务
   - 选择晋级模式（普通/强制）
   - 点击"Promote"→ 确认 → 查看结果
4. **审计日志**：根目录晋级页面点击"Audit Log"查看晋级操作记录，可设置日志保留天数

## 技术架构

```
com.siruoren.jobpromotion
├── engine/                           # 核心引擎层
│   └── PromotionEngine               # 晋级逻辑引擎（文件夹同步、任务晋级）
├── service/                          # 业务服务层
│   └── DeliveryService               # 交付业务逻辑（交付、撤销、回调）
├── util/                             # 工具层
│   ├── JsonResponseUtil              # JSON 响应构建（合并原 JsonResponse/JsonResponseerror）
│   ├── XmlUtil                       # XML 清理（XXE 防护、触发器清理）
│   ├── PathUtil                      # 路径工具（路径遍历防护、路径解析）
│   └── VersionUtil                   # 版本读取（从 POM 元数据）
├── JobPromotionGlobalConfig          # 全局配置（GlobalConfiguration）
├── SourceJenkinsInstance             # 源 Jenkins 实例配置（Describable）
├── JenkinsRemoteClient               # 远程 Jenkins HTTP 客户端
├── PromotionService                  # 晋级服务门面（委托给 PromotionEngine）
├── PromotionThreadPool               # 共享线程池 + 安全上下文传播
├── PromotionResult                   # 晋级结果模型
├── RemoteJobInfo                     # 远程任务信息模型
├── DeliveryItem                      # 交付项模型
├── DeliveryStore                     # 交付项持久化存储
├── RootPromotionAction               # 根目录 Action（RootAction）
├── FolderPromotionAction             # 文件夹 Action
├── FolderPromotionActionFactory      # Folder Action 工厂（TransientActionFactory）
├── AuditLogEntry                     # 审计日志条目模型
└── AuditLogService                   # 审计日志服务（持久化 + 保留策略）
```

### 安全特性

- **XXE 防护**：XML 解析禁用外部实体和 DTD 加载
- **路径遍历防护**：拒绝 `..`、null 字节、绝对路径等恶意路径输入
- **线程安全**：修复 `SimpleDateFormat` 静态实例线程安全问题
- **超时保护**：晋级任务 5 分钟超时限制，防止线程阻塞
- **权限控制**：所有 API 端点均需权限验证（`@RequirePOST` + 权限检查）

## License

MIT License


## Star History

[![Star History Chart](https://api.star-history.com/chart?repos=siruoren/jobPromotion&type=date&legend=top-left)](https://www.star-history.com/?repos=siruoren%2FjobPromotion&type=date&legend=top-left)
