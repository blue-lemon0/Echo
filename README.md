# Echo — Android QR 扫码/生成器

Kotlin + Jetpack Compose 实现的离线二维码扫描与生成工具。

## 功能

- **扫码**：CameraX + ML Kit 条码识别，四角定位框，动画扫描线
- **连续扫码**：搭配 [Helix](https://github.com/blue-lemon0/Helix) Chrome 扩展，扫描链式二维码并自动解析拼接长文本
- **生成二维码**：输入文本 → ZXing 生成 → 保存到相册 / 分享
- **扫描历史**：本地持久化，自动去重，区分普通扫码与链式扫码记录
- **设置**：声音 / 震动 / 自动复制开关
- **完全离线**：ML Kit 模型内置，不依赖网络

## 技术栈

| 层 | 选型 |
|---|---|
| UI | Jetpack Compose + Material 3 |
| 相机预览 | CameraX 1.6.1 |
| 条码识别 | ML Kit BarcodeScanning 17.3.0 |
| 二维码生成 | ZXing 3.5.3 |
| 数据持久化 | SharedPreferences（JSON 序列化） |
| 最低 API | 26（Android 8.0） |

## 与 Helix 联动

Echo 的「连续扫码」模式专为 [Helix](https://github.com/blue-lemon0/Helix) Chrome 扩展设计，实现从桌面浏览器到手机的离线数据传输。

### 工作流程

```
Helix (Chrome 扩展)                  Echo (Android)
─────────────────────                ────────────
输入长文本                             切换到「连续」扫码模式
  │                                     │
  ├─ 智能拼接模式                         ├─ 扫描 Helix 逐帧显示的二维码
  ├─ 按字节切分 → JSON 数据包             ├─ 解析 ChainPacket 协议
  ├─ 每段生成独立二维码                    ├─ 收集所有分段
  ├─ 自动播放逐帧展示 ──────────→          ├─ 自动拼接还原原始文本
  │                                     └─ 展示结果（复制/分享/继续）
  └─ 批量下载 / 翻页
```

### 链式二维码协议

每段二维码内容为 JSON 格式：

```json
{"v":1,"t":3,"i":0,"d":"文本段"}
```

- `v` — 协议版本
- `t` — 总段数
- `i` — 当前段索引（从 0 起）
- `d` — 文本数据
