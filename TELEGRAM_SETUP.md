# Telegram Bot 设置指南

## 🤖 Telegram智能供需助手配置完全指南

### 📋 当前状态
✅ **Telegram Webhook 已完全实现** - 系统能够接收和处理Telegram消息
✅ **智能响应系统已完成** - 本地AI响应完全可用
✅ **消息发送机制已实现** - sendTelegramMessage函数正常工作
⚠️ **需要配置** - Bot Token 和正确的Webhook设置

### 🛠️ 设置步骤

#### 1. 创建Telegram Bot
1. **联系 @BotFather**
   - 在Telegram中搜索 `@BotFather`
   - 发送 `/newbot` 命令
   - 按提示设置Bot名称和用户名
   - 获取Bot Token（格式：`123456789:ABCDEF...`）

2. **配置Bot基本信息**
   ```
   /setdescription - 智能供需平台助手，帮助您发布供应需求、智能匹配和数据分析
   /setabouttext - 专业的B2B供需匹配平台，使用AI技术为您提供精准的商机匹配服务
   /setuserpic - 上传Bot头像
   ```

#### 2. 配置Moqui系统

**方式1: 环境变量（推荐）**
```bash
export TELEGRAM_BOT_TOKEN="你的Bot Token"
```

**方式2: 系统属性**
```bash
# 启动时添加参数
-Dtelegram.bot.token=你的Bot Token
```

**方式3: 配置文件**
在 `runtime/conf/MoquiDevConf.xml` 中添加：
```xml
<moqui-conf>
    <default-property name="telegram.bot.token" value="你的Bot Token"/>
</moqui-conf>
```

#### 3. 设置Webhook
**重要**: 您需要将您的webhook URL告诉Telegram。

**如果您有公网域名:**
```bash
curl -X POST "https://api.telegram.org/bot你的Bot Token/setWebhook" \
     -H "Content-Type: application/json" \
     -d '{"url": "https://你的域名/rest/s1/mcp/telegram"}'
```

**如果使用ngrok进行本地测试:**
```bash
# 1. 安装ngrok
# 2. 启动ngrok
ngrok http 8080

# 3. 使用ngrok提供的URL设置webhook
curl -X POST "https://api.telegram.org/bot你的Bot Token/setWebhook" \
     -H "Content-Type: application/json" \
     -d '{"url": "https://你的ngrok域名.ngrok.io/rest/s1/mcp/telegram"}'
```

#### 4. 验证设置
```bash
# 检查webhook状态
curl "https://api.telegram.org/bot你的Bot Token/getWebhookInfo"

# 应该返回类似这样的信息:
{
  "ok": true,
  "result": {
    "url": "https://你的域名/rest/s1/mcp/telegram",
    "has_custom_certificate": false,
    "pending_update_count": 0
  }
}
```

### 🎯 使用指南

#### 支持的命令和功能
1. **启动Bot** - `/start`
2. **供应信息** - "我要供应100吨钢材"
3. **需求信息** - "我需要采购大米"
4. **智能匹配** - "帮我匹配"
5. **一般咨询** - "你好"、"帮助"

#### 示例对话
```
用户: /start
Bot: 👋 欢迎使用智能供需平台！我可以帮您：
🔹 发布供应信息（说'我要供应...'）
🔹 发布采购需求（说'我要采购...'）
🔹 智能匹配分析（说'帮我匹配'）
🔹 查看统计数据（说'查看数据'）
💬 请直接告诉我您的需求，我会智能为您处理！

用户: 我要供应大米100吨
Bot: 我来帮您发布供应信息！请提供以下详细信息：
📦 产品名称：
📊 数量：
💰 价格：
📍 地区：
📞 联系方式：
例如：我要发布钢材供应，100吨，单价4500元/吨，北京地区
```

### 🔧 故障排除

#### 常见问题

**1. "Chat not found" 错误**
- 确保用户已经与Bot开始对话（发送过/start）
- 验证Chat ID是否正确

**2. "Telegram Bot Token未配置" 警告**
- 检查环境变量或配置文件是否正确设置
- 重启Moqui服务以加载新配置

**3. "Webhook验证失败"**
- 确保您的服务器可以从互联网访问
- 检查SSL证书是否有效（Telegram要求HTTPS）
- 验证webhook URL是否正确

**4. "消息不响应"**
- 检查服务器日志：`tail -f runtime/log/moqui.log | grep Telegram`
- 验证webhook是否正确设置：`curl "https://api.telegram.org/bot你的Token/getWebhookInfo"`

#### 调试命令
```bash
# 查看Telegram相关日志
tail -f runtime/log/moqui.log | grep -i telegram

# 测试webhook端点
curl -X POST "http://localhost:8080/rest/s1/mcp/telegram" \
     -H "Content-Type: application/json" \
     -d '{"update": {"message": {"chat": {"id": 123}, "text": "/start"}}}'

# 手动发送测试消息
curl -X POST "https://api.telegram.org/bot你的Token/sendMessage" \
     -H "Content-Type: application/json" \
     -d '{"chat_id": "你的Chat ID", "text": "测试消息"}'
```

### 📊 系统架构

```
Telegram用户 → Telegram服务器 → Webhook → Moqui系统 → MarketplaceMcpService → 智能响应 → Telegram用户
```

**技术栈:**
- **接收**: Moqui REST API (`/rest/s1/mcp/telegram`)
- **处理**: TelegramServices.groovy
- **智能响应**: MarketplaceMcpService + 本地AI响应
- **发送**: Telegram Bot API

### 🎉 完成验证

当一切配置正确后，您应该能够：
1. 在Telegram中找到您的Bot
2. 发送 `/start` 并收到中文欢迎消息
3. 发送业务消息（如"我要采购钢材"）并收到专业回复
4. 在Moqui日志中看到 "Telegram消息发送成功" 的信息

---

**技术支持**: 如有问题，请检查 `runtime/log/moqui.log` 中的Telegram相关日志信息。