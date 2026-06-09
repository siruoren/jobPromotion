# Changelog

## 1.0.1

### Features

- 交付任务过期机制：交付后 30 天未被晋级的任务自动标记为"已过期"（EXPIRED），需重新交付
- 支持重复交付：已交付（DELIVERED）或已过期（EXPIRED）的任务可重新交付，更新交付时间和交付人
- 晋级菜单交付列表显示交付时间列
- 远程 Jenkins 加载交付列表时只加载当前目录下的交付项（非递归），避免加载过多数据导致卡顿
- 本地任务列表加载支持递归加载所有子目录任务（根目录加载 Jenkins 所有任务）

### Bug Fixes

- 修复晋级结果重复显示两次的问题（文件夹同步结果和条目结果使用相同 key 导致重复）
- 修复目录下加载任务卡死问题（移除递归加载子目录，改为按需加载当前层级）
- 修复 Folder 页面 JS 报错 `Cannot read properties of null (reading 'value')`（缺少 `folderPathInput` 隐藏元素）
- 修复本地任务列表加载重复条目问题（`getAllItems()` 返回嵌套项导致重复）
- 修复"暂无交付项"文本框未覆盖到交付时间列的问题

### Architecture

- 重构代码架构为 engine/service/util 分层结构
  - **engine 层**：`PromotionEngine` - 核心晋级逻辑引擎
  - **service 层**：`DeliveryService` - 交付业务逻辑，消除 Action 类间的代码重复
  - **util 层**：`JsonResponseUtil`（合并 JsonResponse/JsonResponseerror）、`XmlUtil`（XML 清理）、`PathUtil`（路径工具）、`VersionUtil`（版本读取）
- `PromotionService` 精简为门面类，委托给 `PromotionEngine`
- `FolderPromotionAction` 和 `RootPromotionAction` 委托给 service/engine 层

### Security

- 修复 `SimpleDateFormat` 静态实例线程安全问题（`DeliveryItem`、`AuditLogEntry`），防止多线程数据错乱
- 添加路径遍历防护（`PathUtil.sanitizePath`/`sanitizeJobName`），拒绝 `..`、null 字节、绝对路径等恶意输入
- XML 解析已有 XXE 防护（`XmlUtil.createSecureDocumentBuilderFactory` 禁用外部实体）
- `PromotionEngine` 和 `DeliveryService` 中对用户输入的路径参数进行校验

### Stability

- 修复 `future.get()` 无超时导致的线程阻塞问题，添加 5 分钟超时限制
- 修复 `DeliveryStore` 和 `AuditLogService` 中的双重锁问题（`synchronized` 方法 + `synchronized` 块）
- 统一使用 `synchronized(items)` 块替代 `synchronized` 方法修饰符，避免锁粒度过大
- 修复 `saveToDisk` 中在持锁状态下执行 IO 操作的问题，改为先复制数据再释放锁后写文件

### Features

- 添加版本 API 端点（`doGetVersion`），从 POM 元数据读取插件版本号
- 添加 `VersionUtil` 工具类，支持从 `META-INF/maven` 读取版本

### Cleanup

- 删除冗余文件 `JsonResponse.java` 和 `JsonResponseerror.java`（已合并到 `JsonResponseUtil`）
- 更新测试文件适配新架构
- 新增 `PathUtilTest`、`XmlUtilTest`、`DeliveryItemTest` 单元测试

## 1.0.0

### Features

- 系统全局配置：支持多个源 Jenkins 实例配置（名称、地址、凭据），每个实例支持连接测试按钮
- 凭据选择：系统凭据下拉框支持直接新建凭据
- API Token 认证：使用用户名 + API Token（Secret Text 凭据）认证方式，凭据选择器支持直接新建凭据
- 根目录和文件夹左侧任务栏添加"Job Promotion"入口
- 晋级页面支持选择指定的源 Jenkins 实例
- 目录任务固定使用当前目录路径，只显示对应源目录下的任务
- 根目录允许显示和晋级所有任务
- 权限控制：目录页面需要 CREATE 和 CONFIGURE 权限才显示菜单，根目录需要 ADMINISTER 权限
- 远程任务列表加载，支持多层级嵌套目录
- 勾选要晋级的任务，支持全选/取消全选
- 晋级模式：普通更新（跳过已存在）/ 强制更新（覆盖已存在）
- 按源任务 fullPath 幂等同步到当前目录或根目录
- 晋级后自动清理乱码字符
- 晋级后自动清理定时策略（triggers）
- 晋级后自动清理代码监控策略
- 晋级后自动禁用任务
- 确认弹窗显示要晋级的任务列表（使用 fullPath）
- 晋级结果弹窗显示每个任务的状态
- 审计日志功能：根目录下记录所有晋级操作，支持分页浏览（每页20条）
- 审计日志保留时间配置：可在审计日志弹窗中设置保留天数
- 中英文国际化支持（Jelly 和 JS 层面）
- 前端防御性 JSON 解析策略
- 共享线程池管理并发任务
- submitWithAuth() 安全上下文传播
- HTTPS 支持（信任所有 SSL 证书）
- 菜单图标使用 Jenkins 核心内置图标（symbol-download）

### Bug Fixes

- 修复图标显示问题（使用自定义 symbol 图标）
- 修复 Crumb 头支持
- 修复深层嵌套目录查询导致的 HTTP 500 错误
- 修复国际化消息 key 不匹配问题
- 修复多源 Jenkins 实例配置无法保存的问题（改用 Describable + repeatableProperty）
- 修复审计日志保留时间无法保存的问题（改用 StaplerRequest2 解析参数）
