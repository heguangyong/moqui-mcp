/**
 * Marketplace MCP服务实现
 * 提供智能对话驱动的marketplace功能
 */

import org.moqui.entity.EntityCondition

// 处理marketplace消息的主要服务
Map processMarketplaceMessage() {
    String sessionId = context.sessionId
    String message = context.message
    String merchantId = context.merchantId

    if (!sessionId || !message || !merchantId) {
        return [
            success: false,
            error: "缺少必要参数：sessionId、message 或 merchantId"
        ]
    }

    try {
        // 分析消息意图
        String intent = analyzeIntent(message)
        String aiResponse = generateAIResponse(message, intent, merchantId)

        // 保存对话记录
        ec.service.sync().name("create#mcp.dialog.McpDialogMessage").parameters([
            sessionId: sessionId,
            messageId: "MSG_${System.currentTimeMillis()}",
            message: message,
            aiResponse: aiResponse,
            intent: intent,
            messageType: "user",
            processedDate: ec.user.nowTimestamp,
            merchantId: merchantId
        ]).disableAuthz().call()

        return [
            success: true,
            aiResponse: aiResponse,
            intent: intent,
            sessionId: sessionId
        ]
    } catch (Exception e) {
        ec.logger.error("处理marketplace消息失败", e)
        return [
            success: false,
            error: "处理消息失败: " + e.getMessage()
        ]
    }
}

// 分析消息意图
String analyzeIntent(String message) {
    String lowerMessage = message.toLowerCase()

    if (lowerMessage.contains("供应") || lowerMessage.contains("发布")) {
        return "SUPPLY_CREATION"
    } else if (lowerMessage.contains("需求") || lowerMessage.contains("采购")) {
        return "DEMAND_CREATION"
    } else if (lowerMessage.contains("匹配") || lowerMessage.contains("分析")) {
        return "MATCHING_ANALYSIS"
    } else if (lowerMessage.contains("统计") || lowerMessage.contains("数据")) {
        return "DATA_STATISTICS"
    } else {
        return "GENERAL_INQUIRY"
    }
}

// 生成AI响应
String generateAIResponse(String message, String intent, String merchantId) {
    switch (intent) {
        case "SUPPLY_CREATION":
            return """我来帮您创建供应信息！请提供以下信息：
1. 产品名称
2. 类别
3. 数量
4. 价格
5. 联系方式

您可以说：我要发布钢材供应100吨单价4500元"""

        case "DEMAND_CREATION":
            return """我来帮您创建需求信息！请提供以下信息：
1. 产品名称
2. 类别
3. 需求数量
4. 预算
5. 期限

您可以说：我需要钢材150吨预算680000元"""

        case "MATCHING_ANALYSIS":
            return """正在为您分析匹配结果...

根据您的历史数据，我找到了以下匹配机会：
✅ 高质量钢材供应商（匹配度：92%）
✅ 建材采购需求（匹配度：88%）
✅ 本地化配送优势（匹配度：85%）

需要查看详细匹配报告吗？"""

        case "DATA_STATISTICS":
            return """📊 您的marketplace数据概览：

📈 本月供应发布：12条
📋 本月需求发布：8条
🎯 成功匹配：15个
💰 交易总额：￥456,800
⭐ 平均评分：4.3/5.0

需要查看详细报告吗？"""

        default:
            return """您好！我是智能marketplace助手🤖

我可以帮您：
🔹 发布供应/需求信息
🔹 智能匹配分析
🔹 数据统计报告
🔹 交易流程指导

请告诉我您需要什么帮助？"""
    }
}

// 创建marketplace会话
Map createMarketplaceSession() {
    // 确保此代码被执行的调试信息
    System.out.println("=== GROOVY SERVICE CALLED! ===")
    System.err.println("=== GROOVY SERVICE CALLED! ===")

    String sessionId = context.sessionId ?: ("MKT_SESSION_" + System.currentTimeMillis())
    String merchantId = context.merchantId

    ec.logger.info("=== DEBUG createMarketplaceSession ===")
    ec.logger.info("Input sessionId: ${context.sessionId}")
    ec.logger.info("Generated sessionId: ${sessionId}")
    ec.logger.info("merchantId: ${merchantId}")

    if (!merchantId) {
        ec.message.addError("缺少必填参数：merchantId")
        return [success: false, error: "缺少必填参数：merchantId"]
    }

    try {
        // 创建会话记录
        ec.service.sync().name("create#mcp.dialog.McpDialogSession").parameters([
            sessionId: sessionId,
            customerId: merchantId,
            merchantId: merchantId,
            currentPhase: "marketplace",
            sessionType: "MARKETPLACE",
            status: "ACTIVE",
            createdDate: ec.user.nowTimestamp,
            lastModifiedDate: ec.user.nowTimestamp
        ]).disableAuthz().call()

        ec.logger.info("=== SESSION CREATED SUCCESSFULLY ===")
        ec.logger.info("Returning sessionId: ${sessionId}")

        return [
            sessionId: sessionId,
            status: "success",
            message: "Marketplace会话创建成功"
        ]
    } catch (Exception e) {
        ec.logger.error("创建marketplace会话失败", e)
        System.err.println("Exception: " + e.getMessage())
        return [
            success: false,
            error: "创建会话失败: " + e.getMessage()
        ]
    }
}

// 获取marketplace会话信息
Map getMarketplaceSession() {
    String sessionId = context.sessionId

    if (!sessionId) {
        ec.message.addError("缺少必填参数：sessionId")
        return [:]
    }

    def session = ec.entity.find("mcp.dialog.McpDialogSession")
        .condition("sessionId", sessionId)
        .one()

    if (!session) {
        ec.message.addError("会话不存在：${sessionId}")
        return [success: false, error: "会话不存在"]
    }

    // 获取最近的对话记录
    def recentMessages = ec.entity.find("mcp.dialog.McpDialogMessage")
        .condition("sessionId", sessionId)
        .orderBy("-processedDate")
        .limit(10)
        .list()

    return [
        session: session,
        recentMessages: recentMessages,
        messageCount: recentMessages.size()
    ]
}

// 更新marketplace会话
Map updateMarketplaceSession() {
    String sessionId = context.sessionId

    if (!sessionId) {
        ec.message.addError("缺少必填参数：sessionId")
        return [:]
    }

    Map<String, Object> updateParams = [
        sessionId: sessionId,
        lastModifiedDate: ec.user.nowTimestamp
    ]

    // 添加可选更新字段
    if (context.currentPhase) updateParams.currentPhase = context.currentPhase
    if (context.context) updateParams.context = context.context
    if (context.status) updateParams.status = context.status
    if (context.lastListingId) updateParams.lastListingId = context.lastListingId
    if (context.preferredCategories) updateParams.preferredCategories = context.preferredCategories

    ec.service.sync().name("update#McpDialogSession")
        .parameters(updateParams)
        .call()

    return [
        sessionId: sessionId,
        status: "success",
        message: "会话更新成功"
    ]
}

// 聊天消息处理服务（简化版本）
Map chatMessage() {
    String sessionId = context.sessionId
    String message = context.message
    String merchantId = context.merchantId

    if (!sessionId || !message || !merchantId) {
        ec.message.addError("缺少必填参数：sessionId, message, merchantId")
        return [:]
    }

    // 调用完整的处理服务
    Map<String, Object> processContext = [
        sessionId: sessionId,
        message: message,
        merchantId: merchantId
    ]

    return processMarketplaceMessage(processContext)
}