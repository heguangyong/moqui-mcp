// Rocket.Chat Integration Service Methods
// 为marketplace提供Rocket.Chat集成功能

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.URI

/**
 * 处理来自Rocket.Chat的Webhook消息
 * 接收用户在Rocket.Chat中发送的消息，通过MCP AI引擎处理，并发送响应
 */
Map processRocketChatWebhook() {
    logger.info("Processing Rocket.Chat webhook: ${context}")

    try {
        // 解析Rocket.Chat webhook数据
        String messageText = extractMessageText(context)
        String userId = extractUserId(context)
        String channelId = extractChannelId(context)
        String userName = extractUserName(context)

        logger.info("Processing webhook message from user ${userName} in channel ${channelId}: ${messageText}")

        // 检查是否是机器人自己的消息（避免循环）
        if (isFromBot(context)) {
            logger.debug("Ignoring message from bot itself")
            return [
                success: true,
                action: "ignored"
            ]
        }

        // 创建或获取marketplace会话
        String sessionId = getOrCreateMarketplaceSession(userId, userName)

        // 调用MCP AI引擎处理消息
        Map<String, Object> aiResponse = callMcpAiEngine(sessionId, messageText, userId)

        // 发送AI响应回Rocket.Chat
        boolean messageSent = false
        if (aiResponse.aiResponse) {
            messageSent = sendMessageToRocketChat(channelId, aiResponse.aiResponse)
        }

        return [
            success: true,
            sessionId: sessionId,
            intent: aiResponse.intent,
            action: "processed",
            messageSent: messageSent
        ]

    } catch (Exception e) {
        logger.error("Error processing Rocket.Chat webhook", e)
        return [
            success: false,
            error: e.getMessage(),
            errorType: "WEBHOOK_PROCESSING_ERROR"
        ]
    }
}

/**
 * 发送消息到Rocket.Chat频道
 * 主动向指定的Rocket.Chat频道发送消息
 */
Map sendMessageToRocketChat() {
    logger.info("Sending message to Rocket.Chat: channel=${context.channelId}, message=${context.message}")

    try {
        // 验证必需参数
        if (!context.channelId) {
            return [success: false, error: "Missing channelId parameter"]
        }
        if (!context.message) {
            return [success: false, error: "Missing message parameter"]
        }

        // 发送消息到Rocket.Chat
        boolean sent = sendMessageToRocketChatChannel(context.channelId, context.message)

        return [
            success: sent,
            channelId: context.channelId,
            message: context.message,
            sent: sent
        ]

    } catch (Exception e) {
        logger.error("Error sending message to Rocket.Chat", e)
        return [
            success: false,
            error: e.getMessage(),
            errorType: "MESSAGE_SEND_ERROR"
        ]
    }
}

/**
 * 获取Rocket.Chat集成状态
 * 检查与Rocket.Chat的连接状态和配置
 */
Map getRocketChatStatus() {
    logger.info("Checking Rocket.Chat integration status")

    try {
        // 获取配置
        String rocketchatUrl = context.ec.factory.getToolFactory("SystemBinding")
                .getSystemProperty("rocketchat.server.url", "http://localhost:4000")
        String botUsername = context.ec.factory.getToolFactory("SystemBinding")
                .getSystemProperty("rocketchat.bot.username", "marketplace-bot")

        // 简单的连接测试
        boolean configured = !rocketchatUrl.isEmpty() && !botUsername.isEmpty()

        return [
            success: true,
            configured: configured,
            rocketchatUrl: rocketchatUrl,
            botUsername: botUsername,
            status: configured ? "CONFIGURED" : "NOT_CONFIGURED"
        ]

    } catch (Exception e) {
        logger.error("Error checking Rocket.Chat status", e)
        return [
            success: false,
            error: e.getMessage(),
            status: "ERROR"
        ]
    }
}

/**
 * 通过Rocket.Chat发送marketplace通知
 * 用于发送撮合成功、新匹配等重要通知
 */
Map sendMarketplaceNotification() {
    logger.info("Sending marketplace notification via Rocket.Chat: ${context}")

    try {
        // 验证参数
        if (!context.userId) {
            return [success: false, error: "Missing userId parameter"]
        }
        if (!context.notificationType) {
            return [success: false, error: "Missing notificationType parameter"]
        }

        // 根据通知类型构建消息
        String message = buildNotificationMessage(context.notificationType, context)

        // 获取用户的Rocket.Chat频道ID (这里简化为直接使用userId)
        String channelId = getUserRocketChatChannel(context.userId)

        // 发送通知
        boolean sent = sendMessageToRocketChatChannel(channelId, message)

        // 记录通知发送
        if (sent && context.matchId) {
            recordNotificationSent(context.matchId, context.userId, "ROCKETCHAT", message)
        }

        return [
            success: sent,
            userId: context.userId,
            notificationType: context.notificationType,
            message: message,
            channelId: channelId,
            sent: sent
        ]

    } catch (Exception e) {
        logger.error("Error sending marketplace notification", e)
        return [
            success: false,
            error: e.getMessage(),
            errorType: "NOTIFICATION_SEND_ERROR"
        ]
    }
}

// === 辅助方法 ===

private String buildNotificationMessage(String notificationType, Map context) {
    switch (notificationType) {
        case "NEW_MATCH":
            return "🎯 发现新的匹配！\n\n" +
                   "商品：${context.productName ?: '未知商品'}\n" +
                   "匹配度：${context.matchScore ? (context.matchScore * 100).intValue() : 0}%\n" +
                   "查看详情：/marketplace match ${context.matchId ?: ''}"

        case "CONTACT_REQUEST":
            return "📞 有商家想要联系您！\n\n" +
                   "关于：${context.productName ?: '未知商品'}\n" +
                   "来自：${context.contactName ?: '匿名用户'}\n" +
                   "回复 /marketplace contact ${context.matchId ?: ''} 查看详情"

        case "ORDER_UPDATE":
            return "📦 订单状态更新\n\n" +
                   "订单：${context.orderId ?: '未知订单'}\n" +
                   "状态：${context.status ?: '未知状态'}\n" +
                   "查看详情：/marketplace order ${context.orderId ?: ''}"

        default:
            return "📢 Marketplace通知：${context.message ?: '您有新的通知'}"
    }
}

private String getUserRocketChatChannel(String userId) {
    // 简化实现：使用私聊频道
    // 在实际实现中，可能需要查询用户的Rocket.Chat用户名或频道偏好
    return "@" + userId
}

private void recordNotificationSent(String matchId, String userId, String channel, String message) {
    try {
        // 记录通知发送到数据库
        context.ec.service.sync().name("create#mcp.MatchNotification")
            .parameter("matchId", matchId)
            .parameter("recipientPartyId", userId)
            .parameter("notificationType", "NEW_MATCH")
            .parameter("channel", channel)
            .parameter("messageContent", message)
            .parameter("status", "SENT")
            .parameter("sentDate", context.ec.user.nowTimestamp)
            .parameter("createdDate", context.ec.user.nowTimestamp)
            .call()
    } catch (Exception e) {
        logger.warn("Failed to record notification: ${e.message}")
    }
}

// === HTTP Integration Methods ===

/**
 * 发送消息到Rocket.Chat频道的核心方法
 */
private boolean sendMessageToRocketChatChannel(String channelId, String message) {
    try {
        // 获取配置
        String rocketchatUrl = context.ec.factory.getToolFactory("SystemBinding")
                .getSystemProperty("rocketchat.server.url", "http://localhost:4000")
        String botUsername = context.ec.factory.getToolFactory("SystemBinding")
                .getSystemProperty("rocketchat.bot.username", "marketplace-bot")

        // 构建Rocket.Chat API请求
        String apiUrl = rocketchatUrl + "/api/v1/chat.postMessage"

        String jsonPayload = """{
            "channel":"${channelId}",
            "text":"${escapeJson(message)}",
            "username":"${botUsername}"
        }"""

        // 使用HttpClient发送请求
        HttpClient httpClient = HttpClient.newHttpClient()
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .header("X-Auth-Token", getBotAuthToken())
            .header("X-User-Id", getBotUserId())
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build()

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            logger.info("Message sent successfully to channel ${channelId}")
            return true
        } else {
            logger.warn("Failed to send message. Status: ${response.statusCode()}, Body: ${response.body()}")
            return false
        }

    } catch (Exception e) {
        logger.error("Error sending message to Rocket.Chat", e)
        return false
    }
}

/**
 * 创建或获取marketplace会话
 */
private String getOrCreateMarketplaceSession(String userId, String userName) {
    try {
        // 尝试查找现有会话
        Map<String, Object> findResult = context.ec.service.sync().name("moqui.mcp.McpServices.find#DialogSession")
            .parameter("externalUserId", userId)
            .parameter("sessionType", "MARKETPLACE")
            .call()

        if (findResult.session) {
            Map<String, Object> session = findResult.session
            return session.sessionId
        }

        // 创建新会话
        Map<String, Object> createResult = context.ec.service.sync().name("moqui.mcp.create#MarketplaceSession")
            .parameter("merchantId", userId)
            .parameter("initialContext", "Rocket.Chat User: " + userName)
            .call()

        return createResult.sessionId

    } catch (Exception e) {
        logger.error("Error managing marketplace session for user ${userId}", e)
        return "session_${userId}_${System.currentTimeMillis()}"
    }
}

/**
 * 调用MCP AI引擎处理消息
 */
private Map<String, Object> callMcpAiEngine(String sessionId, String message, String merchantId) {
    try {
        return context.ec.service.sync().name("moqui.mcp.chat#Message")
            .parameter("sessionId", sessionId)
            .parameter("message", message)
            .parameter("merchantId", merchantId)
            .call()
    } catch (Exception e) {
        logger.error("Error calling MCP AI engine", e)
        return [
            aiResponse: "抱歉，AI服务暂时不可用，请稍后再试。",
            success: false
        ]
    }
}

// === Helper Methods ===

private String extractMessageText(Map<String, Object> webhookData) {
    return webhookData.text ?: ""
}

private String extractUserId(Map<String, Object> webhookData) {
    Map<String, Object> user = webhookData.user
    return user?._id ?: "unknown"
}

private String extractChannelId(Map<String, Object> webhookData) {
    Map<String, Object> channel = webhookData.channel
    return channel?._id ?: "unknown"
}

private String extractUserName(Map<String, Object> webhookData) {
    Map<String, Object> user = webhookData.user
    return user?.username ?: "unknown"
}

private boolean isFromBot(Map<String, Object> webhookData) {
    String userName = extractUserName(webhookData)
    String botUsername = context.ec.factory.getToolFactory("SystemBinding")
            .getSystemProperty("rocketchat.bot.username", "marketplace-bot")
    return botUsername.equals(userName)
}

private String escapeJson(String text) {
    return text?.replace("\"", "\\\"")?.replace("\n", "\\n")?.replace("\r", "\\r") ?: ""
}

private String getBotAuthToken() {
    // TODO: 实现Rocket.Chat认证token获取
    return context.ec.factory.getToolFactory("SystemBinding")
            .getSystemProperty("rocketchat.bot.token", "")
}

private String getBotUserId() {
    // TODO: 实现Rocket.Chat Bot用户ID获取
    return context.ec.factory.getToolFactory("SystemBinding")
            .getSystemProperty("rocketchat.bot.userid", "")
}