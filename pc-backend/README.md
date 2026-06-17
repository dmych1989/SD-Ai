# SdAiBackend — C# AOT 单 .exe 出图后端

把 [sd.cpp](https://github.com/leejet/stable-diffusion.cpp) 引擎套上 HTTP 衣服，
让你手机上的 SD-Ai App 连上电脑就能用电脑的显卡/CPU 出图。
最大特点：**编译成单个 14MB 的 .exe，外加 4 个 DLL（49MB 引擎 + 1.4MB 小依赖），
双击就开 HTTP 服务，零安装。**

## 怎么用

1. 把这个文件夹整个拷到电脑任意位置（比如桌面）
2. 编辑 `start.bat`（可选）：改模型目录 `SD_MODEL_DIR`、输出目录 `SD_OUTPUT_DIR`
3. 双击 `start.bat`，黑色窗口出现一行 `Listening on http://0.0.0.0:1420` 就是好了
4. 在 SD-Ai App 设置里，把模式切到"远程 PC"，地址填你这台电脑的局域网 IP（比如 `192.168.0.22:1420`），点测试连接

## 文件清单（64.99 MB 总量）

| 文件                       | 大小       | 干啥用的              |
| -------------------------- | ---------- | --------------------- |
| `SdAiBackend.exe`          | 14.11 MB   | 主程序（AOT 单 exe） |
| `stable-diffusion.dll`     | 49.46 MB   | sd.cpp 引擎本体      |
| `ggml-base.dll`            | 0.61 MB    | ggml 底层            |
| `ggml-cpu.dll`             | 0.75 MB    | ggml CPU 后端        |
| `ggml.dll`                 | 0.06 MB    | ggml 入口            |

> **不要删 DLL**——exe 启动时按名字找它们，少一个就崩。

## 放的模型文件

把你的 `.safetensors` / `.ckpt` / `.gguf` 文件丢到 `SD_MODEL_DIR` 目录下就行。
后端启动会自动扫这个目录加载第一个模型。
切换模型走 HTTP 端点 `POST /sdapi/v1/reload-checkpoint` 传 `{"checkpoint": "模型文件名"}`。

## HTTP 端点（兼容 SD WebUI API）

| 方法   | 路径                       | 说明                          |
| ------ | -------------------------- | ----------------------------- |
| GET    | `/`                        | 看后端 + 引擎 + 当前模型信息 |
| GET    | `/sdapi/v1/progress`       | 当前生图进度（step, ETA）    |
| GET    | `/sdapi/v1/samplers`       | 支持的采样器列表             |
| GET    | `/sdapi/v1/schedulers`     | 支持的调度器列表             |
| GET    | `/sdapi/v1/upscalers`      | 支持的放大器列表             |
| GET    | `/sdapi/v1/options`        | 当前设置                     |
| POST   | `/sdapi/v1/options`        | 修改设置（JSON body）        |
| POST   | `/sdapi/v1/txt2img`        | 文生图（返回 base64 PNG）    |
| POST   | `/sdapi/v1/img2img`        | 图生图（init_images base64） |
| POST   | `/sdapi/v1/interrupt`      | 取消当前生图                 |
| POST   | `/sdapi/v1/reload-checkpoint` | 切换模型                   |
| GET    | `/sdapi/v1/models`         | 已加载模型列表               |

## txt2img 调用示例

```bash
curl -X POST http://192.168.0.22:1420/sdapi/v1/txt2img \
  -H "Content-Type: application/json" \
  -d '{
    "prompt": "a cute cat on sofa, sunlight, 4k",
    "negative_prompt": "blurry, low quality",
    "steps": 20,
    "cfg_scale": 7.0,
    "width": 512,
    "height": 512,
    "seed": 42,
    "sampler_name": "Euler",
    "scheduler": "Karras",
    "batch_size": 1
  }'
```

返回 JSON：
```json
{
  "images": ["data:image/png;base64,iVBORw0KGgo..."],
  "parameters": { "prompt": "...", "steps": 20, ... },
  "info": "Steps: 20, Sampler: Euler, ..."
}
```

## 性能参考（这台电脑：8 核 CPU，无独显）

| 任务                          | 用时    |
| ----------------------------- | ------- |
| 模型首次加载（1.99GB safetensors）| ~3 秒（mmap 内存映射）|
| 256x256 / 4 steps / Euler     | ~31 秒  |
| 512x512 / 20 steps / Euler    | ~5 分钟（待测）|

> **CPU 跑 SD 1.5 慢**——有 NVIDIA 独显的话可以重编 `stable-diffusion.dll`
> 加上 CUDA 后端（需要装 CUDA Toolkit + Vulkan SDK），能快 10-50 倍。

## 从源码自己重编译（可选）

需要先装好：
- .NET 9 SDK（[下载](https://dotnet.microsoft.com/download/dotnet/9.0)）
- CMake（`winget install Kitware.CMake`）
- Visual Studio 2022 + C++ 桌面开发（带 MSVC 工具链）

跑这两个脚本：
```powershell
# 1. 编 sd.cpp 引擎为 DLL（约 5 分钟）
powershell -ExecutionPolicy Bypass -File build-sd-cpp-dll.ps1

# 2. 编 C# AOT 主程序（约 10-15 分钟）
powershell -ExecutionPolicy Bypass -File build-cs-aot.ps1

# 3. 拷 DLL 到 publish 目录 + 清理杂物
powershell -ExecutionPolicy Bypass -File copy-dlls.ps1
powershell -ExecutionPolicy Bypass -File cleanup-publish.ps1
```

## 已知问题

- **CPU 慢**：出图 4-5 分钟一张 512x512。手机走远程时如果卡，App 端调小图（256x256）+ 少步数（4-8 步）能快很多。
- **目前只用 CPU 后端**：没装 CUDA/Vulkan，所以用不上显卡。要 GPU 加速得重编 `stable-diffusion.dll` 时打开 `SD_CUDA=ON` 或 `SD_VULKAN=ON`。
- **单进程串行**：一次只能跑一个生图任务（后端会锁住）。手机 App 同时发多个请求会排队。

## 怎么从 Windows 服务 / 开机自启（高级）

最简单：把 `start.bat` 拖到「启动」文件夹。
想要真后台跑（无黑窗）：用 [nssm](https://nssm.cc/) 把 `SdAiBackend.exe` 注册成 Windows 服务。
