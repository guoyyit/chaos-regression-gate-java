# chaos-regression-gate-java

Spring Boot 示例服务 + 可复现的端到端混沌回归门禁：

- 带版本号与历史追溯的有状态资源 API（过期版本写入返回稳定的 `VERSION_CONFLICT`）
- 声明式场景、确定性调度、客户端与依赖边界的故障注入
- 规范化轨迹与基线比对，自动收缩出最小复现场景
- 并发场景执行、构建门禁（回归即非零退出）

详见 [docs/gate.md](docs/gate.md)。
