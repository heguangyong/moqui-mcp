/*
 * Telegram Webhook Handler for Intelligent Supply-Demand Platform
 *
 * This script processes incoming Telegram messages and integrates with
 * the MarketplaceMcpService for AI-powered supply and demand matching.
 */

import groovy.json.JsonOutput
import org.moqui.mcp.MarketplaceMcpService
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

// Initialize HTTP client for Telegram API calls
HttpClient telegramHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

ec.logger.info("=== TELEGRAM WEBHOOK PROCESSING STARTED ===")
ec.logger.info("Request parameters: update=${context.update}, message=${context.message}")

try {
    // Parse Telegram message data
    Map update = context.update instanceof Map ? (Map) context.update : [:]
    Map telegramMessage = context.message instanceof Map ? (Map) context.message :
            (update.message instanceof Map ? (Map) update.message : [:])

    if (!telegramMessage || telegramMessage.isEmpty()) {
        context.success = false
        context.error = "Invalid Telegram message payload"
        context.response = [ok: false]
        ec.logger.warn("Telegram webhook missing message field: ${update}")
        return
    }

    String chatId = telegramMessage.chat?.id?.toString()
    if (!chatId) {
        context.success = false
        context.error = "Cannot parse chat ID"
        context.response = [ok: false]
        ec.logger.warn("Telegram message missing chat.id: ${telegramMessage}")
        return
    }

    // Handle different message types
    String incomingText = ""
    String messageType = "text"
    Map attachmentInfo = [:]

    // Check for voice messages
    if (telegramMessage.voice) {
        messageType = "voice"
        attachmentInfo = [
            type: "voice",
            fileId: telegramMessage.voice.file_id,
            duration: telegramMessage.voice.duration,
            mimeType: telegramMessage.voice.mime_type ?: "audio/ogg"
        ]
        incomingText = "[Voice Message - Duration: ${telegramMessage.voice.duration}s]"
        ec.logger.info("Received voice message: fileId=${telegramMessage.voice.file_id}, duration=${telegramMessage.voice.duration}s")
    }
    // Check for audio messages
    else if (telegramMessage.audio) {
        messageType = "audio"
        attachmentInfo = [
            type: "audio",
            fileId: telegramMessage.audio.file_id,
            duration: telegramMessage.audio.duration,
            mimeType: telegramMessage.audio.mime_type ?: "audio/mpeg",
            title: telegramMessage.audio.title,
            performer: telegramMessage.audio.performer
        ]
        incomingText = "[Audio Message - ${telegramMessage.audio.title ?: 'Unknown'} by ${telegramMessage.audio.performer ?: 'Unknown'}]"
        ec.logger.info("Received audio message: fileId=${telegramMessage.audio.file_id}")
    }
    // Check for photos
    else if (telegramMessage.photo) {
        messageType = "photo"
        // Get the largest photo size
        def largestPhoto = telegramMessage.photo.max { it.file_size ?: 0 }
        attachmentInfo = [
            type: "photo",
            fileId: largestPhoto.file_id,
            width: largestPhoto.width,
            height: largestPhoto.height,
            fileSize: largestPhoto.file_size
        ]
        incomingText = telegramMessage.caption ?: "[Photo Message]"
        ec.logger.info("Received photo message: fileId=${largestPhoto.file_id}, size=${largestPhoto.width}x${largestPhoto.height}")
    }
    // Check for documents
    else if (telegramMessage.document) {
        messageType = "document"
        attachmentInfo = [
            type: "document",
            fileId: telegramMessage.document.file_id,
            fileName: telegramMessage.document.file_name,
            mimeType: telegramMessage.document.mime_type,
            fileSize: telegramMessage.document.file_size
        ]
        incomingText = telegramMessage.caption ?: "[Document: ${telegramMessage.document.file_name ?: 'Unknown'}]"
        ec.logger.info("Received document: fileId=${telegramMessage.document.file_id}, fileName=${telegramMessage.document.file_name}")
    }
    // Default to text message
    else {
        incomingText = telegramMessage.text ?: telegramMessage.caption ?: ""
    }

    incomingText = incomingText?.trim()
    if (!incomingText) {
        incomingText = "/start"
    }

    String merchantId = telegramMessage.from?.id?.toString()
    if (!merchantId) {
        merchantId = "telegram_${chatId}"
    }
    String sessionId = "telegram_${chatId}"

    // Ensure the merchantId Party exists, create if not exists
    try {
        def existingParty = ec.entity.find("mantle.party.Party")
            .condition("partyId", merchantId)
            .disableAuthz()
            .one()

        if (!existingParty) {
            ec.logger.info("Creating Party for Telegram user: ${merchantId}")
            ec.service.sync().name("create#mantle.party.Party")
                .parameters([
                    partyId: merchantId,
                    partyTypeEnumId: "PtyPerson",
                    disabled: "N"
                ])
                .disableAuthz()
                .call()
            ec.logger.info("Successfully created Party: ${merchantId}")
        }
    } catch (Exception e) {
        ec.logger.warn("Failed to create Party for ${merchantId}: ${e.message}")
        // Continue processing even if Party creation fails
    }

    // Handle /start command
    if (incomingText.equalsIgnoreCase("/start")) {
        // Process through MCP service to get localized response
        MarketplaceMcpService marketplaceService = new MarketplaceMcpService(ec)
        Map<String, Object> result

        try {
            result = marketplaceService.processMarketplaceMessage([
                    sessionId : sessionId,
                    message   : "帮助",
                    merchantId: merchantId
            ])
        } catch (Exception e) {
            ec.logger.error("Failed to process /start command", e)
            result = [
                    success: true,
                    aiResponse: "👋 欢迎使用智能供需平台！\n\n我可以帮您：\n🔹 发布供应信息\n🔹 发布采购需求\n🔹 智能匹配分析\n🔹 查看统计数据\n\n请告诉我您需要什么帮助？",
                    intent: "welcome"
            ]
        }

        String aiResponse = (result.aiResponse ?: "欢迎使用智能供需平台！").toString()

        context.success = true
        context.aiResponse = aiResponse
        context.chatId = chatId
        context.intent = result.intent ?: "welcome"
        context.matches = result.matches ?: []
        context.response = [ok: true]

        // Send Telegram message
        sendTelegramMessage(chatId, aiResponse, telegramHttpClient, ec)

        ec.logger.info("Telegram welcome message sent to chat: ${chatId}")
        return
    }

    // Handle voice and image messages before marketplace processing
    if (messageType == "voice" || messageType == "audio") {
        Map voiceResult = processVoiceMessage(attachmentInfo, telegramHttpClient, ec)
        String aiResponse = voiceResult.message

        // Send response
        sendTelegramMessage(chatId, aiResponse, telegramHttpClient, ec)

        context.success = voiceResult.success
        context.aiResponse = aiResponse
        context.chatId = chatId
        context.intent = "voice_message"
        context.messageType = messageType
        context.attachmentInfo = attachmentInfo
        context.response = [ok: true]

        ec.logger.info("Voice message processed for chat: ${chatId}")
        return
    }

    if (messageType == "photo") {
        Map imageResult = processImageMessage(attachmentInfo, incomingText, telegramHttpClient, ec)
        String aiResponse = imageResult.message

        // Send response
        sendTelegramMessage(chatId, aiResponse, telegramHttpClient, ec)

        context.success = imageResult.success
        context.aiResponse = aiResponse
        context.chatId = chatId
        context.intent = "image_message"
        context.messageType = messageType
        context.attachmentInfo = attachmentInfo
        context.response = [ok: true]

        ec.logger.info("Image message processed for chat: ${chatId}")
        return
    }

    // Process marketplace message through MCP service
    MarketplaceMcpService marketplaceService = new MarketplaceMcpService(ec)
    Map<String, Object> result

    try {
        result = marketplaceService.processMarketplaceMessage([
                sessionId : sessionId,
                message   : incomingText,
                merchantId: merchantId,
                messageType: messageType,
                attachmentInfo: attachmentInfo
        ])
    } catch (Exception e) {
        ec.logger.error("Failed to process marketplace message", e)
        result = [
                success: false,
                aiResponse: null,
                error: "Error processing supply-demand information, please try again later."
        ]
    }

    String aiResponse = (result.aiResponse ?: result.error ?: "Sorry, the system cannot process your request at the moment, please try again later.").toString()

    // Send Telegram message
    sendTelegramMessage(chatId, aiResponse, telegramHttpClient, ec)

    boolean success = result.success != false
    context.success = success
    context.aiResponse = aiResponse
    context.chatId = chatId
    context.intent = result.intent
    context.matches = result.matches ?: []
    context.response = [ok: true]

    if (!success && result.error) {
        context.error = result.error
    }

    ec.logger.info("Telegram message processed successfully, chat: ${chatId}, response length: ${aiResponse?.length()}")

} catch (Exception e) {
    ec.logger.error("Script execution error", e)
    context.success = false
    context.error = "Script execution failed: ${e.message}"
    context.response = [ok: false]
}

// Helper function for sending Telegram messages
void sendTelegramMessage(String chatId, String messageText, HttpClient httpClient, def executionContext) {
    try {
        // 获取Telegram Bot Token
        String botToken = System.getProperty("telegram.bot.token") ?:
                         System.getenv("TELEGRAM_BOT_TOKEN") ?:
                         executionContext.ecfi.getConfValue("telegram.bot.token")

        if (!botToken || botToken.isEmpty()) {
            executionContext.logger.warn("Telegram Bot Token未配置，无法发送消息。仅记录日志: ${messageText}")
            executionContext.logger.info("Telegram message to chat ${chatId}: ${messageText}")
            return
        }

        // 构建Telegram API URL
        String telegramApiUrl = "https://api.telegram.org/bot${botToken}/sendMessage"

        // 准备请求数据
        Map<String, Object> requestData = [
            chat_id: chatId,
            text: messageText,
            parse_mode: "Markdown"
        ]

        String requestBody = groovy.json.JsonOutput.toJson(requestData)

        // 发送HTTP请求
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(telegramApiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .timeout(Duration.ofSeconds(30))
            .build()

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
            executionContext.logger.info("Telegram消息发送成功到聊天 ${chatId}")
        } else {
            executionContext.logger.warn("Telegram消息发送失败: HTTP ${response.statusCode()}, 响应: ${response.body()}")
        }

    } catch (Exception e) {
        executionContext.logger.error("发送Telegram消息时出错: ${e.message}", e)
        // 降级到仅日志记录
        executionContext.logger.info("Telegram message to chat ${chatId}: ${messageText}")
    }
}

// Helper function for downloading Telegram files
String downloadTelegramFile(String fileId, HttpClient httpClient, def executionContext) {
    try {
        String botToken = System.getProperty("telegram.bot.token") ?:
                         System.getenv("TELEGRAM_BOT_TOKEN") ?:
                         executionContext.ecfi.getConfValue("telegram.bot.token")

        if (!botToken || botToken.isEmpty()) {
            executionContext.logger.warn("Telegram Bot Token not configured, cannot download file")
            return null
        }

        // First, get file path
        String getFileUrl = "https://api.telegram.org/bot${botToken}/getFile?file_id=${fileId}"

        HttpRequest getFileRequest = HttpRequest.newBuilder()
            .uri(URI.create(getFileUrl))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build()

        HttpResponse<String> getFileResponse = httpClient.send(getFileRequest, HttpResponse.BodyHandlers.ofString())

        if (getFileResponse.statusCode() != 200) {
            executionContext.logger.warn("Failed to get file info: HTTP ${getFileResponse.statusCode()}")
            return null
        }

        // Parse response to get file path
        def fileInfo = new groovy.json.JsonSlurper().parseText(getFileResponse.body())
        if (!fileInfo.ok || !fileInfo.result?.file_path) {
            executionContext.logger.warn("Invalid file info response: ${getFileResponse.body()}")
            return null
        }

        String filePath = fileInfo.result.file_path
        String downloadUrl = "https://api.telegram.org/file/bot${botToken}/${filePath}"

        executionContext.logger.info("Downloading file from: ${downloadUrl}")
        return downloadUrl

    } catch (Exception e) {
        executionContext.logger.error("Error downloading Telegram file: ${e.message}", e)
        return null
    }
}

// Helper function for processing voice messages - 调用Java语音转文字服务
Map processVoiceMessage(Map attachmentInfo, HttpClient httpClient, def executionContext) {
    try {
        executionContext.logger.info("Processing voice message with fileId: ${attachmentInfo.fileId}")

        // 直接调用Java类处理语音消息
        def marketplaceService = new org.moqui.mcp.MarketplaceMcpService(executionContext)

        def parameters = [
            sessionId: "voice_session_${System.currentTimeMillis()}".toString(),
            message: "[Voice Message]",
            merchantId: "voice_user_${System.currentTimeMillis()}".toString(),
            messageType: "voice",
            attachmentInfo: attachmentInfo
        ]

        def serviceResponse = marketplaceService.processMarketplaceMessage(parameters)

        if (serviceResponse.aiResponse) {
            executionContext.logger.info("Voice message successfully processed with speech-to-text")
            return [
                success: true,
                message: serviceResponse.aiResponse
            ]
        } else {
            executionContext.logger.warn("Voice message processing returned empty response")
            return [
                success: false,
                message: "🎙️ 收到您的语音消息（时长: ${attachmentInfo.duration}秒），但语音转文字服务暂时不可用。\n\n" +
                        "请您用文字重新描述一下：\n" +
                        "• 您要发布供应信息吗？\n" +
                        "• 您要采购某种产品吗？\n" +
                        "• 您想查看匹配建议吗？\n\n" +
                        "💡 提示：直接说出您的需求，比如\"我要采购100吨钢材\""
            ]
        }
    } catch (Exception e) {
        executionContext.logger.error("Error processing voice message: ${e.message}", e)
        return [
            success: false,
            message: "🎙️ 语音消息处理出错，请用文字描述您的需求。"
        ]
    }
}

// Helper function for processing image messages - 调用Java图片识别服务
Map processImageMessage(Map attachmentInfo, String caption, HttpClient httpClient, def executionContext) {
    try {
        executionContext.logger.info("Processing image message with fileId: ${attachmentInfo.fileId}")

        // 直接调用Java类处理图片消息
        def marketplaceService = new org.moqui.mcp.MarketplaceMcpService(executionContext)

        def parameters = [
            sessionId: "image_session_${System.currentTimeMillis()}".toString(),
            message: caption ?: "[Image Message]",
            merchantId: "image_user_${System.currentTimeMillis()}".toString(),
            messageType: "photo",
            attachmentInfo: attachmentInfo
        ]

        def serviceResponse = marketplaceService.processMarketplaceMessage(parameters)

        if (serviceResponse.aiResponse) {
            executionContext.logger.info("Image message successfully processed with demo recognition")
            return [
                success: true,
                message: serviceResponse.aiResponse
            ]
        } else {
            executionContext.logger.warn("Image message processing returned empty response")
            return [
                success: false,
                message: "📷 收到您的图片，但图片识别服务暂时不可用。\n\n" +
                        "请您用文字补充一些信息：\n" +
                        "• 这是什么产品的图片？\n" +
                        "• 您想要供应还是采购这个产品？\n" +
                        "• 需要什么规格和数量？\n\n" +
                        "💡 提示：结合图片内容，用文字详细描述您的需求"
            ]
        }
    } catch (Exception e) {
        executionContext.logger.error("Error processing image message: ${e.message}", e)
        return [
            success: false,
            message: "📷 图片处理出错，请用文字描述您的需求。"
        ]
    }
}

String buildUrl(String base, String path) {
    if (!base) return path
    if (base.endsWith("/")) {
        return path.startsWith("/") ? base + path.substring(1) : base + path
    } else {
        return path.startsWith("/") ? base + path : base + "/" + path
    }
}
