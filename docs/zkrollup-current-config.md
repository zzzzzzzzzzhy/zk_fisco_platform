# ZK Rollup 当前配置

本文档记录当前 zk-rollup 流程在
`/data/Dapp_Share_Platform/competition-platform` 的运行配置。

## 链上合约（Polygon 主网）
- Rollup Registry: `0x6cF40A68f705972f8b42b1C4e2e16424eCc1eBb5`
- Rollup Reward Distributor: `0x6274c362AD736B7FbDBc005c4EC8E855ea328462`
- RISC0 Image ID: `0x06a4c68ab8cc1f68c73a8209c50eb5d7e576a19340e8242c3d2e2dc1921365b9`

来源：`competition-platform/backend/src/main/resources/application.yml`

## 运行环境（Docker）
服务：`competition-backend` + `competition-rollup-worker`（host 网络）。

在 `competition-platform/docker/docker-compose.yml` 中的关键覆盖项：
- `REWARD_ROLLUP_ENABLED=false`（仅手动触发）
- `REWARD_ROLLUP_WINDOW_MINUTES=1440`
- `REWARD_ROLLUP_CRON=0 0 0 * * ?`
- `REWARD_ROLLUP_SUBMIT_CRON=0 0 0 * * ?`
- `REWARD_ROLLUP_DISTRIBUTE_CRON=0 0 0 * * ?`
- `REWARD_ROLLUP_PENDING_GRACE_MINUTES=20`
- `REWARD_ROLLUP_RETRY_MINUTES=20`
- `REWARD_ROLLUP_PROVER_CMD=/app/prover/rollup-prove`
- `REWARD_ROLLUP_PROVER_TIMEOUT_SECONDS=900`
- `REWARD_ROLLUP_LOG_FILE=/app/docs/rollup-execution.log`
- `REWARD_ROLLUP_WORK_DIR=/data/Dapp_Share_Platform/competition-platform/docs/rollup-work`
- `REWARD_ROLLUP_CANCEL_TX_HASHES=0x28f79f01fa3adba2e9bca26b86aff880b5c0c0c6c6d792ef4d89f3fdf3a7f7cb,0xe5b3ad27c8d2a5f30bedc5c6ec8b0f43f12a8e69aa812d325d6b4a7bf8c1be78`
- `REWARD_ROLLUP_CANCEL_NONCE_RANGE=0xfa-0x10d`
- `RISC0_SERVER_PATH=/app/prover/r0vm`

注意：`BLOCKCHAIN_ROLLUP_*` 在默认情况下为空；若设置了
`ROLLUP_REGISTRY_ADDRESS`、`ROLLUP_REWARD_DISTRIBUTOR_ADDRESS` 或
`ROLLUP_IMAGE_ID`，会覆盖上面的合约地址。

## 手动触发
后端支持手动触发，并忽略 `REWARD_ROLLUP_ENABLED`：

- 仅窗口：
  `POST /rollup/run?action=window`
- 仅提交：
  `POST /rollup/run?action=submit`
- 仅分发：
  `POST /rollup/run?action=distribute`
- 一键全流程：
  `POST /rollup/run?action=all`

示例：
```
curl -X POST "http://127.0.0.1:8082/rollup/run?action=all"
```

## 日志与工作目录
- Rollup 日志（宿主机）：`competition-platform/docs/rollup-execution.log`
- Rollup 工作目录（宿主机）：`competition-platform/docs/rollup-work`
