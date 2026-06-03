# Job Promotion Plugin

Jenkins 任务晋级插件 - 从源 Jenkins 实例晋级（同步）任务到当前 Jenkins。

## 功能特性

- **系统全局配置**：在 Manage Jenkins 中配置源 Jenkins 地址、系统凭据（用户名/密码），支持连接测试按钮验证认证
- **任务晋级入口**：根目录和文件夹（Folder）左侧任务栏添加"Job Promotion"入口
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
- **中英文国际化**：支持中文和英文界面
- **防御性 JSON 解析**：前端采用防御性 JSON 解析策略，确保后端返回非 JSON 内容也能优雅处理
- **线程池管理**：
  - 避免线程无限增长导致的内存泄漏
  - 防止多个用户同时操作时的线程阻塞
  - 统一管理并发任务，提高系统稳定性
  - 两个 Action 类共享同一个线程池实例
  - 通过 `submitWithAuth()` 方法确保安全上下文正确传播

## 系统要求

- Jenkins >= 2.479.2
- Java >= 17

## 构建与安装

```bash
# 构建
mvn clean package -DskipTests

# 生成的 HPI 文件在 target/job-promotion.hpi
# 在 Jenkins -> Manage Plugins -> Advanced -> Upload Plugin 安装
```

## 配置步骤

1. **配置凭据**：Manage Jenkins → Credentials → 添加 Username/Password 类型的凭据
2. **配置源 Jenkins**：Manage Jenkins → System → Job Promotion → 填写源 Jenkins URL 和选择凭据 → 点击 Test Connection 验证
3. **使用晋级**：
   - 在根目录或文件夹页面左侧点击"Job Promotion"
   - 输入源 Jenkins 上的目录路径（留空表示根目录）
   - 点击"Load Jobs"加载远程任务列表
   - 勾选要晋级的任务
   - 选择晋级模式（普通/强制）
   - 点击"Promote"→ 确认 → 查看结果

## 技术架构

```
com.siruoren.jobpromotion
├── JobPromotionGlobalConfig      # 全局配置（GlobalConfiguration）
├── JenkinsRemoteClient           # 远程 Jenkins HTTP 客户端
├── PromotionService              # 晋级服务核心逻辑
├── PromotionThreadPool           # 共享线程池 + 安全上下文传播
├── PromotionResult               # 晋级结果模型
├── RemoteJobInfo                 # 远程任务信息模型
├── RootPromotionAction           # 根目录 Action（RootAction）
├── FolderPromotionAction         # 文件夹 Action
├── FolderPromotionActionFactory  # Folder Action 工厂（TransientActionFactory）
├── JsonResponse                  # JSON 成功响应
└── JsonResponseerror             # JSON 错误响应
```

## License

MIT License
