参考 文档 ：  
https://github.com/listen1/listen1_mobile  
https://pypi.org/project/bilibili-api-python/

# 家庭 KTV 机顶盒 App 详细 PRD（修正版终稿）

**项目名称：** 家庭 KTV 机顶盒 App  
**文档类型：** 产品需求文档（PRD）  
**版本：** V1.0
**适用对象：** 产品经理、Android TV/机顶盒开发、主机服务开发、前端/H5 开发、测试工程师、项目负责人  
**项目阶段：** 首期可落地版本（MVP / P0）

---

## 1. 文档目标

本文档用于指导家庭 KTV 机顶盒系统的设计、开发、联调、测试与验收。  
本 PRD 聚焦于首期可用版本，要求研发团队能够依据本文档直接开展以下工作：

1. 机顶盒端开发
2. 主机分离服务开发
3. 手机扫码控制页开发
4. 联调与测试
5. 上线前验收

本文档为**功能与交互主文档**，同时兼顾研发可落地所需的状态、接口、数据结构、异常处理与验收标准。

---

## 2. 项目背景与目标

### 2.1 项目背景

为满足家庭娱乐中的 KTV 使用需求，需要开发一套以机顶盒为主业务端、以主机为音频分离节点、以手机为控制入口的家庭 KTV 系统。

系统需要实现以下核心体验：

- 用户打开电视端机顶盒 App
- 电视展示二维码
- 用户用手机扫码进入控制页
- 用户用手机进行 B 站搜歌、点歌、本地点歌、切歌、原唱/伴唱切换、音量控制
- 机顶盒负责 B 站内容搜索、音视频下载、本地资源管理与播放
- 主机只负责将机顶盒提交的媒体做伴奏/人声分离
- 机顶盒保存原始资源与分离结果，实现本地复用

### 2.2 项目目标

本项目首期需要完成一个在家庭局域网内可稳定使用的 KTV 系统，满足以下目标：

#### 目标一：实现最小完整闭环
1. 机顶盒启动并展示二维码  
2. 手机扫码绑定机顶盒  
3. 手机端完成 B 站搜歌与点歌  
4. 机顶盒独立完成 B 站音视频下载  
5. 机顶盒向主机提交分离任务  
6. 主机返回伴奏/人声结果  
7. 机顶盒缓存所有资源并播放  
8. 手机端可实时控制播放  

#### 目标二：保证机顶盒为核心业务端
机顶盒端不仅是播放器，还必须承担：
- B 站搜索
- B 站音视频下载
- 本地资源管理
- 队列管理
- 播放控制承接

#### 目标三：将主机职责收敛为处理节点
主机侧只做音频分离，不参与：
- B 站搜索
- B 站下载
- 队列管理
- 播放控制
- 本地资源管理

#### 目标四：支持本地资源复用
机顶盒必须保存：
- 已下载的原始视频/音频
- 主机分离返回的伴奏
- 主机分离返回的人声

并支持后续本地点歌与直接播放，避免重复下载与重复处理。

---

## 3. 产品定位

本产品定位为：

**适用于家庭局域网环境的轻量级 KTV 系统。**

产品特征如下：

1. 以机顶盒为主业务端
2. 以手机为输入和控制入口
3. 以主机为音频分离工具节点
4. 强调本地缓存与资源复用
5. 优先满足“能搜、能点、能播、能切、能缓存”的核心需求

---

## 4. 业务边界

### 4.1 本期范围（P0）

本期必须实现以下能力：

#### 机顶盒端
1. 启动页/首页  
2. 二维码展示与扫码绑定  
3. B 站搜索能力承接  
4. B 站音视频下载  
5. 本地点歌队列  
6. 播放器  
7. 原唱/伴唱切换  
8. 切歌  
9. 音量控制  
10. 本地资源库  
11. 将待处理媒体提交给主机  
12. 接收主机返回的伴奏/人声  
13. 本地缓存管理  

#### 手机端
1. 扫码绑定  
2. 搜歌  
3. 点歌  
4. 本地点歌  
5. 切歌  
6. 原唱/伴唱切换  
7. 音量控制  
8. 查看当前播放  
9. 查看队列  

#### 主机端
1. 接收机顶盒提交的媒体文件/任务  
2. 执行伴奏/人声分离  
3. 返回处理状态  
4. 返回伴奏与人声结果  

### 4.2 本期不做

首期明确不做以下内容：

1. 多平台音乐源接入
2. 账号体系
3. 会员体系
4. 公网远程控制
5. 在线社区/评论/弹幕
6. 歌单、收藏、排行榜
7. 歌词逐字高亮
8. 打分、评分、AI 修音
9. 麦克风混音、录音、回放
10. 复杂推荐系统
11. 复杂权限体系

---

## 5. 角色定义

### 5.1 角色一：机顶盒 App

机顶盒 App 是系统核心业务端，负责以下工作：

#### 核心职责
1. 启动应用并注册当前设备  
2. 展示二维码供手机扫码  
3. 承接手机端搜索请求  
4. 完成 B 站搜索  
5. 完成 B 站音视频下载  
6. 保存原始视频/音频到本地  
7. 发起分离任务到主机  
8. 接收伴奏/人声结果  
9. 保存伴奏与人声文件到本地  
10. 管理播放队列  
11. 执行播放、切歌、模式切换、音量调节  
12. 展示本地资源库  
13. 向手机端同步当前状态  

#### 角色结论
**机顶盒是系统主控，不是纯播放器。**

### 5.2 角色二：系统主机

系统主机是音频处理节点，仅承担媒体分离任务。

#### 核心职责
1. 接收机顶盒提交的媒体文件或媒体地址
2. 执行伴奏/人声分离
3. 输出处理结果：
   - 伴奏文件
   - 人声文件
4. 返回处理状态与错误信息
5. 提供结果下载或回传能力

#### 明确不负责
1. 不负责 B 站搜索
2. 不负责 B 站下载
3. 不负责点歌队列
4. 不负责播放器
5. 不负责本地缓存
6. 不负责手机控制入口

#### 角色结论
**主机是音频分离服务，不是媒体中心。**

### 5.3 角色三：手机控制端

手机端是控制入口，承担以下工作：

1. 扫码绑定机顶盒
2. 发起 B 站搜索
3. 查看搜索结果
4. 点歌
5. 本地点歌
6. 切歌
7. 原唱/伴唱切换
8. 音量控制
9. 查看当前播放
10. 查看队列状态

#### 角色结论
**手机端只负责控制与输入，不负责下载与处理。**

---

## 6. 系统架构

### 6.1 架构总览

系统由三部分组成：

#### A. 机顶盒 App
负责：
- 搜索 B 站
- 下载音视频
- 管理本地缓存
- 播放与控制执行
- 队列管理
- 手机扫码绑定
- 主机分离任务发起与结果接收

#### B. 主机分离服务
负责：
- 接收机顶盒待处理媒体
- 执行伴奏/人声分离
- 返回结果

#### C. 手机控制页
负责：
- 扫码绑定
- 搜歌
- 点歌
- 本地点歌
- 切歌
- 原唱/伴唱切换
- 音量控制

### 6.2 推荐通信关系

#### 手机端 ↔ 机顶盒
这是主要业务交互链路，用于：
- 绑定会话
- 搜歌
- 点歌
- 队列查看
- 播放控制

#### 机顶盒 ↔ 主机
这是处理链路，用于：
- 提交待分离媒体
- 查询分离状态
- 获取伴奏/人声结果

#### 手机端 ↔ 主机
首期原则上不直接通信，避免主机承担业务入口角色。

### 6.3 推荐实现方式

#### 手机与机顶盒通信
建议机顶盒内置轻量本地服务能力，供手机端访问：
- HTTP API
- WebSocket 状态同步

二维码内容建议指向机顶盒自身提供的手机控制页地址，或指向一个能连接到机顶盒的控制页入口。

#### 机顶盒与主机通信
建议使用：
- HTTP 文件上传/下载
- HTTP 状态查询
- WebSocket 非必须，首期可用轮询

### 6.4 架构原则

1. 机顶盒主业务化
2. 主机轻职责化
3. 手机轻控制化
4. 所有可复用资源本地化
5. 处理任务异步化
6. 搜索、下载、缓存、播放全部由机顶盒主导

---

## 7. 核心使用场景

### 7.1 首次使用场景

1. 用户打开机顶盒 App
2. 机顶盒尝试加载主页面
3. 电视展示二维码
4. 用户使用手机扫码
5. 手机打开控制页并完成绑定
6. 用户开始搜歌点歌

### 7.2 正常点歌场景

1. 用户在手机端输入关键词
2. 手机将搜索请求发给机顶盒
3. 机顶盒执行 B 站搜索
4. 搜索结果返回手机端
5. 用户选中歌曲并点歌
6. 机顶盒开始下载该歌曲的原始音视频
7. 下载完成后，机顶盒将待处理媒体提交给主机
8. 主机完成伴奏/人声分离
9. 主机返回伴奏与人声结果
10. 机顶盒保存原始资源与分离结果
11. 歌曲入队
12. 到达顺序后开始播放

### 7.3 本地点歌场景

1. 用户进入本地资源页
2. 查看已缓存歌曲
3. 选择一首歌加入队列
4. 机顶盒直接读取本地文件
5. 若资源完整，则支持原唱/伴唱切换
6. 若资源不完整，则按可用模式播放或提示补处理

### 7.4 播放控制场景

1. 手机端查看当前播放
2. 用户点击切歌
3. 机顶盒停止当前歌曲并播放下一首
4. 用户点击原唱/伴唱切换
5. 机顶盒切换模式
6. 用户调节音量
7. 机顶盒调整音量并同步状态

---

## 8. 核心业务流程

### 8.1 启动与绑定流程

#### 前置条件
- 机顶盒与手机在同一局域网
- 机顶盒 App 已启动

#### 流程
1. 机顶盒启动 App
2. 机顶盒初始化设备信息
3. 机顶盒生成当前会话 session
4. 机顶盒生成二维码
5. 二维码包含：
   - device_id
   - session_id
   - host/ip/port
   - expire_at
   - sign
6. 手机扫码打开控制页
7. 手机根据二维码内容与机顶盒建立连接
8. 机顶盒校验并绑定成功
9. 电视显示“已连接手机”

#### 异常处理
- 二维码过期：提示重新扫码
- 机顶盒不可达：提示网络异常
- session 无效：提示绑定失败

### 8.2 搜索流程

#### 流程
1. 用户在手机端输入关键词
2. 手机调用机顶盒搜索接口
3. 机顶盒接收关键词
4. 机顶盒执行 B 站搜索
5. 机顶盒返回搜索结果
6. 手机端展示结果列表

#### 搜索结果包含
- 标题
- UP 主/发布者
- 时长
- 封面
- source_id
- 来源：B站

### 8.3 点歌与下载流程

#### 流程
1. 手机端点歌
2. 机顶盒创建歌曲实体和任务实体
3. 机顶盒检查是否已存在本地缓存
4. 若存在完整缓存，则直接入队
5. 若不存在，则发起下载
6. 下载原始视频/音频到本地
7. 下载成功后更新下载状态
8. 自动发起分离任务或根据策略延迟发起
9. 入队等待播放

### 8.4 分离流程

#### 流程
1. 机顶盒下载完原始媒体
2. 机顶盒向主机创建分离任务
3. 机顶盒将原始文件上传到主机，或提供主机可访问文件地址
4. 主机开始处理
5. 机顶盒轮询分离状态
6. 主机处理完成后返回：
   - accompaniment
   - vocal
7. 机顶盒下载/接收结果到本地
8. 更新资源完整度

#### 说明
分离流程与播放流程可并行设计，但首期建议：
- 下载完成后即可入队
- 若伴奏未就绪，则先按原唱播放
- 伴奏完成后支持切换到伴唱

### 8.5 原唱/伴唱切换流程

#### 流程
1. 当前歌曲播放中
2. 用户点击切换模式
3. 机顶盒检查伴奏文件是否存在
4. 若存在，则切换为伴唱模式
5. 若不存在，则提示“伴唱未准备好”
6. 切换结果同步到电视端与手机端

### 8.6 本地复用流程

#### 流程
1. 用户再次点同一首歌
2. 机顶盒检查 song_id/source_id 是否有缓存
3. 若有完整缓存：
   - 不再重新下载
   - 不再重新分离
   - 直接加入队列
4. 若仅有原始资源：
   - 可直接原唱播放
   - 可选择继续提交分离任务
5. 若文件损坏：
   - 标记 broken
   - 允许重新下载或重新分离

---

## 9. 功能需求详述

### 9.1 机顶盒首页

#### 功能目标
首页作为电视端主入口，用于展示绑定入口、状态概览和主要功能导航。

#### 页面元素
1. 页面标题：家庭 KTV
2. 当前连接状态
   - 手机是否已绑定
   - 主机是否可用
3. 二维码区域
4. 当前播放简报
   - 歌曲名
   - 模式
   - 当前进度
5. 功能入口
   - 播放页
   - 队列页
   - 本地资源库
   - 设置页

#### 交互要求
1. 未绑定时二维码区域高亮
2. 已绑定后可展示“重新绑定”入口
3. 使用遥控器可完成焦点移动
4. 首页能直接跳到所有核心页面

#### 页面状态
- 未绑定
- 已绑定
- 正在播放
- 主机异常
- 网络异常

#### 验收标准
- 用户进入首页后能立刻看到二维码
- 用户可从首页快速进入本地资源、队列、设置

### 9.2 扫码绑定模块

#### 功能目标
建立手机端和机顶盒当前会话的绑定关系。

#### 功能点
1. 机顶盒生成二维码
2. 二维码内容带会话信息
3. 手机扫码后完成绑定
4. 支持重新绑定
5. 支持二维码有效期
6. 支持断开后重新连接

#### 二维码字段建议
- schema/url
- device_id
- session_id
- ip
- port
- expire_at
- sign

#### 规则
1. 二维码默认有效期 5 分钟
2. 绑定成功后机顶盒显示手机已连接
3. 可允许 1~2 台手机同时控制，首期默认 1 台优先

#### 验收标准
- 手机扫码后可看到当前设备控制页
- 非法二维码不可控制设备

### 9.3 B 站搜索模块

#### 功能目标
允许用户通过手机端搜索 B 站歌曲资源，搜索逻辑由机顶盒执行。

#### 功能点
1. 手机端输入搜索关键词
2. 机顶盒执行 B 站搜索
3. 返回结果列表
4. 结果项支持点歌

#### 结果展示字段
- 封面
- 标题
- UP 主/作者
- 时长
- 来源标签：B站
- 点歌按钮

#### 搜索规则
1. 只支持 B 站
2. 首期不做平台切换
3. 首期不做复杂筛选
4. 关键词为空时不可提交

#### 异常处理
- 无结果：提示换关键词
- 搜索失败：提示稍后重试
- 机顶盒异常：提示设备不可用

#### 验收标准
- 手机输入关键词后可返回结果
- 结果项可成功点歌

### 9.4 下载模块

#### 功能目标
机顶盒根据点歌结果自行完成 B 站音视频资源下载。

#### 功能点
1. 点歌后生成下载任务
2. 机顶盒根据 source_id 发起资源获取
3. 下载原始视频或原始音频到本地
4. 下载过程中展示状态
5. 下载完成后更新本地资源库
6. 下载完成后可发起分离任务

#### 下载状态
- pending
- downloading
- success
- failed

#### 下载结果
- 原始视频文件
- 原始音频文件（如有）
- 封面
- 基础元数据

#### 验收标准
- 机顶盒能够独立完成音视频下载
- 下载完成文件可在本地资源中看到
- 下载失败有错误提示

### 9.5 分离任务模块

#### 功能目标
机顶盒将原始媒体提交给主机，获取伴奏与人声结果。

#### 功能点
1. 机顶盒创建分离任务
2. 向主机提交媒体文件/媒体路径
3. 查询分离状态
4. 获取分离结果
5. 将结果保存到本地
6. 更新歌曲完整度

#### 分离状态
- pending
- processing
- success
- failed

#### 输入
- song_id
- 原始媒体文件
- 文件格式
- 可选元数据

#### 输出
- accompaniment
- vocal
- checksum
- error_message

#### 验收标准
- 主机不依赖 B 站即可处理任务
- 分离成功后机顶盒可切伴唱

### 9.6 点歌队列模块

#### 功能目标
统一管理当前播放和待播内容。

#### 队列结构
- 当前播放项
- 待播项列表

#### 功能点
1. 查看当前播放
2. 查看待播歌曲
3. 删除待播项
4. 置顶下一首
5. 清空待播队列
6. 展示任务处理状态
7. 展示缓存状态

#### 队列项信息
- 标题
- 封面
- 当前处理状态
- 当前缓存状态
- 资源完整度
- 点歌时间

#### 去重规则
默认策略：
1. 同一 source_id 已在队列中时，提示已存在
2. 设置页可配置是否允许重复点歌

#### 验收标准
- 队列顺序变化后立即同步
- 手机与电视显示一致

### 9.7 播放器模块

#### 功能目标
机顶盒端执行稳定播放和 KTV 核心控制。

#### 支持能力
1. 播放视频或音频
2. 自动播放下一首
3. 切歌
4. 原唱/伴唱切换
5. 音量调节
6. 状态同步
7. 异常容错

#### 播放模式
- original：原唱
- accompaniment：伴唱

#### 展示内容
- 歌曲名称
- 视频画面或封面
- 当前模式
- 当前进度
- 音量
- 下一首提示

#### 规则
1. 若伴奏文件未就绪，则仅支持原唱
2. 若当前为伴唱模式但伴奏文件丢失，则退回原唱
3. 播放失败时跳过当前歌曲并记录日志

#### 验收标准
- 连续播放多首歌不崩溃
- 切歌有效
- 模式切换有效

### 9.8 原唱/伴唱切换模块

#### 功能目标
支持播放时切换唱歌模式。

#### 功能点
1. 查看当前模式
2. 点击切换模式
3. 切换成功后更新 UI
4. 切换失败时提示原因

#### 限制条件
- 伴唱模式必须存在 accompaniment 文件
- 若伴奏未就绪，按钮置灰或提示不可切换

#### 验收标准
- 有伴奏资源时可切到伴唱
- 无伴奏资源时不可误切换

### 9.9 音量控制模块

#### 功能目标
允许手机与电视控制当前播放音量。

#### 功能点
1. 音量加
2. 音量减
3. 滑杆设置
4. 实时同步当前音量

#### 规则
- 范围 0~100
- 快速连续设置需要防抖
- 音量变更要立即作用于当前播放

#### 验收标准
- 手机调节音量后电视立即变化
- 音量显示一致

### 9.10 本地资源库模块

#### 功能目标
管理机顶盒已缓存的全部资源并支持复用。

#### 本地必须保存的内容
1. 原始视频
2. 原始音频
3. 伴奏文件
4. 人声文件
5. 封面
6. 元数据

#### 页面能力
1. 查看本地歌曲列表
2. 搜索本地歌曲
3. 查看资源完整度
4. 直接加入队列
5. 立即播放
6. 删除单曲缓存
7. 清理全部缓存

#### 资源完整度定义
- downloaded_only：仅原始资源已下载
- with_accompaniment：原始资源 + 伴奏
- full：原始资源 + 伴奏 + 人声
- broken：数据或文件损坏

#### 验收标准
- 已下载资源能在本地资源页看到
- 已完整资源可直接本地点歌
- 删除缓存会同步清理文件与记录

### 9.11 设置模块

#### 功能目标
承载系统配置和缓存管理。

#### 功能点
1. 主机地址配置
2. 主机连通性检测
3. 默认播放模式设置
4. 是否允许重复点歌
5. 本地缓存空间查看
6. 清理缓存
7. 重新生成二维码
8. 查看版本信息
9. 查看日志入口（可选）

#### 验收标准
- 用户可完成主机配置与缓存清理
- 用户可查看系统状态

### 9.12 手机控制页

#### 功能目标
作为用户最主要的操作入口。

#### 页面内容
1. 当前设备信息
2. 当前播放信息
3. 搜歌入口
4. 队列入口
5. 本地资源入口
6. 切歌按钮
7. 原唱/伴唱按钮
8. 音量控制区

#### 验收标准
- 用户无需安装 App 就可控制机顶盒
- 高频操作 1~2 步内完成

---

## 10. 页面设计与交互细则

### 10.1 机顶盒页面清单

1. 首页
2. 播放页
3. 队列页
4. 本地资源页
5. 设置页
6. 加载/空态页
7. 异常提示弹层

### 10.2 手机页面清单

1. 绑定成功页
2. 搜索页
3. 搜索结果页
4. 当前播放控制页
5. 队列页
6. 本地资源页
7. 错误提示页

### 10.3 机顶盒首页交互

#### 默认布局建议
- 左侧：二维码
- 右侧：当前播放信息/绑定状态
- 下方：功能入口

#### 遥控器焦点
- 初始焦点在二维码或主功能入口
- 左右切换模块
- 上下切换卡片
- OK 执行

#### 文案示例
- 未绑定：请使用手机扫码点歌
- 已绑定：手机已连接
- 主机异常：分离主机不可用，但可继续播放本地歌曲

### 10.4 播放页交互

#### 显示内容
- 视频或封面
- 歌曲标题
- 模式
- 进度条
- 音量条
- 下一首歌曲提示

#### 控制入口
- 切歌
- 原唱/伴唱切换
- 暂停/继续（可选）
- 查看队列

### 10.5 本地资源页交互

#### 列表字段
- 封面
- 标题
- 来源
- 资源完整度
- 缓存时间

#### 操作
- 加入队列
- 立即播放
- 删除缓存

### 10.6 手机搜索页交互

#### 页面元素
- 搜索框
- 搜索按钮
- 搜索结果列表

#### 结果项操作
- 点歌

#### 文案示例
- 搜索中：正在搜索
- 无结果：未找到相关内容
- 异常：搜索失败，请稍后重试

### 10.7 手机控制页交互

#### 顶部区
- 当前设备名称
- 当前连接状态

#### 当前播放区
- 歌曲名
- 模式
- 进度
- 音量

#### 按钮区
- 切歌
- 原唱/伴唱
- 音量减
- 音量滑杆
- 音量加

---

## 11. 状态机设计

### 11.1 设备绑定状态

- init
- unbound
- bound
- disconnected

转移：
- init → unbound
- unbound → bound
- bound → unbound
- 任意状态 → disconnected

### 11.2 下载状态

- pending
- downloading
- success
- failed

### 11.3 分离状态

- pending
- processing
- success
- failed

### 11.4 播放状态

- idle
- buffering
- playing
- paused
- ended
- error

### 11.5 资源状态

- not_exist
- downloaded_only
- with_accompaniment
- full
- broken
- deleted

---

## 12. 数据结构设计

### 12.1 Song

```json
{
  "song_id": "string",
  "source_type": "bilibili",
  "source_id": "string",
  "title": "string",
  "artist": "string",
  "duration": 0,
  "cover_url": "string",
  "cover_local_path": "string",
  "video_path": "string",
  "original_audio_path": "string",
  "accompaniment_path": "string",
  "vocal_path": "string",
  "download_status": "pending|downloading|success|failed",
  "separate_status": "pending|processing|success|failed",
  "resource_level": "downloaded_only|with_accompaniment|full|broken",
  "file_size": 0,
  "created_at": "datetime",
  "updated_at": "datetime"
}
```

### 12.2 QueueItem

```json
{
  "queue_id": "string",
  "song_id": "string",
  "queue_status": "waiting|playing|finished|removed",
  "play_mode": "original|accompaniment",
  "position": 0,
  "ordered_by": "string",
  "created_at": "datetime"
}
```

### 12.3 SeparateTask

```json
{
  "task_id": "string",
  "song_id": "string",
  "status": "pending|processing|success|failed",
  "progress": 0,
  "error_code": "string",
  "error_message": "string",
  "created_at": "datetime",
  "updated_at": "datetime",
  "completed_at": "datetime"
}
```

### 12.4 Session

```json
{
  "session_id": "string",
  "device_id": "string",
  "device_name": "string",
  "bind_status": "unbound|bound",
  "mobile_count": 0,
  "host_ip": "string",
  "port": 0,
  "expire_at": "datetime",
  "last_active_time": "datetime"
}
```

---

## 13. 接口需求

以下接口分为两类：

1. 手机端调用机顶盒
2. 机顶盒调用主机

### 13.1 机顶盒对手机提供的接口

#### 1）获取会话状态
`GET /api/session/status`

返回：
```json
{
  "code": 0,
  "data": {
    "session_id": "string",
    "bind_status": "bound",
    "device_name": "Living Room TV",
    "current_song": {
      "song_id": "string",
      "title": "string"
    }
  }
}
```

#### 2）绑定会话
`POST /api/session/bind`

请求：
```json
{
  "session_id": "string",
  "sign": "string"
}
```

#### 3）B站搜索
`GET /api/search/bilibili?keyword=xxx&page=1&page_size=20`

返回：
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "source_type": "bilibili",
        "source_id": "string",
        "title": "string",
        "artist": "string",
        "duration": 240,
        "cover_url": "string"
      }
    ],
    "page": 1,
    "page_size": 20,
    "has_more": true
  }
}
```

#### 4）创建点歌
`POST /api/order/create`

请求：
```json
{
  "session_id": "string",
  "source_type": "bilibili",
  "source_id": "string",
  "title": "string",
  "cover_url": "string"
}
```

返回：
```json
{
  "code": 0,
  "data": {
    "song_id": "string",
    "download_status": "pending"
  }
}
```

#### 5）获取当前队列
`GET /api/queue/list`

返回：
```json
{
  "code": 0,
  "data": {
    "current": {
      "queue_id": "string",
      "song_id": "string",
      "title": "string",
      "play_mode": "original"
    },
    "upcoming": [
      {
        "queue_id": "string",
        "song_id": "string",
        "title": "string",
        "resource_level": "with_accompaniment"
      }
    ]
  }
}
```

#### 6）删除队列项
`POST /api/queue/remove`

请求：
```json
{
  "queue_id": "string"
}
```

#### 7）置顶下一首
`POST /api/queue/move-next`

请求：
```json
{
  "queue_id": "string"
}
```

#### 8）切歌
`POST /api/player/next`

#### 9）切换模式
`POST /api/player/mode`

请求：
```json
{
  "mode": "original"
}
```

#### 10）调节音量
`POST /api/player/volume`

请求：
```json
{
  "action": "set",
  "value": 70
}
```

#### 11）获取当前播放状态
`GET /api/player/status`

返回：
```json
{
  "code": 0,
  "data": {
    "play_status": "playing",
    "current_time": 120,
    "duration": 240,
    "volume": 70,
    "mode": "accompaniment",
    "current_song": {
      "song_id": "string",
      "title": "string"
    }
  }
}
```

#### 12）获取本地资源列表
`GET /api/local/list?page=1&page_size=50`

返回：
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "song_id": "string",
        "title": "string",
        "resource_level": "full",
        "file_size": 123456789
      }
    ]
  }
}
```

#### 13）本地点歌
`POST /api/local/order`

请求：
```json
{
  "song_id": "string"
}
```

#### 14）删除本地缓存
`POST /api/local/delete`

请求：
```json
{
  "song_id": "string"
}
```

### 13.2 机顶盒调用主机的接口

#### 1）创建分离任务
`POST /api/separate/create`

请求：
```json
{
  "song_id": "string",
  "file_path": "string",
  "file_format": "mp4"
}
```

或文件上传方式：
```json
{
  "song_id": "string",
  "file_binary": "..."
}
```

返回：
```json
{
  "code": 0,
  "data": {
    "task_id": "string",
    "status": "pending"
  }
}
```

#### 2）查询分离任务状态
`GET /api/separate/status?task_id=xxx`

返回：
```json
{
  "code": 0,
  "data": {
    "task_id": "string",
    "status": "processing",
    "progress": 60
  }
}
```

#### 3）获取分离结果
`GET /api/separate/result?task_id=xxx`

返回：
```json
{
  "code": 0,
  "data": {
    "task_id": "string",
    "status": "success",
    "accompaniment_url": "string",
    "vocal_url": "string",
    "checksum": {
      "accompaniment": "string",
      "vocal": "string"
    }
  }
}
```

---

## 14. WebSocket 事件建议

建议机顶盒对手机端建立 session 级 WebSocket。

### 14.1 机顶盒 → 手机端

#### 事件：queue_updated
```json
{
  "event": "queue_updated",
  "data": {
    "current_song_id": "string",
    "queue_count": 3
  }
}
```

#### 事件：player_updated
```json
{
  "event": "player_updated",
  "data": {
    "play_status": "playing",
    "mode": "original",
    "volume": 60,
    "current_song_id": "string"
  }
}
```

#### 事件：download_updated
```json
{
  "event": "download_updated",
  "data": {
    "song_id": "string",
    "download_status": "downloading",
    "progress": 45
  }
}
```

#### 事件：separate_updated
```json
{
  "event": "separate_updated",
  "data": {
    "song_id": "string",
    "status": "processing",
    "progress": 80
  }
}
```

---

## 15. 本地存储设计

### 15.1 目录结构建议

```text
/ktv/
  /songs/
    /{song_id}/
      meta.json
      cover.jpg
      video.mp4
      original_audio.m4a
      accompaniment.mp3
      vocal.mp3
  /temp/
  /logs/
```

### 15.2 本地数据库建议

#### 表：song_local
- song_id
- source_type
- source_id
- title
- artist
- duration
- cover_local_path
- video_path
- original_audio_path
- accompaniment_path
- vocal_path
- download_status
- separate_status
- resource_level
- file_size
- created_at
- updated_at

#### 表：queue_local
- queue_id
- song_id
- queue_status
- play_mode
- position
- created_at

#### 表：device_config
- device_id
- device_name
- host_address
- default_play_mode
- allow_duplicate_order
- cache_limit_mb

### 15.3 缓存策略

#### 默认策略
1. 首期不自动清理用户缓存
2. 空间不足时给出提示
3. 支持手动删除单曲
4. 支持手动清空全部缓存

#### 后续可扩展
- LRU 清理
- 按最近播放时间清理
- 只清理不完整资源

---

## 16. 异常处理与降级策略

### 16.1 搜索失败
提示：搜索失败，请稍后重试  
处理：保留关键词，允许再次搜索

### 16.2 下载失败
提示：下载失败  
处理：不入队，支持重试

### 16.3 主机不可用
提示：分离主机不可用  
处理：
- 已有本地完整资源不受影响
- 新歌曲可先原唱播放
- 分离任务延后或失败

### 16.4 分离失败
提示：伴唱处理失败  
处理建议：
- 歌曲仍可原唱播放
- 模式切换中禁用伴唱
- 允许后续重新提交分离

### 16.5 分离结果校验失败
提示：伴唱资源异常  
处理：
- 不标记为完整资源
- 允许重新拉取或重新处理

### 16.6 播放失败
提示：当前歌曲播放异常  
处理：
- 自动跳到下一首
- 日志落盘

### 16.7 手机断开
提示：手机已断开  
处理：
- 电视继续播放
- 支持重新扫码恢复控制

---

## 17. 非功能需求

### 17.1 性能要求

#### 机顶盒端
1. 页面切换流畅
2. 队列显示及时
3. 本地资源 500 首以内可正常浏览
4. 切歌响应及时

#### 手机端
1. 搜索结果展示流畅
2. 控制点击后快速反馈

### 17.2 稳定性要求

1. 连续播放 20 首以上不崩溃
2. 手机刷新页面后可恢复状态
3. 主机短暂不可达时系统不崩溃
4. 文件损坏可识别并处理

### 17.3 可维护性要求

1. 下载模块、分离模块、播放模块、缓存模块解耦
2. 接口返回结构统一
3. 日志格式统一
4. 错误码统一

### 17.4 安全要求

1. 二维码带有效期与签名
2. 仅已绑定 session 的手机可控制当前设备
3. 机顶盒写文件必须限制在应用目录内
4. 主机仅接收合法的媒体处理请求

---

## 18. 埋点与日志需求

### 18.1 埋点建议
1. 二维码曝光
2. 扫码绑定成功
3. 搜索成功/失败
4. 点歌成功/失败
5. 下载成功/失败
6. 分离成功/失败
7. 切歌
8. 模式切换
9. 本地点歌
10. 删除缓存

### 18.2 必要日志
1. 绑定日志
2. 搜索日志
3. 下载日志
4. 分离任务日志
5. 播放异常日志
6. 本地文件校验日志

---

## 19. 验收标准

### 19.1 功能验收

1. 机顶盒启动后展示二维码
2. 手机扫码成功绑定
3. 手机端可搜索 B 站歌曲
4. 点歌后机顶盒开始下载
5. 下载完成后歌曲可入队
6. 机顶盒可向主机提交分离任务
7. 主机返回伴奏/人声
8. 机顶盒能缓存所有资源
9. 可切歌
10. 可调音量
11. 有伴奏时可切伴唱
12. 本地资源可直接点歌

### 19.2 异常验收

1. 二维码过期不能绑定
2. 下载失败不应错误入队
3. 分离失败时仍可原唱播放
4. 文件损坏不能标记为完整资源
5. 播放失败不应导致 App 崩溃

### 19.3 数据验收

1. 本地文件和数据库记录一致
2. 删除缓存后数据完全移除
3. 完整资源可被再次复用
4. 不完整资源状态正确显示

---

## 20. 研发拆分建议

### 20.1 机顶盒端

#### 模块拆分
1. 会话与二维码模块
2. 手机控制接口模块
3. B 站搜索模块
4. B 站下载模块
5. 分离任务提交模块
6. 播放器模块
7. 队列模块
8. 本地资源库模块
9. 设置模块
10. 文件与数据库模块

#### 开发重点
- 下载与缓存一致性
- 播放模式切换
- 遥控器交互
- 局域网对手机服务能力

### 20.2 主机端

#### 模块拆分
1. 分离任务创建
2. 文件接收
3. 音频分离处理
4. 状态查询
5. 结果输出
6. 日志与错误处理

#### 开发重点
- 文件处理链路
- 结果稳定性
- 大文件处理
- 任务异步化

### 20.3 手机端

#### 模块拆分
1. 扫码绑定页
2. 搜索页
3. 搜索结果页
4. 当前播放控制页
5. 队列页
6. 本地资源页
7. WebSocket 状态同步页

#### 开发重点
- 简洁高频操作
- 状态同步
- 局域网连接稳定性

### 20.4 测试拆分

1. 扫码绑定测试
2. 搜索测试
3. 下载测试
4. 分离任务测试
5. 播放控制测试
6. 本地资源测试
7. 异常网络测试
8. 长时间稳定性测试

---

## 21. 里程碑建议

### 里程碑一：基础通信打通
- 二维码绑定
- 手机控制页
- 机顶盒对外接口
- 基础状态同步

### 里程碑二：搜歌与下载打通
- B 站搜索
- 点歌
- 下载
- 队列

### 里程碑三：播放与分离打通
- 播放器
- 主机分离接口
- 分离结果接收
- 原唱/伴唱切换

### 里程碑四：缓存与稳定性优化
- 本地资源页
- 删除缓存
- 异常处理
- 日志完善
- 长测

---

## 22. 首期固定产品决策

本项目首期明确做如下固定决策：

1. 只接 B 站
2. 搜索由机顶盒执行
3. 下载由机顶盒执行
4. 主机只做伴奏/人声分离
5. 手机只做控制和输入
6. 所有资源最终保存在机顶盒本地
7. 本地点歌是首期核心能力
8. 原唱/伴唱切换是首期核心能力
9. 分离失败时仍要保证原唱可播放

---

## 23. 对开发团队的一句话定义

这是一个：

**以机顶盒为核心业务端、以主机为伴奏/人声分离工具节点、以手机为扫码控制入口的家庭 KTV 系统。**

---

# 家庭 KTV 机顶盒 App 技术实施附录（Android 机顶盒版）

**适用平台：** Android 机顶盒 / Android TV  
**建议语言：** Kotlin  
**文档定位：** 在 PRD 基础上补充 Android 研发可直接落地的技术实施说明、接口文档、数据库设计与任务拆解

---

## 一、技术方案总览

### 1.1 端侧职责再次确认

#### 机顶盒 Android App
负责：
1. 对外提供手机控制访问入口  
2. 展示二维码并维护会话  
3. 承接手机端搜歌请求  
4. 调用 B 站搜索能力  
5. 执行音视频下载  
6. 管理本地资源  
7. 调用主机分离接口  
8. 接收分离结果  
9. 播放音视频  
10. 管理播放队列  
11. 响应手机端控制指令  

#### 主机服务
负责：
1. 接收机顶盒上传或提交的媒体  
2. 执行伴奏/人声分离  
3. 返回任务状态  
4. 返回伴奏、人声结果  

#### 手机端 H5
负责：
1. 扫码进入控制页  
2. 搜歌  
3. 点歌  
4. 本地点歌  
5. 查看当前播放  
6. 切歌  
7. 原唱/伴唱切换  
8. 音量控制  

### 1.2 Android 机顶盒端建议技术栈

考虑到你是机顶盒安卓系统，建议采用以下技术组合：

#### 基础框架
- Kotlin
- MVVM
- Jetpack ViewModel
- Kotlin Coroutines + Flow
- Room
- OkHttp / Retrofit
- Media3（播放器）
- WorkManager 或前台 Service 用于下载/任务调度
- Ktor / NanoHTTPD / 内嵌轻量 HTTP Server（用于手机控制访问）
- WebSocket（用于手机端状态实时同步）

#### 为什么这样选
1. 机顶盒是长期运行应用，模块解耦要清晰
2. 播放器、下载、队列、接口服务需要并发管理
3. 本地资源管理必须有数据库
4. 手机扫码控制必须有机顶盒对外局域网访问能力

---

## 二、Android 机顶盒端整体架构

### 2.1 建议分层

建议采用四层结构：

#### 1）UI 层
负责：
- 首页
- 播放页
- 队列页
- 本地资源页
- 设置页
- 状态弹层

#### 2）Domain 层
负责：
- 搜歌用例
- 点歌用例
- 下载用例
- 分离任务用例
- 切歌用例
- 模式切换用例
- 音量控制用例
- 本地点歌用例

#### 3）Data 层
负责：
- B 站搜索与下载数据源
- 主机分离接口数据源
- 本地数据库
- 本地文件管理
- 队列管理
- 会话管理
- WebSocket 推送

#### 4）System 层
负责：
- 局域网 HTTP Server
- 二维码生成
- 播放器内核
- 下载任务调度
- 存储目录管理
- 网络状态监听

### 2.2 Android 工程模块建议

建议不要一开始就拆成很多 Gradle module，首期可以先按包结构分层，等稳定后再拆模块。

建议包结构：

```text
com.xxx.ktv
├── app
├── ui
│   ├── home
│   ├── player
│   ├── queue
│   ├── local
│   └── settings
├── domain
│   ├── usecase
│   └── model
├── data
│   ├── remote
│   │   ├── bilibili
│   │   ├── host
│   │   └── mobile_api
│   ├── local
│   │   ├── db
│   │   ├── dao
│   │   └── entity
│   └── repository
├── player
├── downloader
├── queue
├── session
├── storage
├── websocket
├── server
├── qrcode
└── common
```

---

## 三、Android 机顶盒核心模块设计

### 3.1 会话与二维码模块

#### 模块职责
1. 启动时生成 session_id
2. 获取机顶盒局域网 IP 与端口
3. 生成二维码内容
4. 保存当前会话状态
5. 管理手机绑定状态

#### 建议类
- `SessionManager`
- `QrCodeManager`
- `BindStateRepository`

#### 关键状态
- unbound
- bound
- expired
- disconnected

#### 二维码内容建议
```json
{
  "device_id": "stb-001",
  "session_id": "session-123",
  "ip": "192.168.1.20",
  "port": 8080,
  "expire_at": 1712345678,
  "sign": "xxxx"
}
```

#### Android 注意点
- IP 获取要处理 Wi-Fi / 有线网络场景
- 二维码要支持定时刷新
- 建议二维码 5 分钟过期

### 3.2 手机控制接口模块

#### 模块职责
机顶盒内置一个轻量 HTTP 服务，对手机开放局域网接口。

#### 建议能力
- GET/POST API
- WebSocket 状态同步
- Session 校验
- 跨域处理（若 H5 单独托管）
- 静态页面托管（若控制页直接内嵌在机顶盒内）

#### 建议类
- `MobileApiServer`
- `RouteRegistry`
- `SessionAuthInterceptor`
- `MobileWebSocketHub`

#### 建议端口
- HTTP：8080
- WebSocket：8081 或同端口升级

### 3.3 B 站搜索模块

#### 模块职责
1. 接收手机端关键词
2. 调用 B 站搜索能力
3. 返回结构化搜索结果

#### 建议类
- `BilibiliSearchRepository`
- `SearchBilibiliUseCase`
- `SearchResultMapper`

#### 搜索结果模型
```kotlin
data class SearchSongItem(
    val sourceType: String = "bilibili",
    val sourceId: String,
    val title: String,
    val artist: String?,
    val duration: Long,
    val coverUrl: String?,
    val extra: String?
)
```

#### 说明
- sourceId 你可以统一抽象成内部资源 ID
- 首期不需要歌手识别很准确，没有就用 UP 主或标题提取结果兜底

### 3.4 下载模块

#### 模块职责
1. 根据 sourceId 获取实际音视频资源
2. 下载原始视频/音频
3. 保存到本地
4. 更新下载状态
5. 下载完成后触发分离任务或等待后续触发

#### 建议类
- `DownloadManager`
- `SongDownloadWorker`
- `DownloadRepository`
- `FileAllocator`

#### 下载状态
- pending
- downloading
- success
- failed

#### Android 实现建议
机顶盒端下载不要阻塞 UI，推荐：
- 短任务：Coroutine + Repository
- 长任务：Foreground Service 或 WorkManager

#### 推荐策略
首期建议：
- 下载时写入 temp 文件
- 下载完成后 rename 成正式文件
- 避免中断后出现假完成文件

#### 关键方法示例
```kotlin
interface SongDownloader {
    suspend fun downloadSong(sourceId: String, songId: String): DownloadResult
}
```

### 3.5 分离任务模块

#### 模块职责
1. 根据歌曲资源状态判断是否需要提交分离
2. 调用主机接口创建任务
3. 轮询任务状态
4. 获取伴奏和人声结果
5. 保存到本地
6. 更新资源完整度

#### 建议类
- `SeparateTaskRepository`
- `SeparateTaskManager`
- `CreateSeparateTaskUseCase`
- `PollSeparateTaskUseCase`

#### 状态
- pending
- processing
- success
- failed

#### 建议策略
- 下载完成后自动触发分离
- 若主机不可用，歌曲仍然可原唱播放
- 分离成功后更新成 `with_accompaniment` 或 `full`

### 3.6 播放器模块

#### 模块职责
1. 播放原始视频/音频
2. 切换原唱/伴唱
3. 切歌
4. 通知播放状态
5. 异常恢复

#### Android 建议
使用 Media3 统一播放器能力。

#### 建议类
- `KtvPlayerManager`
- `PlayQueueManager`
- `AudioModeController`
- `PlaybackStateDispatcher`

#### 模式设计
- `original`
- `accompaniment`

#### 首期切换策略建议
为了降低复杂度，首期不要做复杂双轨实时混切，建议：
- 原唱模式：播放原始媒体
- 伴唱模式：播放原始视频画面 + 伴奏音频  
  或  
- 伴唱模式：仅播放伴奏音频 + 封面/视频画面复用

#### 关键点
机顶盒首期优先“稳定播”，不要上来追求特别复杂的音轨同步。

### 3.7 队列模块

#### 模块职责
1. 管理当前播放项
2. 管理待播列表
3. 删除队列项
4. 调整下一首
5. 自动续播下一首

#### 建议类
- `QueueRepository`
- `QueueManager`
- `NextSongResolver`

#### 队列规则
- 当前播放项不可删除
- 仅 waiting 状态歌曲可删除或置顶
- ready/下载中/分离中歌曲都可入 waiting 队列
- 若轮到某首歌播放时只有原始资源，则先按原唱播

### 3.8 本地资源库模块

#### 模块职责
1. 保存资源元数据
2. 保存资源文件路径
3. 校验资源完整性
4. 支持本地点歌
5. 支持删除缓存

#### 建议类
- `LocalSongRepository`
- `SongFileManager`
- `LocalResourceValidator`

#### 首期重点
- 文件和数据库状态必须一致
- 删除时必须清理目录
- broken 状态要能重新修复

---

## 四、Android 页面开发说明

### 4.1 页面列表

#### 机顶盒端
1. `HomeActivity / HomeFragment`
2. `PlayerActivity / PlayerFragment`
3. `QueueFragment`
4. `LocalLibraryFragment`
5. `SettingsFragment`

#### 手机端 H5
1. 绑定成功页
2. 搜索页
3. 搜索结果页
4. 当前控制页
5. 队列页
6. 本地资源页

### 4.2 机顶盒遥控器交互要求

机顶盒不是触摸屏，必须保证焦点体验。

#### 焦点原则
1. 所有主要操作可通过方向键到达
2. 每个页面进入时有默认焦点
3. 焦点高亮明显
4. 返回键路径明确
5. 弹层必须可遥控器关闭

#### 页面要求
- 首页默认焦点在二维码区或“开始点歌”
- 播放页默认焦点在控制区
- 队列页默认焦点在当前列表首项
- 设置页默认焦点在首个设置项

### 4.3 Android 生命周期要求

播放器和下载任务都需要考虑生命周期。

#### 关键要求
1. Activity/Fragment 销毁不应导致下载任务丢失
2. 播放器应与 UI 分离，避免旋转/切后台丢状态
3. 会话与 WebSocket 建议放在 Application 级或 Service 级管理
4. 下载、分离轮询建议放在 Repository/Manager 层，不要绑死 UI

---

## 五、接口文档（Android 机顶盒版）

这里按“**手机调机顶盒**”和“**机顶盒调主机**”分别列。

### 5.1 手机调用机顶盒接口

#### 1）绑定会话
**POST** `/api/session/bind`

请求：
```json
{
  "session_id": "session-123",
  "sign": "abcd"
}
```

响应：
```json
{
  "code": 0,
  "message": "bind success"
}
```

#### 2）查询当前状态
**GET** `/api/session/status`

响应：
```json
{
  "code": 0,
  "data": {
    "session_id": "session-123",
    "device_name": "客厅机顶盒",
    "bind_status": "bound",
    "mobile_count": 1,
    "host_available": true
  }
}
```

#### 3）搜索 B 站
**GET** `/api/search/bilibili?keyword=周杰伦&page=1&page_size=20`

响应：
```json
{
  "code": 0,
  "data": {
    "page": 1,
    "page_size": 20,
    "has_more": true,
    "list": [
      {
        "source_type": "bilibili",
        "source_id": "BV1xxx",
        "title": "周杰伦 稻香",
        "artist": "xxxUP主",
        "duration": 240,
        "cover_url": "https://xxx"
      }
    ]
  }
}
```

#### 4）点歌
**POST** `/api/order/create`

请求：
```json
{
  "session_id": "session-123",
  "source_type": "bilibili",
  "source_id": "BV1xxx",
  "title": "周杰伦 稻香",
  "cover_url": "https://xxx"
}
```

响应：
```json
{
  "code": 0,
  "data": {
    "song_id": "song-001",
    "queue_status": "waiting",
    "download_status": "pending"
  }
}
```

#### 5）获取队列
**GET** `/api/queue/list`

响应：
```json
{
  "code": 0,
  "data": {
    "current": {
      "queue_id": "q-1",
      "song_id": "song-001",
      "title": "稻香",
      "play_mode": "original",
      "queue_status": "playing"
    },
    "upcoming": [
      {
        "queue_id": "q-2",
        "song_id": "song-002",
        "title": "晴天",
        "queue_status": "waiting",
        "download_status": "success",
        "separate_status": "processing",
        "resource_level": "downloaded_only"
      }
    ]
  }
}
```

#### 6）删除队列项
**POST** `/api/queue/remove`

请求：
```json
{
  "queue_id": "q-2"
}
```

#### 7）置顶下一首
**POST** `/api/queue/move-next`

请求：
```json
{
  "queue_id": "q-2"
}
```

#### 8）切歌
**POST** `/api/player/next`

响应：
```json
{
  "code": 0,
  "message": "ok"
}
```

#### 9）切换原唱/伴唱
**POST** `/api/player/mode`

请求：
```json
{
  "mode": "accompaniment"
}
```

响应：
```json
{
  "code": 0,
  "data": {
    "mode": "accompaniment"
  }
}
```

#### 10）音量控制
**POST** `/api/player/volume`

请求：
```json
{
  "action": "set",
  "value": 65
}
```

#### 11）获取当前播放状态
**GET** `/api/player/status`

响应：
```json
{
  "code": 0,
  "data": {
    "play_status": "playing",
    "current_time": 120000,
    "duration": 240000,
    "volume": 65,
    "mode": "original",
    "current_song": {
      "song_id": "song-001",
      "title": "稻香"
    }
  }
}
```

#### 12）本地资源列表
**GET** `/api/local/list?page=1&page_size=50`

响应：
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "song_id": "song-001",
        "title": "稻香",
        "resource_level": "full",
        "download_status": "success",
        "separate_status": "success",
        "file_size": 123456789
      }
    ]
  }
}
```

#### 13）本地点歌
**POST** `/api/local/order`

请求：
```json
{
  "song_id": "song-001"
}
```

#### 14）删除本地缓存
**POST** `/api/local/delete`

请求：
```json
{
  "song_id": "song-001"
}
```

### 5.2 机顶盒调用主机接口

#### 1）创建分离任务
**POST** `/api/separate/create`

请求：
```json
{
  "song_id": "song-001",
  "file_path": "/storage/emulated/0/Android/data/xxx/files/ktv/songs/song-001/video.mp4",
  "file_format": "mp4"
}
```

如果主机不能直接访问机顶盒文件，则改成上传：
- multipart file upload

响应：
```json
{
  "code": 0,
  "data": {
    "task_id": "task-001",
    "status": "pending"
  }
}
```

#### 2）查询分离状态
**GET** `/api/separate/status?task_id=task-001`

响应：
```json
{
  "code": 0,
  "data": {
    "task_id": "task-001",
    "status": "processing",
    "progress": 80
  }
}
```

#### 3）获取分离结果
**GET** `/api/separate/result?task_id=task-001`

响应：
```json
{
  "code": 0,
  "data": {
    "task_id": "task-001",
    "status": "success",
    "accompaniment_url": "http://host/result/task-001/acc.mp3",
    "vocal_url": "http://host/result/task-001/vocal.mp3",
    "checksum": {
      "accompaniment": "md5-xxx",
      "vocal": "md5-yyy"
    }
  }
}
```

---

## 六、WebSocket 设计

### 6.1 机顶盒到手机端实时事件

建议事件：

#### `player_updated`
```json
{
  "event": "player_updated",
  "data": {
    "play_status": "playing",
    "mode": "original",
    "volume": 60,
    "current_song_id": "song-001"
  }
}
```

#### `queue_updated`
```json
{
  "event": "queue_updated",
  "data": {
    "current_song_id": "song-001",
    "queue_count": 3
  }
}
```

#### `download_updated`
```json
{
  "event": "download_updated",
  "data": {
    "song_id": "song-002",
    "download_status": "downloading",
    "progress": 45
  }
}
```

#### `separate_updated`
```json
{
  "event": "separate_updated",
  "data": {
    "song_id": "song-002",
    "status": "processing",
    "progress": 70
  }
}
```

---

## 七、数据库设计（Room 版本）

### 7.1 SongEntity

```kotlin
@Entity(tableName = "song")
data class SongEntity(
    @PrimaryKey val songId: String,
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val artist: String?,
    val duration: Long,
    val coverUrl: String?,
    val coverLocalPath: String?,
    val videoPath: String?,
    val originalAudioPath: String?,
    val accompanimentPath: String?,
    val vocalPath: String?,
    val downloadStatus: String,
    val separateStatus: String,
    val resourceLevel: String,
    val fileSize: Long,
    val createdAt: Long,
    val updatedAt: Long
)
```

### 7.2 QueueEntity

```kotlin
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val queueId: String,
    val songId: String,
    val queueStatus: String,
    val playMode: String,
    val position: Int,
    val orderedBy: String?,
    val createdAt: Long
)
```

### 7.3 SeparateTaskEntity

```kotlin
@Entity(tableName = "separate_task")
data class SeparateTaskEntity(
    @PrimaryKey val taskId: String,
    val songId: String,
    val status: String,
    val progress: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)
```

### 7.4 DeviceConfigEntity

```kotlin
@Entity(tableName = "device_config")
data class DeviceConfigEntity(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val hostAddress: String,
    val defaultPlayMode: String,
    val allowDuplicateOrder: Boolean,
    val cacheLimitMb: Long
)
```

---

## 八、本地文件存储设计

### 8.1 推荐目录

建议优先使用应用私有目录，减少权限问题：

```text
${context.getExternalFilesDir(null)}/ktv/
  songs/
    {songId}/
      meta.json
      cover.jpg
      video.mp4
      original_audio.m4a
      accompaniment.mp3
      vocal.mp3
  temp/
  logs/
```

#### 原因
1. Android 机顶盒系统版本差异大
2. 应用私有目录更稳定
3. 不容易碰到外部存储权限问题

### 8.2 文件命名建议

固定命名，不要用原始标题命名，避免特殊字符问题：

- `video.mp4`
- `original_audio.m4a`
- `accompaniment.mp3`
- `vocal.mp3`
- `cover.jpg`
- `meta.json`

目录用 `songId` 区分。

### 8.3 文件状态规则

#### 只要满足以下条件才可标记 `full`
- video 或 original_audio 存在
- accompaniment 存在
- vocal 存在
- 数据库记录完整
- 校验通过

#### `with_accompaniment`
- 原始资源存在
- accompaniment 存在
- vocal 可无

#### `downloaded_only`
- 只有原始资源存在

#### `broken`
- 数据库和文件不一致
- 文件损坏
- 校验不通过

---

## 九、安卓实现关键点

### 9.1 播放器切换模式实现建议

首期建议使用“重建播放源”的方式切换，而不是做复杂的实时混轨。

#### 原唱模式
- 播放原始视频/原始音频

#### 伴唱模式
- 如果有视频：视频画面继续，音频换为 accompaniment
- 如果首期实现复杂，允许降级为：
  - 停掉原始资源
  - 播放伴奏音频
  - UI 显示封面/背景图

#### 原因
机顶盒硬件和系统差异大，首期优先稳定。

### 9.2 下载与分离并发策略

建议：
- 同时只允许 1~2 个下载任务
- 分离任务顺序执行
- 播放优先级高于后台任务

#### 推荐策略
1. 正在播放的 UI 线程最高优先
2. 下载任务后台串行/限流
3. 分离轮询间隔 2~5 秒
4. 不要在主线程做大文件处理

### 9.3 网络断开处理

#### 手机与机顶盒断开
- 不影响当前播放
- 页面提示可重新扫码

#### 机顶盒与主机断开
- 不影响已缓存歌曲播放
- 新歌不能分离
- 若只下载到原始资源，则可原唱播放

#### 机顶盒与 B 站访问异常
- 点歌失败或下载失败
- 不应影响当前播放队列

---

## 十、开发任务拆解表

### 10.1 Android 机顶盒端任务

#### A. 基础框架
1. 建工程
2. MVVM 基础搭建
3. Room 初始化
4. 网络层封装
5. 全局错误处理

#### B. 会话与二维码
1. SessionManager
2. QRCode 生成
3. 首页二维码 UI
4. 绑定状态管理

#### C. 手机接口服务
1. 内置 HTTP Server
2. 路由注册
3. 参数校验
4. Session 鉴权
5. WebSocket 推送

#### D. 搜索与下载
1. B站搜索实现
2. 搜索结果映射
3. 点歌任务创建
4. 下载器实现
5. 下载状态同步

#### E. 分离模块
1. 主机地址配置
2. 创建分离任务
3. 状态轮询
4. 结果拉取
5. 文件落盘与校验

#### F. 播放器
1. Media3 播放器接入
2. 播放页 UI
3. 原唱/伴唱切换
4. 切歌
5. 进度同步
6. 音量控制

#### G. 队列与本地资源
1. 队列 DAO/Repository
2. 队列管理器
3. 本地资源页
4. 本地点歌
5. 删除缓存

#### H. 设置与系统能力
1. 主机配置页
2. 缓存空间显示
3. 允许重复点歌开关
4. 版本信息
5. 日志入口

### 10.2 主机端任务

1. 分离接口定义
2. 文件接收
3. 任务队列
4. 分离执行器
5. 状态查询接口
6. 结果输出接口
7. 失败重试策略
8. 日志与错误码

### 10.3 手机端 H5 任务

1. 扫码进入页
2. 绑定成功页
3. 搜索页
4. 搜索结果页
5. 当前控制页
6. 队列页
7. 本地资源页
8. WebSocket 同步

### 10.4 测试任务

#### 功能测试
1. 二维码绑定
2. 搜索
3. 点歌
4. 下载
5. 分离
6. 切歌
7. 切换模式
8. 音量控制
9. 本地点歌
10. 删除缓存

#### 异常测试
1. 搜索失败
2. 下载失败
3. 主机不可用
4. 分离失败
5. 文件损坏
6. 手机断网
7. 主机断网

#### 稳定性测试
1. 连播 20 首
2. 快速切歌
3. 快速多次点歌
4. 多次扫码绑定
5. 长时间运行不退出

---

## 十一、建议开发排期

### 第一阶段：基础架构
- Android 工程搭建
- 首页
- 二维码
- Session
- 内置 HTTP 服务

### 第二阶段：搜歌与点歌
- B站搜索
- 搜索结果
- 点歌接口
- 队列基础能力

### 第三阶段：下载与本地库
- 下载器
- 文件存储
- Room 入库
- 本地资源页

### 第四阶段：播放闭环
- Media3 播放器
- 当前播放页
- 切歌
- 音量控制

### 第五阶段：主机分离闭环
- 分离任务接口
- 分离状态同步
- 结果接收
- 原唱/伴唱切换

### 第六阶段：联调与优化
- WebSocket
- 异常处理
- 长测
- 遥控器焦点优化

---

## 十二、给安卓研发的最终落地要求

这不是一个普通“播放器 App”，安卓机顶盒端必须具备下面四种能力：

1. **局域网服务能力**  
   手机要能通过扫码访问并控制机顶盒。

2. **资源抓取与下载能力**  
   搜索和下载都在机顶盒侧，不在主机侧。

3. **本地资源管理能力**  
   原始资源、伴奏、人声都要在机顶盒本地落盘并可复用。

4. **稳定的播放器能力**  
   要能切歌、切模式、调音量，并适应安卓机顶盒遥控器操作。

---

## 十三、最终一句话技术定义

这是一个：

**运行在 Android 机顶盒上的本地局域网 KTV 系统，机顶盒负责搜 B 站、下资源、管缓存和播放，主机只负责伴奏/人声分离，手机通过扫码完成控制。**
