# Marketplace MCP Integration Guide

## 🎯 概述

本文档介绍如何使用增强版的MCP (Model Context Protocol) 组件为moqui-marketplace提供AI驱动的对话式商业撮合功能。

## 🏗️ 架构图

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│   商家用户       │    │  MCP AI Agent    │    │  Marketplace Engine │
│   对话界面       │◄──►│  (Claude API)    │◄──►│  (智能匹配引擎)      │
└─────────────────┘    └──────────────────┘    └─────────────────────┘
                              │
                              ▼
                       ┌──────────────────┐
                       │  Moqui Framework │
                       │  (REST API)      │
                       └──────────────────┘
```

## 🚀 快速开始

### 1. 前置要求

- ✅ Moqui Framework (已升级到JDK 21)
- ✅ moqui-marketplace组件 (已实现)
- ✅ moqui-mcp组件 (已增强)
- 🔑 Claude API密钥 (需要配置)

### 2. 配置Claude API

```bash
# 设置Claude API密钥
export ANTHROPIC_API_KEY="your_claude_api_key_here"

# 或者在MoquiConf.xml中配置
<default-property name="anthropic.api.key" value="your_api_key"/>
```

### 3. 启动服务

```bash
# 确保所有组件都已构建
./gradlew :runtime:component:moqui-marketplace:build
./gradlew :runtime:component:moqui-mcp:build

# 启动Moqui
./gradlew run
```

### 4. API端点测试

所有API端点都在 `/rest/s1/mcp/` 路径下：

#### 创建会话
```bash
POST /rest/s1/mcp/marketplace-session
{
  "merchantId": "MERCHANT_001"
}
```

#### 发送消息
```bash
POST /rest/s1/mcp/marketplace-chat
{
  "sessionId": "session_id",
  "merchantId": "MERCHANT_001",
  "message": "我想发布20斤新鲜菠菜，3-5元一斤"
}
```

## 🤖 AI对话能力

### 智能意图识别

MCP系统能够自动识别用户意图：

| 用户表达 | 识别意图 | 系统行为 |
|---------|---------|---------|
| "我想发布供应信息" | PUBLISH_SUPPLY | 引导收集商品信息并创建listing |
| "需要购买蔬菜" | PUBLISH_DEMAND | 帮助发布需求并匹配供应商 |
| "搜索菠菜" | SEARCH_LISTINGS | 执行智能搜索并展示结果 |
| "推荐匹配" | VIEW_MATCHES | 使用AI匹配引擎推荐最佳匹配 |

### 对话流程示例

```
👨‍💼 商家: "我有20斤新鲜菠菜想卖"

🤖 AI: "好的！我帮您发布供应信息。请问：
       1. 价格大概多少？
       2. 品质等级如何？
       3. 配送范围多远？"

👨‍💼 商家: "3-5元一斤，A级新鲜，3公里内可配送"

🤖 AI: "✅ 供应信息已发布！
       找到2个匹配买家：

       🎯 张女士 (匹配度92%)
       - 需要: 15斤菠菜
       - 距离: 1.2公里
       - 信用: ⭐⭐⭐⭐⭐

       📞 [授权联系] [查看详情]"
```

## 🧠 AI功能特性

### 1. 智能信息提取
- 从自然语言中提取商品名称、数量、价格、品质等
- 支持模糊表达和方言

### 2. 实时匹配推荐
- 集成SmartMatchingEngine
- 多维度评分：标签相似度、地理位置、价格匹配、时效性、用户偏好

### 3. 个性化对话
- 基于商家历史行为定制对话策略
- 学习用户偏好和沟通风格

### 4. 智能决策支持
- 市场趋势分析
- 定价建议
- 最佳发布时机推荐

## 📊 数据模型

### 会话管理
```sql
McpDialogSession {
  sessionId: String (PK)
  merchantId: String (FK)
  sessionType: "MARKETPLACE"
  lastListingId: String (FK)
  preferredCategories: String
  createdDate: Timestamp
}
```

### 消息记录
```sql
McpDialogMessage {
  messageId: String (PK)
  sessionId: String (FK)
  messageType: String (Intent)
  content: String (用户消息)
  aiResponse: String (AI回复)
  processedDate: Timestamp
}
```

## 🔧 高级配置

### 自定义提示词

在`MarketplaceMcpService.java`中修改`buildMarketplacePrompt`方法：

```java
private String buildMarketplacePrompt(String userMessage, String context, String intent) {
    // 根据业务需求定制提示词
    StringBuilder prompt = new StringBuilder();
    prompt.append("你是专业的农贸市场AI助手...");
    // 添加特定领域知识
    // 添加个性化指令
    return prompt.toString();
}
```

### 匹配算法调优

调整`SmartMatchingEngine`中的权重配置：

```java
// 权重配置 (总和 = 1.0)
private static final BigDecimal WEIGHT_TAG_SIMILARITY = new BigDecimal("0.35");
private static final BigDecimal WEIGHT_GEO_PROXIMITY = new BigDecimal("0.25");
private static final BigDecimal WEIGHT_PRICE_MATCH = new BigDecimal("0.15");
private static final BigDecimal WEIGHT_FRESHNESS = new BigDecimal("0.10");
private static final BigDecimal WEIGHT_PREFERENCE = new BigDecimal("0.15");
```

## 🧪 测试验证

### 自动化测试
```bash
# 运行完整集成测试
./runtime/component/moqui-mcp/test_marketplace_mcp.sh
```

### 手动测试场景

1. **发布供应信息测试**
   - 输入："我想卖20斤新鲜白菜"
   - 期望：系统引导收集完整信息并创建listing

2. **匹配推荐测试**
   - 输入："帮我找找有谁需要蔬菜"
   - 期望：返回智能匹配的买家列表

3. **搜索功能测试**
   - 输入："搜索附近的肉类供应"
   - 期望：基于地理位置和类别的精准搜索

## 🚧 已知限制与改进计划

### 当前限制
- NLP信息提取较为简单（基于关键词匹配）
- Claude API调用需要网络连接
- 暂不支持图片识别

### 改进计划
- 🔄 集成更高级的NLP模型
- 🔄 添加图片识别功能（商品照片分析）
- 🔄 支持语音对话
- 🔄 多语言支持

## 📞 技术支持

如有技术问题，请查看：
1. `logs/moqui.log` - 系统日志
2. `MarketplaceMcpService.java:logger` - MCP专用日志
3. REST API响应中的error字段

## 🎉 成功案例

### 典型使用场景

**场景1: 菜农快速发布**
- 用户："今天摘了50斤番茄，新鲜便宜卖"
- 系统：自动识别商品、引导定价、匹配附近买家、推送通知

**场景2: 餐厅采购需求**
- 用户："需要20斤土豆，要求无农药，明天要用"
- 系统：发布需求、智能匹配、筛选优质供应商、协助交易

**场景3: 市场行情咨询**
- 用户："最近蔬菜价格怎么样？"
- 系统：分析历史数据、预测趋势、给出建议

---

*本文档持续更新中，最新版本请查看项目代码注释*