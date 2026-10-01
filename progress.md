
## SUT 完成
- ResultCode 稳定结果码枚举
- ResourceService: 版本控制、历史追溯、幂等键、依赖通知
- ResourceController: REST API + X-Dependency-Observations 响应头
- LoggingEventNotifier / DependencyFaultInjector / DependencyConfig
- ResourceApiTests 验证通过（版本递增、STALE_VERSION、NOT_FOUND、幂等重放）

## 下一步
- 场景加载器、故障计划生成器、轨迹写入器
- HTTP 客户端故障注入、服务启动器、场景运行器
- 基线比较、自动收缩、报告、GateMain

## 门禁核心完成
- ScenarioLoader: JSON 场景加载与校验
- FaultPlanGenerator: 故障计划生成（客户端 + 依赖边界）
- TraceWriter: 规范化轨迹 JSON（无端口/时间戳/随机标识）
- GateHttpClient: 客户端故障注入（LATENCY/TIMEOUT/CONNECTION_RESET/DUPLICATE_DELIVERY）
- ServiceLauncher: 每场景独立端口启动服务
- ScenarioRunner: 波次调度 + 种子化优先级 + 资源串行仲裁
- ResourceApiTests / ScenarioRunnerTests / FaultInjectionTests 全部通过

## 下一步
- TraceComparator 基线比较与分类
- ScenarioShrinker 自动收缩
- ReportWriter + GateMain 构建门禁

## 基线比较与自动收缩完成
- TraceComparator: 行为差异判 REGRESSION，仅波动字段差异判 FLUCTUATION
- ScenarioShrinker: ddmin 式逐步剔除，保留失败签名，最小场景可直接重跑
- TraceComparatorTests / ScenarioShrinkerTests 全部通过

## 下一步
- ReportWriter + GateMain CLI（并发场景、独立端口/目录、非零退出、偶发策略）
- Maven 构建接入
- 确定性回放测试、并发测试、构建门禁测试
- 文档

## 确定性修复完成
- 资源 ID 提示：门禁通过 X-Gate-Resource-Id 头传递确定性资源 ID（基于请求位置）
- 移除 synchronized 避免依赖边界故障死锁
- 移除创建串行化（资源 ID 提示已保证确定性）
- 所有场景确定性验证通过（同场景同多种子轨迹一致）
- 全部 28 个测试通过

## 下一步
- 清理调试代码
- Maven 构建接入（exec 插件 verify 阶段）
- 文档（场景格式、故障类型、判定规则）

## Maven 构建接入完成
- exec-maven-plugin 在 verify 阶段运行门禁
- 基线已生成，完整构建通过（26 测试 + 12 场景 MATCH）

## 下一步
- 编写文档（场景格式、故障类型、判定规则）
- 最终验证

## 最终验证完成
- 全部 26 个测试通过
- Maven verify 构建通过（需 MAVEN_OPTS="-Xmx512m" 避免内存压力导致偶发失败）
- 文档 docs.md 完成
- 12 个场景 + 基线 + 报告完整
