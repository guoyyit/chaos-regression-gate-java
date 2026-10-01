# Chaos Regression Gate

本仓库的端到端回归门禁：用声明式场景驱动真实 HTTP 服务，注入可控故障，
产出规范化轨迹并与基线比对。同一场景 + 同一随机种子 ⇒ 轨迹逐条一致，
失败时自动收缩出可直接重放的最小复现场景。

## 组成

- **有状态资源服务**（`resource` 包）
  - `POST /resources/{id}` 创建，返回 `201 + {code: CREATED, version: 1}`
  - `PUT /resources/{id}` 携带 `expectedVersion` 更新；过期版本返回 `409 + VERSION_CONFLICT`
  - `GET /resources/{id}` 读取当前版本；`GET /resources/{id}/history` 追溯全部历史版本
  - 所有响应都带稳定结果码：`OK / CREATED / VERSION_CONFLICT / NOT_FOUND / ALREADY_EXISTS / DEPENDENCY_FAILURE / BAD_REQUEST`
- **依赖边界**：服务每次变更都会调用审计依赖（`DependencyClient`），
  故障计划通过 `POST /__control/faults` 安装，`POST /__control/reset` 清空全部可变状态。
- **门禁引擎**（`gate` 包）：场景加载、确定性调度、故障注入、轨迹规范化、
  基线比对、自动收缩、并发执行、报告与退出码。

## 场景文件格式（JSON）

```json
{
  "name": "basic-crud",
  "seeds": [7, 42],
  "steps": [
    {"id": "s1", "stream": "a", "method": "POST", "path": "/resources/item-1",
     "body": "{\"data\":\"v1\"}"},
    {"id": "s3", "stream": "b", "method": "PUT", "path": "/resources/item-1",
     "body": "{\"expectedVersion\":1,\"data\":\"v3\"}",
     "faults": [{"type": "DELAY", "delayMs": 3}]}
  ],
  "dependencyFaults": [{"callIndex": 2, "type": "MIDWAY_FAILURE"}]
}
```

- `seeds`：每个种子独立运行一次（全新服务实例），各自有独立基线。
- `stream`：同一 stream 内的 step 按声明顺序执行；不同 stream 由种子决定的
  确定性调度器交错执行，用于表达并发。
- `faults`：客户端边界故障，作用于该 step。
- `dependencyFaults`：依赖边界故障，`callIndex` 为第 N 次依赖调用（1 起）。

## 故障类型

| 类型 | 边界 | 语义 |
|------|------|------|
| `DELAY` | 客户端/依赖 | 虚拟延迟，只影响调度交错与轨迹波动字段，不真实睡眠 |
| `TIMEOUT` | 客户端/依赖 | 客户端得到合成的 `598 CLIENT_TIMEOUT`；依赖侧抛超时 |
| `CONNECTION_RESET` | 客户端/依赖 | 客户端得到合成的 `597 CONNECTION_RESET`；依赖侧连接中断 |
| `DUPLICATE` | 客户端 | 同一请求实际投递两次，轨迹记录两次尝试（`#2`） |
| `OUT_OF_ORDER` | 客户端 | 该 step 的响应事件延后到后续事件之后入轨迹 |
| `MIDWAY_FAILURE` | 依赖 | 依赖处理完成后才失败：变更已提交但调用方收到 `500 DEPENDENCY_FAILURE` |

## 轨迹与规范化

每次运行把轨迹写到 `target/gate-runs/<scenario>/traces/<name>-seed-<n>.trace.json`。
轨迹条目只含：`seq / stepId / action / request / status / code / body / faultsApplied / volatileFields`。
端口、时间戳、UUID 一律被替换为 `<PORT> / <TIMESTAMP> / <UUID>`；
允许波动的值（如虚拟时钟）只放在 `volatileFields` 中。

## 结果判定规则

| 状态 | 含义 | 是否失败构建 |
|------|------|--------------|
| `PASSED` | 与基线完全一致 | 否 |
| `VOLATILE_ONLY` | 仅波动字段不同 | 否 |
| `EXPECTED_CHANGE` | 行为差异，但场景已在 `gate.expectedChanges` 中声明 | 否 |
| `REGRESSION` | 未声明的行为差异 | **是（退出码 1）** |
| `STABLE_FAILURE` | 场景执行本身失败 | **是（退出码 1）** |
| `FLAKY` | 各种子结果不一致 | 取决于 `gate.flakyPolicy` |
| `ERROR` | 门禁自身错误（如缺基线） | **是（退出码 1）** |

- 行为字段（状态码、结果码、响应体、顺序、故障记录）不同 ⇒ 回归；
  仅 `volatileFields` 不同 ⇒ 单独归类为 `VOLATILE_ONLY`。
- 基线更新必须显式确认：
  `-Dgate.updateBaseline=true -Dgate.updateConfirm=I_UNDERSTAND`，否则拒绝写入。
- 预期变更通过 `-Dgate.expectedChanges=场景名,...` 声明，与回归区分。
- 偶发失败策略：`-Dgate.flakyPolicy=FAIL`（退出码 2）/ `WARN`（默认）/ `IGNORE`。

## 自动收缩与重放

回归发生时，门禁自动贪心剔除与失败无关的 step、step 故障与依赖故障，
直到剩余场景仍稳定复现同一失败签名，结果落盘到
`target/gate-runs/<scenario>/shrunk/<name>.min.json`，可直接重放：

```bash
java -cp ... com.github.guoyyit.chaosregressiongatejava.gate.GateMain \
  replay target/gate-runs/<scenario>/shrunk/<name>.min.json
```

## 并发执行与隔离

`GateRunner` 以固定线程池并发执行场景；每个场景的每个种子使用：
独立的服务实例（随机端口）、独立的依赖桩（随机端口）、独立的运行目录。
场景间不共享任何可变状态；每个场景有整体时间预算（`gate.scenarioTimeoutSeconds`），
超时单独判 `ERROR`，不会拖垮其他场景。

## 构建接入

`RegressionGateBuildTest` 在 `mvn test` / `mvn verify` 中自动执行全部门禁场景，
出现回归或稳定失败时测试失败、构建以非零状态退出，报告写入
`target/gate-runs/gate-report.md`。

### 配置项（系统属性）

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `gate.scenariosDir` | `src/test/resources/gate/scenarios` | 场景目录 |
| `gate.baselinesDir` | `src/test/resources/gate/baselines` | 基线目录 |
| `gate.runsDir` | `target/gate-runs` | 运行产物目录 |
| `gate.parallelism` | `4` | 并发场景数 |
| `gate.scenarioTimeoutSeconds` | `120` | 单场景时间预算 |
| `gate.flakyPolicy` | `WARN` | `FAIL` / `WARN` / `IGNORE` |
| `gate.updateBaseline` | `false` | 允许更新基线 |
| `gate.updateConfirm` | 空 | 必须为 `I_UNDERSTAND` |
| `gate.expectedChanges` | 空 | 声明为预期变更的场景名（逗号分隔） |

### 常用命令

```bash
mvn verify                                  # 跑门禁，回归即失败
mvn test -Dgate.flakyPolicy=FAIL            # 偶发失败也让构建失败
java -Dgate.updateBaseline=true -Dgate.updateConfirm=I_UNDERSTAND \
  -cp target/classes:$(cat target/cp.txt) \
  com.github.guoyyit.chaosregressiongatejava.gate.GateMain update-baseline
```
