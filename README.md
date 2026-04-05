# 家庭 KTV

这是一个以 **Android 机顶盒为主控** 的家庭 KTV 项目。

当前仓库已经打通一条可运行的 MVP 主链路：

- 机顶盒执行 B 站搜索、下载、本地缓存、播放、队列管理
- 手机通过局域网页面扫码进入同一会话进行控制
- 主机作为独立分离服务，接收机顶盒上传的原始音频并返回伴奏、人声结果
- 机顶盒拉回分离结果后，可在播放时使用伴奏/人声混音控制

## 项目结构

```text
apps/
  stb-android/      Android 机顶盒主应用
  host-separator/   Python 主机分离服务
  mobile-web/       前端源码工程（当前实际托管页以 Android assets 为准）
docs/               PRD 与文档
shared/             契约与共享定义
```

## 三端职责

### 机顶盒 Android

负责：

- B 站搜索
- B 站媒体解析与下载
- 本地资源管理
- 公共点歌队列
- 视频播放
- 手机控制接口
- 上传原始音频到主机
- 下载主机返回的伴奏、人声结果

### 主机分离服务

负责：

- 接收机顶盒上传的原始音频
- 调用 `inst_v1e` 模型提取伴奏
- 输出：
  - `accompaniment.wav`
  - `vocal.wav`

说明：

- 当前模型正式保证的是 **伴奏提取**
- `vocal.wav` 当前来源是 `原始音频 - 伴奏` 的残差结果，主要用于调试和后续扩展

### 手机控制页

负责：

- 扫码绑定
- 搜索
- 下载并分离
- 查看公共队列
- 查看本地资源
- 本地点歌
- 手动触发伴奏分离
- 暂停、继续、下一首
- 总音量 / 人声 / 伴奏调节

## 当前主流程

### 搜索与下载

1. 手机控制页或 TV 搜歌页搜索 B 站内容
2. 搜索结果只做：
   - 下载并分离
3. 机顶盒下载视频和原始音频到本地

### 分离

1. 下载成功后，机顶盒自动上传 `original_audio`
2. 主机创建分离任务
3. 主机返回：
   - `accompaniment.wav`
   - `vocal.wav`
4. 机顶盒下载结果回本地资源目录

### 点歌与播放

1. 只有本地资源页允许加入公共队列
2. 已下载成功且本地文件有效的歌曲可以本地点歌
3. 即使主机未连接或分离失败，歌曲也可以按原唱正常播放
4. 只有分离结果存在时，才会显示人声 / 伴奏混音控制

## 运行方式

## 1. 启动主机分离服务

```powershell
cd D:\APP_self\KTV_self\apps\host-separator
pip install -r requirements.txt
python app.py
```

默认端口：

- `9090`

模拟器访问宿主机时，机顶盒设置页里的主机地址通常填：

- `http://10.0.2.2:9090`

## 2. 启动 Android 机顶盒应用

打开：

- `apps/stb-android`

然后运行 `app` 模块。

## 3. 配置主机地址

在机顶盒 `设置` 页中：

1. 输入主机分离地址
2. 点击 `保存主机地址`
3. 点击 `测试主机连通性`

预期：

- `HTTP 200`

## 4. 手机扫码控制

手机扫码机顶盒首页二维码后，会打开控制页。

说明：

- 当前真实托管页是：
  - `apps/stb-android/app/src/main/assets/mobile-web/index.html`
- `apps/mobile-web/src/...` 当前不是直接上线版本

## 本地资源目录

歌曲资源目录：

```text
Android/data/<package>/files/ktv/songs/{songId}/
```

当前可能包含：

- `video.mp4`
- `original_audio.m4a`
- `accompaniment.wav`
- `vocal.wav`

## 主机接口

主机服务当前提供：

- `POST /api/separate/create`
- `GET /api/separate/status?task_id=...`
- `GET /api/separate/result?task_id=...`
- `GET /api/separate/file/{taskId}/accompaniment`
- `GET /api/separate/file/{taskId}/vocal`

## 当前已完成能力

- 多手机同 session
- 局域网页面控制
- 公共队列
- Room 持久化
- 真实 B 站搜索
- 真实下载
- 本地资源管理
- 视频播放
- TV 端搜歌页
- B 站扫码登录态接入
- 主机独立分离服务
- 自动分离与手动补分离
- 人声 / 伴奏双滑杆控制

## 当前约束与已知限制

- 搜索结果页不直接点歌，只负责下载并分离
- 点歌统一从本地资源页进入队列
- 分离质量当前优先保证伴奏
- `vocal.wav` 当前是残差结果，不应视为高质量正式产物
- 主机分离目前主要优化方向仍然是伴奏质量
- 手机控制页和 TV 页面仍有继续收口空间

## 后续建议

如果继续迭代，建议优先顺序：

1. 继续优化主机分离质量
2. 收口 TV 页面体验
3. 做完整验收与回归
4. 再考虑更复杂的原唱 / 伴唱混音策略

## 说明

当前版本已经是一条可运行的 MVP 链路，重点已从“搭骨架”转到“质量优化与体验收口”。
