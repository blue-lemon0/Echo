# Echo — Android QR 扫码/生成器

Kotlin + Jetpack Compose 实现的离线二维码扫描与生成工具。

## 功能

- **扫码**：CameraX + ML Kit 条码识别，四角定位框，动画扫描线
- **生成二维码**：输入文本 → ZXing 生成 → 保存到相册 / 分享
- **扫描历史**：本地持久化，自动去重
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
