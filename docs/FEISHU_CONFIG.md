# 飞书机器人配置指南

## 快速开始

### 步骤 1: 创建飞书应用

1. 打开 [飞书开放平台](https://open.feishu.cn/)
2. 使用企业账号登录
3. 进入「应用开发」→「创建应用」
4. 选择「企业自建应用」
5. 填写应用信息：
   - 应用名称：`DragonAgent`
   - 应用描述：`AI 助手机器人`

### 步骤 2: 获取应用凭证

创建成功后，在应用详情页获取：

| 凭证 | 说明 | 获取位置 |
|------|------|----------|
| App ID | 应用唯一标识 | 应用凭证页面 |
| App Secret | 应用密钥 | 应用凭证页面 |
| Verification Token | 回调验证 Token | 事件订阅配置页面 |

### 步骤 3: 配置应用权限

进入「权限管理」，搜索并添加以下权限：

```
✅ im:message:send_as_bot        - 发送消息
✅ im:message:receive            - 接收消息  
✅ im:message:read              - 读取消息
✅ im:chat:readonly             - 读取群信息
✅ contact:user.base:readonly   - 读取用户基本信息
```

### 步骤 4: 配置事件订阅

1. 进入「事件订阅」
2. 点击「创建订阅」
3. 配置回调 URL（需公网可访问）
4. 订阅以下事件：
   - `im.message.receive_v1` - 接收消息
   - `im.chat.member.bot_added_v1` - 机器人被添加到群

5. 填写验证信息：
   - Verification Token: 自行设置一个 token

### 步骤 5: 发布应用

1. 进入「版本管理与发布」
2. 创建新版本（如 `v1.0.0`）
3. 提交审核
4. 发布应用

---

## DragonAgent 配置

### 配置格式

在 DragonAgent 设置页面，飞书配置填写格式：

```
AppID|AppSecret|VerificationToken
```

示例：
```
cli_a1b2c3d4e5f6g7h8|xxxxxxxxxxxxxxxxxxxx|xxxxxxxxxxxxxxxxxxxx
```

### 配置位置

```
设置 → 渠道配置 → 飞书 → 输入上述格式的凭证
```

---

## 常见问题

### Q: 回调 URL 如何配置？

需要公网可访问的 URL。可使用：
- 云服务器
- 内网穿透（如 ngrok、frp）

本地测试示例（ngrok）：
```bash
ngrok http 8080
# 得到 https://xxx.ngrok.io
# 回调 URL 填写: https://xxx.ngrok.io/callback/feishu
```

### Q: 收不到消息？

检查：
1. ✅ 应用是否已发布
2. ✅ 权限是否添加完整
3. ✅ 回调 URL 是否可访问
4. ✅ 事件是否订阅成功

### Q: 如何实现@机器人回复？

在群聊中，用户发送消息时需要 @机器人。代码中已处理：
- 检测消息内容是否包含 `@机器人`
- 包含时才触发 AI 回复

### Q: 如何开启主动消息？

需要额外权限 `im:message:send_as_bot`，并在应用配置中开启「机器人主动发消息」能力。

---

## API 限流参考

| 接口 | 限频 |
|------|------|
| 发送消息 | 50次/分钟/应用 |
| 获取 access_token | 5次/分钟/应用 |
| 获取用户信息 | 100次/分钟/应用 |

---

## 安全注意事项

1. **App Secret** 不要泄露到客户端
2. **Verification Token** 每次回调都需要验证
3. 建议使用签名验证消息真实性
4. 敏感操作需要配置 IP 白名单

---
