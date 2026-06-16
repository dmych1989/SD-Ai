# PC 后端

PC 端推理服务，监听 1420 端口，对外提供 OpenAI 兼容 API。

## 文件

| 文件 | 说明 |
|------|------|
| `start.bat` | 启动脚本（同时启动 Node 包装层 + native sd-cuda/sd-vulkan） |
| `serve.cjs` | Node 包装层：管理模型、处理 API 路由、代理到 sd-vulkan.exe |
| `一键部署.bat` | 自动下载二进制 + 创建快捷方式 |
| `cuda-check.bat` | 检测 NVIDIA 显卡和 CUDA 驱动 |
| `cuda-diagnostics.bat` | CUDA 失败时排错 |
| `CUDA后端手动下载指南.md` | 见 docs/ 目录 |

## 端口

- **1420**：前端 / API（Node serve.cjs 监听）
- **8080**（或 28088-28120）：native sd-cuda/sd-vulkan，由 serve.cjs 自动选可用端口

## 启动

```bat
start.bat
```

## API 端点

- `GET  /api/health` —— 健康检查
- `GET  /api/models` —— 已安装模型列表
- `GET  /api/telemetry` —— 实时硬件监控（CPU/GPU/温度/显存）
- `GET  /api/backend-status` —— 后端进程状态
- `POST /api/restart-backend` —— 重启后端并加载指定模型
- `POST /api/stop-backend` —— 停止后端
- `POST /v1/images/generations` —— 文生图（OpenAI 兼容）
- `POST /v1/images/edits` —— 图生图（OpenAI 兼容）

## 后端二进制（不包含在仓库）

`start.bat` 期望 `app\backend\win\vulkan\sd-vulkan.exe`（CUDA 失败时回退）或
`app\backend\win\cuda\sd-cuda.exe`（需要 NVIDIA 显卡）。

下载方式见 `一键部署.bat` 或 `docs/CUDA后端手动下载指南.md`。
