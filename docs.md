# 混沌回归门禁（Chaos Regression Gate）

一套可自动运行的端到端回归门禁，提供可控的故障注入、确定性轨迹回放、基线比对、自动收缩与并发执行能力。

## 目录

- [场景文件格式](#场景文件格式)
- [故障类型](#故障类型)
- [结果判定规则](#结果判定规则)
- [构建接入](#构建接入)
- [命令行参数](#命令行参数)

---

## 场景文件格式

场景文件是 JSON 格式，描述请求序列、并发度与依赖行为。

### 基本结构

```json
{
  "name": "场景名称",
  "seed": 42,
  "parallelism": 4,
  "serializeResourceAccess": true,
  "requests": [ ... ],
  "faults": [ ... ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 否 | 场景名称，缺省取文件名 |
| `seed` | long | 是 | 随机种子，决定调度优先级与故障计划 |
| `parallelism` | int | 否 | 并发度（预留） |
| `serializeResourceAccess` | boolean | 否 | 是否串行化同一资源的写操作，默认 `true` |
| `requests` | array | 是 | 请求列表 |
| `faults` | array | 否 | 故障列表 |

### 请求对象

```json
{
  "id": "create-1",
  "method": "POST",
  "path": "/api/resources",
  "body": {"data": {"name": "foo"}},
  "dependsOn": ["read-1"]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 是 | 请求唯一标识 |
| `method` | string | 是 | HTTP 方法（GET/POST） |
| `path` | string | 是 | 请求路径 |
| `body` | object | 否 | 请求体 |
| `dependsOn` | array | 否 | 依赖的请求 id 列表 |

### 故障对象

```json
{
  "id": "f1",
  "type": "LATENCY",
  "boundary": "CLIENT",
  "request": "create-1",
  "delayMs": 100,
  "after": "read-1"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | string | 否 | 故障标识 |
| `type` | string | 是 | 故障类型，见[故障类型](#故障类型) |
| `boundary` | string | 是 | 故障边界：`CLIENT` 或 `DEPENDENCY` |
| `request` | string | 是 | 目标请求 id |
| `delayMs` | long | 否 | 延迟毫秒数（LATENCY 类型） |
| `after` | string | 否 | 引用的请求 id（OUT_OF_ORDER 类型） |

### 依赖行为

请求之间通过 `dependsOn` 表达依赖关系。门禁根据依赖关系计算波次（wave），同一波次内的请求并发执行，不同波次串行执行。

- `dependsOn`：显式依赖边。
- `OUT_OF_ORDER` 故障会添加隐式依赖边：被引用的请求完成后，当前请求才发送。

### 确定性保证

- 资源 ID：门禁通过 `X-Gate-Resource-Id` 请求头传递确定性资源 ID（基于请求在场景中的位置），确保同一资源的写操作不会因执行顺序不同而产生不同结果。
- 调度优先级：同一种子下，波次内的调度顺序由种子决定。
- 轨迹规范化：轨迹中不包含端口、时间戳、随机标识等每次不同的字段。

---

## 故障类型

门禁支持 6 种故障类型，可在客户端（`CLIENT`）与依赖边界（`DEPENDENCY`）注入。

| 故障类型 | 边界 | 说明 | 轨迹表现 |
|----------|------|------|----------|
| `LATENCY` | CLIENT / DEPENDENCY | 延迟指定毫秒后执行 | 故障记录，结果码不变 |
| `TIMEOUT` | CLIENT | 客户端超时 | 结果码 `TIMEOUT` |
| `CONNECTION_RESET` | CLIENT | 连接中断 | 结果码 `CONNECTION_FAILURE` |
| `DUPLICATE_DELIVERY` | CLIENT / DEPENDENCY | 重复投递 | 两次尝试（客户端）或两条观测（依赖） |
| `OUT_OF_ORDER` | CLIENT / DEPENDENCY | 乱序返回 | 请求被持有至引用请求完成 |
| `FAIL_MIDWAY` | DEPENDENCY | 处理中途失败 | 结果码 `DEPENDENCY_FAILURE`，数据已提交 |

### LATENCY

- **CLIENT**：客户端发送前延迟 `delayMs` 毫秒。
- **DEPENDENCY**：依赖通知前延迟 `delayMs` 毫秒。

### TIMEOUT

- **CLIENT**：客户端等待响应超时，返回 `TIMEOUT`，不发送请求到服务端。

### CONNECTION_RESET

- **CLIENT**：客户端发送前连接中断，返回 `CONNECTION_FAILURE`，不发送请求到服务端。

### DUPLICATE_DELIVERY

- **CLIENT**：客户端发送两次请求。第二次为重试，幂等创建返回 `OK`（`replayed=true`），过期更新返回 `STALE_VERSION`。
- **DEPENDENCY**：依赖被调用两次，观测记录两条事件（eventId 相同）。

### OUT_OF_ORDER

- **CLIENT**：当前请求被持有，直到 `after` 引用的请求完成后才发送。
- **DEPENDENCY**：当前请求的事件被持有，直到 `after` 引用的请求事件投递后才投递。

### FAIL_MIDWAY

- **DEPENDENCY**：依赖通知中途失败，返回 `DEPENDENCY_FAILURE`，但资源数据已提交。

---

## 结果判定规则

### 比较结果分类

门禁将执行结果与基线轨迹比对，分为以下类别：

| 类别 | 说明 | 构建影响 |
|------|------|----------|
| `MATCH` | 轨迹完全一致 | 通过 |
| `REGRESSION` | 行为字段发生变化 | 失败 |
| `FLUCTUATION` | 仅波动字段发生变化 | 通过 |
| `EXPECTED_CHANGE` | 基线已显式更新 | 通过 |
| `STABLE_FAILURE` | 场景执行失败（服务启动失败、超时等） | 失败 |
| `FLAKY` | 多次稳定性运行轨迹不一致 | 按策略配置 |
| `NO_BASELINE` | 无基线且未更新 | 通过（警告） |

### 行为字段与波动字段

- **行为字段**（决定是否为回归）：结果码、资源版本、资源 ID、故障结果、依赖事件。
- **波动字段**（仅归类为波动）：波次（wave）、调度索引（dispatchIndex）、完成索引（completionIndex）。

### 偶发失败策略

当多次稳定性运行（`--stability-runs` > 1）的轨迹不一致时，场景被判定为 `FLAKY`。处理策略由 `--flaky-policy` 配置：

| 策略 | 说明 |
|------|------|
| `FAIL` | 构建失败（默认） |
| `WARN` | 构建通过，报告中警告 |
| `IGNORE` | 构建通过，忽略 |

### 基线更新

基线需要显式确认才会更新。使用 `--update-baseline` 参数更新基线，更新后场景判定为 `EXPECTED_CHANGE`。

### 自动收缩

场景失败后，门禁自动收缩，逐步剔除与失败无关的请求和故障，直到剩下一个仍然稳定复现失败的最小场景。

- 收缩结果落盘到 `gate/reports/minimal/` 目录。
- 最小场景可直接再次执行。

---

## 构建接入

门禁通过 `exec-maven-plugin` 接入 Maven 构建，在 `verify` 阶段执行。

```bash
# 运行门禁（verify 阶段自动执行）
mvn verify

# 仅运行门禁
mvn exec:java

# 更新基线
mvn exec:java -Dexec.args="--scenarios gate/scenarios --baselines gate/baselines --output gate/reports --update-baseline"
```

### 目录结构

```
gate/
├── scenarios/    # 场景文件
├── baselines/    # 基线轨迹
└── reports/      # 执行报告与最小复现场景
```

---

## 命令行参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--scenarios` | - | 场景文件目录 |
| `--baselines` | - | 基线轨迹目录 |
| `--output` | - | 输出目录 |
| `--stability-runs` | 1 | 稳定性运行次数 |
| `--flaky-policy` | FAIL | 偶发失败策略（FAIL/WARN/IGNORE） |
| `--update-baseline` | false | 是否更新基线 |
| `--parallelism` | 4 | 并发场景数 |
| `--scenario-timeout` | 60000 | 单场景超时毫秒数 |
