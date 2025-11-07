/*
 * Telegram Webhook Handler for Intelligent Supply-Demand Platform
 *
 * This script processes incoming Telegram messages and integrates with
 * the MarketplaceMcpService for AI-powered supply and demand matching.
 */

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.moqui.mcp.MarketplaceMcpService
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Timestamp

// Initialize HTTP client for Telegram API calls
HttpClient telegramHttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

ec.logger.info("=== TELEGRAM WEBHOOK PROCESSING STARTED ===")
ec.logger.info("Request parameters: update=${context.update}, message=${context.message}")

try {
    // Parse Telegram message data
Map update = context.update instanceof Map ? (Map) context.update :
        (context instanceof Map ? (Map) context : [:])
Map callbackQuery = update.callback_query instanceof Map ? (Map) update.callback_query : [:]
if (!callbackQuery && context.callback_query instanceof Map) {
    callbackQuery = (Map) context.callback_query
}
if (!callbackQuery && ec.web?.requestBodyText) {
    try {
        def rawJson = new JsonSlurper().parseText(ec.web.requestBodyText)
        if (rawJson instanceof Map) {
            if (!update) update = (Map) rawJson
            if (!callbackQuery && rawJson.callback_query instanceof Map) {
                callbackQuery = (Map) rawJson.callback_query
            }
        }
    } catch (Exception ignored) { }
}
    if (callbackQuery) {
        handleCallbackQuery(callbackQuery, telegramHttpClient, ec)
        context.success = true
        context.response = [ok: true]
        return
    }

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
    ensureTelegramParty(merchantId, ec)

    ensureDialogSession(sessionId, merchantId, ec)
    Map sessionContext = loadSessionContext(sessionId, ec)

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
                    aiResponse: "👋 欢迎加入智能推荐平台！\n\n请选择或直接告诉我您的需求：",
                    intent: "welcome"
            ]
        }

        String aiResponse = (result.aiResponse ?: "欢迎使用智能推荐！").toString()

        context.success = true
        context.aiResponse = aiResponse
        context.chatId = chatId
        context.intent = result.intent ?: "welcome"
        context.matches = result.matches ?: []
        context.response = [ok: true]

        // Send Telegram message
        sendTelegramMessage(chatId, aiResponse, telegramHttpClient, ec, createMainMenuKeyboard())

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

    if (messageType == "text") {
        Map projectCommandResult = processProjectCommand(incomingText, merchantId, ec)
        if (projectCommandResult?.handled) {
            String replyText = projectCommandResult.message ?: "项目命令已处理"
            sendTelegramMessage(chatId, replyText, telegramHttpClient, ec)

            context.success = projectCommandResult.success != false
            context.aiResponse = replyText
            context.chatId = chatId
            context.intent = projectCommandResult.intent ?: "project_command"
            context.response = [ok: true]
            return
        }
    }

    if (sessionContext?.smartMode == true && messageType == "text") {
        handleSmartClassification(chatId, incomingText, sessionId, telegramHttpClient, ec)
        context.success = true
        context.chatId = chatId
        context.intent = "smart_classify"
        context.response = [ok: true]
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
void sendTelegramMessage(String chatId, String messageText, HttpClient httpClient, def executionContext, Map replyMarkup = null) {
    try {
        String botToken = resolveBotToken(executionContext)

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

        if (replyMarkup) {
            requestData.reply_markup = replyMarkup
        }

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
            String respBody = response.body()
            if (response.statusCode() == 400 && respBody?.contains("chat not found")) {
                executionContext.logger.info("Telegram聊天 ${chatId} 不可达，已忽略发送: ${respBody}")
            } else {
                executionContext.logger.warn("Telegram消息发送失败: HTTP ${response.statusCode()}, 响应: ${respBody}")
            }
        }

    } catch (Exception e) {
        executionContext.logger.error("发送Telegram消息时出错: ${e.message}", e)
        // 降级到仅日志记录
        executionContext.logger.info("Telegram message to chat ${chatId}: ${messageText}")
    }
}

String resolveBotToken(def executionContext) {
    return System.getProperty("telegram.bot.token") ?:
            System.getenv("TELEGRAM_BOT_TOKEN") ?:
            executionContext.ecfi.getConfValue("telegram.bot.token")
}

void editTelegramMessage(String chatId, Integer messageId, String messageText, Map replyMarkup, HttpClient httpClient, def executionContext) {
    try {
        String botToken = resolveBotToken(executionContext)
        if (!botToken) {
            executionContext.logger.warn("Telegram Bot Token未配置，无法编辑消息")
            return
        }

        Map<String, Object> requestData = [
            chat_id   : chatId,
            message_id: messageId,
            text      : messageText,
            parse_mode: "Markdown"
        ]
        if (replyMarkup) requestData.reply_markup = replyMarkup

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot${botToken}/editMessageText"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(requestData)))
            .timeout(Duration.ofSeconds(30))
            .build()

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            String respBody = response.body()
            if (response.statusCode() == 400) {
                executionContext.logger.info("编辑Telegram消息失败，回退为新消息: ${respBody}")
                sendTelegramMessage(chatId, messageText, httpClient, executionContext, replyMarkup)
            } else {
                executionContext.logger.warn("编辑消息失败: HTTP ${response.statusCode()} -> ${respBody}")
            }
        }
    } catch (Exception e) {
        executionContext.logger.error("编辑Telegram消息异常: ${e.message}", e)
        sendTelegramMessage(chatId, messageText, httpClient, executionContext, replyMarkup)
    }
}

void answerCallbackQuery(String callbackQueryId, HttpClient httpClient, def executionContext) {
    try {
        String botToken = resolveBotToken(executionContext)
        if (!botToken) return

        Map<String, Object> requestData = [callback_query_id: callbackQueryId]
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.telegram.org/bot${botToken}/answerCallbackQuery"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(JsonOutput.toJson(requestData)))
            .timeout(Duration.ofSeconds(30))
            .build()
        httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    } catch (Exception e) {
        executionContext.logger.warn("answerCallbackQuery 调用失败: ${e.message}")
    }
}

Map createMainMenuKeyboard() {
    return [
        inline_keyboard: [
            [[text: "📊 智能供需匹配", callback_data: "category_supply_demand"],
             [text: "🏗️ 蜂巢项目管理", callback_data: "category_hivemind"]],
            [[text: "🛒 流行电商", callback_data: "category_ecommerce"],
             [text: "💼 大理石ERP", callback_data: "category_erp"]],
            [[text: "🤖 智能识别模式", callback_data: "smart_classify"],
             [text: "ℹ️ 帮助说明", callback_data: "help_info"]]
        ]
    ]
}

Map createSupplyDemandSubMenu() {
    return [
        inline_keyboard: [
            [[text: "🔍 发现匹配", callback_data: "sd_discover"],
             [text: "📢 发布信息", callback_data: "sd_publish"]],
            [[text: "🎯 精准推荐", callback_data: "sd_recommend"],
             [text: "📈 交易跟踪", callback_data: "sd_track"]],
            [[text: "🎤 语音输入", callback_data: "sd_voice"],
             [text: "📷 图像识别", callback_data: "sd_image"]],
            [[text: "⬅️ 返回主菜单", callback_data: "main_menu"]]
        ]
    ]
}

Map createProjectSubMenu() {
    return [
        inline_keyboard: [
            [[text: "📋 创建项目", callback_data: "project_create"],
             [text: "📊 项目概览", callback_data: "project_list"]],
            [[text: "🔄 同步状态", callback_data: "project_sync"],
             [text: "🗂️ 任务清单", callback_data: "project_tasks"]],
            [[text: "⬅️ 返回主菜单", callback_data: "main_menu"]]
        ]
    ]
}

Map createEcommerceSubMenu() {
    return [
        inline_keyboard: [
            [[text: "🛍️ 商品搜索", callback_data: "ec_search"],
             [text: "📦 库存管理", callback_data: "ec_inventory"]],
            [[text: "🛒 订单查询", callback_data: "ec_orders"],
             [text: "👥 客户管理", callback_data: "ec_customers"]],
            [[text: "📊 销售统计", callback_data: "ec_analytics"],
             [text: "🎯 智能推荐", callback_data: "ec_recommend"]],
            [[text: "⬅️ 返回主菜单", callback_data: "main_menu"]]
        ]
    ]
}

Map processProjectCommand(String rawCommand, String merchantId, def executionContext) {
    if (!rawCommand) return [handled: false]
    String trimmed = rawCommand.trim()
    if (!trimmed || !trimmed.toLowerCase().startsWith("/project")) {
        return [handled: false]
    }

    List<String> tokens = trimmed.split(/\s+/)
    String action = tokens.size() > 1 ? tokens[1].toLowerCase() : "status"
    String identifier = tokens.size() > 2 ? tokens[2] : null

    List<String> statusKeywords = ["status", "sync", "状态", "tongbu", "同步"]
    List<String> taskKeywords = ["tasks", "task", "list", "renwu", "任务"]
    if (!(action in statusKeywords || action in taskKeywords)) {
        return [handled: true, success: true,
                message: "⚙️ 项目命令用法:\n" +
                        "/project status [项目ID]  查看项目状态\n" +
                        "/project tasks [项目ID]   查看任务列表\n\n" +
                        "示例:\n/project status PROJECT-1001\n/project tasks WORK1003"]
    }

    Map projectRecord = resolveHiveMindProjectRecord(identifier, merchantId, executionContext)
    if (!projectRecord) {
        String hint = identifier ? "未找到ID为 ${identifier} 的 HiveMind 项目" : "未找到和您账户关联的项目"
        String advice = identifier ? "请确认项目ID是否正确，或先在控制台创建项目" : "可以在控制台创建供需项目后再试"
        return [handled: true, success: false, message: "⚠️ ${hint}\n${advice}"]
    }

    if (action in taskKeywords) {
        return handleProjectTasksCommand(projectRecord, identifier, executionContext)
    }

    Map syncParams = [:]
    if (projectRecord.hiveMindProjectId) syncParams.hiveMindProjectId = projectRecord.hiveMindProjectId
    if (!syncParams && projectRecord.workEffortId) syncParams.workEffortId = projectRecord.workEffortId

    Map syncResult = [:]
    boolean syncSuccess = false
    String syncError = null
    if (syncParams) {
        try {
            syncResult = executionContext.service.sync()
                    .name("marketplace.MarketplaceServices.sync#HiveMindProjectStatus")
                    .parameters(syncParams)
                    .call()
            syncSuccess = syncResult.success != false
        } catch (Exception e) {
            executionContext.logger.warn("同步HiveMind项目状态失败: ${e.message}")
            syncSuccess = false
            syncError = e.message
        }
    }

    String messageErrors = executionContext.message?.errorsString
    executionContext.message.clearErrors()
    if (messageErrors) {
        syncError = messageErrors
    }

    String latestStatus = syncResult.projectStatus ?: projectRecord.syncStatus ?: "UNKNOWN"
    Timestamp lastSyncTs = syncResult.success ? executionContext.user.nowTimestamp : projectRecord.lastSyncDate
    String lastSyncText = formatTimestamp(executionContext, lastSyncTs)

    String listingName = projectRecord.listingTitle ?: projectRecord.listingId ?: "—"

    StringBuilder sb = new StringBuilder()
    sb.append("📌 HiveMind 项目状态\n")
    sb.append("• 项目ID: ${projectRecord.hiveMindProjectId ?: '尚未同步'}\n")
    if (projectRecord.workEffortId) sb.append("• WorkEffort: ${projectRecord.workEffortId}\n")
    sb.append("• 关联需求: ${listingName}\n")
    sb.append("• 当前状态: ${latestStatus}\n")
    sb.append("• 上次同步: ${lastSyncText ?: '未同步'}\n")

    if (syncResult.response?.updatedAt) {
        sb.append("• HiveMind更新时间: ${syncResult.response.updatedAt}\n")
    }
    if (syncResult.response?.owner) {
        sb.append("• 负责人: ${syncResult.response.owner}\n")
    }

    if (!syncSuccess) {
        String fallback = syncError ?: "暂时无法连接HiveMind，请稍后重试"
        sb.append("\n⚠️ 同步未成功: ${fallback}")
    }

    return [handled: true, success: true, intent: "project_status_command", message: sb.toString()]
}

Map handleProjectTasksCommand(Map projectRecord, String identifier, def executionContext) {
    Map taskParams = [:]
    if (projectRecord.hiveMindProjectId) taskParams.hiveMindProjectId = projectRecord.hiveMindProjectId
    if (!taskParams && projectRecord.workEffortId) taskParams.workEffortId = projectRecord.workEffortId
    taskParams.maxResults = 6

    if (!taskParams) {
        return [handled: true, success: false, message: "⚠️ 该项目尚未同步到HiveMind，暂无任务可展示"]
    }

    Map taskResult = [:]
    boolean fetchSuccess = false
    try {
        taskResult = executionContext.service.sync()
                .name("marketplace.MarketplaceServices.fetch#HiveMindProjectTasks")
                .parameters(taskParams)
                .call()
        fetchSuccess = taskResult.success != false
    } catch (Exception e) {
        executionContext.logger.warn("获取项目任务失败: ${e.message}")
        fetchSuccess = false
    }

    String messageErrors = executionContext.message?.errorsString
    executionContext.message.clearErrors()
    if (messageErrors && !fetchSuccess) {
        return [handled: true, success: false, message: "⚠️ ${messageErrors}"]
    }

    List tasks = taskResult.tasks instanceof List ? (List) taskResult.tasks : []
    StringBuilder sb = new StringBuilder()
    sb.append("🗂️ 项目任务列表\n")
    sb.append("• 项目ID: ${projectRecord.hiveMindProjectId ?: '尚未同步'}\n")
    if (projectRecord.workEffortId) sb.append("• WorkEffort: ${projectRecord.workEffortId}\n")
    sb.append("• 数据来源: ${taskResult.source ?: (fetchSuccess ? 'HIVEMIND' : 'LOCAL')}\n\n")

    if (!tasks) {
        sb.append("暂无任务记录，您可以在控制台中创建任务或稍后再试。")
        return [handled: true, success: fetchSuccess, intent: "project_tasks_command", message: sb.toString()]
    }

    tasks.eachWithIndex { Map task, int idx ->
        String status = task.status ?: "UNKNOWN"
        String assigned = task.assignedTo ?: "--"
        String due = task.dueDate ? task.dueDate.toString() : "--"
        sb.append("${idx + 1}. ${task.name ?: '未命名任务'}\n")
        sb.append("   状态: ${status} | 负责人: ${assigned}\n")
        if (due && due != "--") sb.append("   截止: ${due}\n")
        if (task.description) sb.append("   描述: ${task.description}\n")
    }

    sb.append("\n提示: 可在控制台更新任务状态，也可稍后再使用 /project status 查看最新进度。")
    return [handled: true, success: true, intent: "project_tasks_command", message: sb.toString()]
}

Map resolveHiveMindProjectRecord(String identifier, String merchantId, def executionContext) {
    def entity = executionContext.entity
    def projectFind = entity.find("marketplace.project.HiveMindProject")
    boolean hasCondition = false

    if (identifier) {
        projectFind.condition("hiveMindProjectId", identifier)
        hasCondition = true
    }

    if (!identifier && merchantId) {
        def latestListing = entity.find("marketplace.listing.Listing")
                .condition("publisherId", merchantId)
                .orderBy("-createdDate")
                .limit(1)
                .disableAuthz()
                .one()
        if (latestListing) {
            projectFind.condition("listingId", latestListing.listingId)
            hasCondition = true
        }
    }

    projectFind.orderBy("-lastSyncDate")
    projectFind.limit(1)
    projectFind.disableAuthz()
    def projectValue = projectFind.one()

    if (!projectValue && identifier) {
        def wfFind = entity.find("marketplace.project.HiveMindProject")
                .condition("workEffortId", identifier)
                .orderBy("-lastSyncDate")
                .limit(1)
                .disableAuthz()
        projectValue = wfFind.one()
    }

    if (!projectValue) return null

    Map projectMap = [
            workEffortId      : projectValue.workEffortId,
            hiveMindProjectId : projectValue.hiveMindProjectId,
            listingId         : projectValue.listingId,
            syncStatus        : projectValue.syncStatus,
            lastSyncDate      : projectValue.lastSyncDate
    ]

    if (projectMap.listingId) {
        def listing = entity.find("marketplace.listing.Listing")
                .condition("listingId", projectMap.listingId)
                .disableAuthz()
                .one()
        if (listing) {
            projectMap.listingTitle = listing.title ?: listing.listingId
            projectMap.publisherId = listing.publisherId
        }
    }

    return projectMap
}

String formatTimestamp(def executionContext, def ts) {
    if (!ts) return null
    try {
        return executionContext.l10n.format(ts, "yyyy-MM-dd HH:mm")
    } catch (Exception e) {
        executionContext.logger.debug("无法格式化时间: ${e.message}")
        return ts.toString()
    }
}

void handleCallbackQuery(Map callbackQuery, HttpClient httpClient, def ec) {
    String callbackId = callbackQuery.id
    Map message = callbackQuery.message instanceof Map ? (Map) callbackQuery.message : [:]
    String chatId = message.chat?.id?.toString()
    Integer messageId = message.message_id instanceof Number ? ((Number) message.message_id).intValue() : null
    String data = callbackQuery.data ?: ""
    ec.logger.warn("Telegram callback received chat=${chatId}, data=${data}")
    if (!chatId) {
        answerCallbackQuery(callbackId, httpClient, ec)
        return
    }

    String merchantId = callbackQuery.from?.id?.toString() ?: chatId
    ensureTelegramParty(merchantId, ec)
    String sessionId = "telegram_${chatId}"
    ensureDialogSession(sessionId, merchantId, ec)
    Map sessionContext = loadSessionContext(sessionId, ec)

    switch (data) {
        case "category_supply_demand":
            sendTelegramMessage(chatId, "📊 智能供需匹配\n\n请选择需要的操作：", httpClient, ec, createSupplyDemandSubMenu())
            break
        case "category_hivemind":
            sendTelegramMessage(chatId, "🏗️ 蜂巢项目管理\n\n请选择需要的操作：", httpClient, ec, createProjectSubMenu())
            break
        case "category_ecommerce":
            sendTelegramMessage(chatId, "🛒 流行电商\n\n请选择需要的操作：", httpClient, ec, createEcommerceSubMenu())
            break
        case "category_erp":
            sendTelegramMessage(chatId, "💼 大理石 ERP 正在集成中，稍后为您开放。", httpClient, ec)
            break
        case "ec_search":
            try {
                List<Map> products = fetchEcommerceProducts(ec, 5, [:])
                String text = formatProductListMessage(products, ec)
                editTelegramMessage(chatId, messageId, text, createEcommerceSubMenu(), httpClient, ec)
            } catch (Exception e) {
                ec.logger.error("电商商品搜索回调异常", e)
                sendTelegramMessage(chatId, "❌ 获取商品列表失败，请稍后再试。", httpClient, ec)
            }
            break
        case "ec_inventory":
            try {
                List<Map> products = fetchEcommerceProducts(ec, 50, [:])
                List<Map> lowStock = products.findAll {
                    def qty = it.stockQuantity
                    qty instanceof Number ? qty.longValue() < 5L : false
                }
                String text = formatLowStockMessage(lowStock, ec)
                editTelegramMessage(chatId, messageId, text, createEcommerceSubMenu(), httpClient, ec)
            } catch (Exception e) {
                ec.logger.error("电商库存提醒回调异常", e)
                sendTelegramMessage(chatId, "❌ 库存数据暂时不可用，请稍后重试。", httpClient, ec)
            }
            break
        case "ec_orders":
            editTelegramMessage(chatId, messageId,
                    "🛒 订单管理说明：\n" +
                    "• 使用 `/order create` 指令可由AI助手引导创建订单\n" +
                    "• REST接口：`POST /rest/s1/marketplace/ecommerce/orders`\n" +
                    "• 查看状态：`GET /rest/s1/marketplace/ecommerce/orders/{ecommerceOrderId}`\n\n" +
                    "请选择其他操作或输入订单编号获取详情。", createEcommerceSubMenu(), httpClient, ec)
            break
        case "ec_customers":
            editTelegramMessage(chatId, messageId,
                    "👥 客户管理规划：\n" +
                    "• 将同步HiveMind项目信息生成客户档案\n" +
                    "• 支持从Telegram直接绑定客户意向\n" +
                    "• Web控制台正在建设客户360视图。\n\n" +
                    "欢迎先录入客户标签，方便后续联动推荐。", createEcommerceSubMenu(), httpClient, ec)
            break
        case "ec_analytics":
            editTelegramMessage(chatId, messageId,
                    "📊 销售数据分析即将上线：\n" +
                    "• 实时GMV与订单转化率\n" +
                    "• 商品热度排行榜\n" +
                    "• 客户复购与人群分层。\n\n" +
                    "相关仪表板将同步至控制台 Dashboard。", createEcommerceSubMenu(), httpClient, ec)
            break
        case "ec_recommend":
            try {
                List<Map> recommendations = fetchEcommerceRecommendations(ec, 5)
                String text = formatRecommendationMessage(recommendations, ec)
                editTelegramMessage(chatId, messageId, text, createEcommerceSubMenu(), httpClient, ec)
            } catch (Exception e) {
                ec.logger.error("电商推荐回调异常", e)
                sendTelegramMessage(chatId, "❌ 推荐功能暂时不可用，请稍后再试。", httpClient, ec)
            }
            break
        case "project_create":
            sendTelegramMessage(chatId,
                    "📋 创建项目\n" +
                    "1️⃣ 即将开放 `/project create 项目名称` 指令\n" +
                    "2️⃣ 当前可在控制台新建项目，Telegram 会同步最新状态\n" +
                    "3️⃣ 发送 `/project status [项目ID]` 可随时查询进度",
                    httpClient, ec)
            break
        case "project_list":
            sendTelegramMessage(chatId,
                    "📊 项目概览功能开发中。\n" +
                    "暂时可通过 `/project status [项目ID]` 或 Web 控制台查看项目列表。",
                    httpClient, ec)
            break
        case "project_sync":
            sendTelegramMessage(chatId,
                    "🔄 状态同步\n" +
                    "发送 `/project status [项目ID]` 将立即同步 HiveMind 状态。",
                    httpClient, ec)
            break
        case "project_tasks":
            sendTelegramMessage(chatId,
                    "🗂️ 任务清单\n" +
                    "使用 `/project tasks [项目ID]` 查看任务详情；若项目尚未同步，可先在控制台维护任务。",
                    httpClient, ec)
            break
        case "smart_classify":
            sessionContext.smartMode = true
            sendTelegramMessage(chatId, "🤖 智能识别模式已启用，请直接输入需求，我会自动识别业务类型并为您导航。", httpClient, ec)
            break
        case "help_info":
            sendTelegramMessage(chatId, "ℹ️ 操作指南：\n1️⃣ 选择分类进入对应功能\n2️⃣ 使用智能识别模式直接描述需求\n3️⃣ 随时点击返回主菜单切换功能", httpClient, ec)
            break
        case "main_menu":
            sessionContext.smartMode = false
            editTelegramMessage(chatId, messageId, "请选择业务分类：", createMainMenuKeyboard(), httpClient, ec)
            break
        case "sd_discover":
            sendTelegramMessage(chatId, "🔍 正在为您查找匹配的供需信息……请稍候。", httpClient, ec)
            break
        case "sd_publish":
            sendTelegramMessage(chatId, "📢 请直接输入要发布的供需内容，我会协助您完成。", httpClient, ec)
            break
        case "sd_recommend":
            sendTelegramMessage(chatId, "🎯 正在根据您的历史记录准备推荐结果……", httpClient, ec)
            break
        case "sd_track":
            sendTelegramMessage(chatId, "📈 项目跟踪功能即将开放，请持续关注。", httpClient, ec)
            break
        case "sd_voice":
            sendTelegramMessage(chatId, "🎤 已切换语音输入模式，请直接发送语音消息。", httpClient, ec)
            break
        case "sd_image":
            sendTelegramMessage(chatId, "📷 请上传相关图片，我会帮助识别并整理需求。", httpClient, ec)
            break
        default:
            sendTelegramMessage(chatId, "⚙️ 功能开发中，敬请等待进一步更新。", httpClient, ec)
            break
    }

    saveSessionContext(sessionId, sessionContext, ec)
    answerCallbackQuery(callbackId, httpClient, ec)
}

void ensureDialogSession(String sessionId, String merchantId, def ec) {
    def sessionValue = ec.entity.find("mcp.dialog.McpDialogSession")
        .condition("sessionId", sessionId)
        .disableAuthz()
        .one()
    if (sessionValue) {
        ec.service.sync().name("update#mcp.dialog.McpDialogSession").parameters([
            sessionId       : sessionId,
            lastModifiedDate: ec.user.nowTimestamp
        ]).disableAuthz().call()
        return
    }
    ec.service.sync().name("create#mcp.dialog.McpDialogSession").parameters([
        sessionId       : sessionId,
        customerId      : merchantId,
        merchantId      : merchantId,
        sessionType     : "TELEGRAM",
        status          : "ACTIVE",
        createdDate     : ec.user.nowTimestamp,
        lastModifiedDate: ec.user.nowTimestamp,
        context         : JsonOutput.toJson([:])
    ]).disableAuthz().call()
}

Map loadSessionContext(String sessionId, def ec) {
    def sessionValue = ec.entity.find("mcp.dialog.McpDialogSession")
        .condition("sessionId", sessionId)
        .disableAuthz()
        .one()
    if (!sessionValue?.context) return [:]
    try {
        def parsed = new JsonSlurper().parseText(sessionValue.context)
        return parsed instanceof Map ? parsed : [:]
    } catch (Exception e) {
        ec.logger.warn("无法解析会话上下文: ${e.message}")
        return [:]
    }
}

void saveSessionContext(String sessionId, Map context, def ec) {
    def sessionValue = ec.entity.find("mcp.dialog.McpDialogSession")
        .condition("sessionId", sessionId)
        .forUpdate(true)
        .disableAuthz()
        .one()
    if (!sessionValue) return
    sessionValue.set("context", JsonOutput.toJson(context ?: [:]))
    sessionValue.set("lastModifiedDate", ec.user.nowTimestamp)
    sessionValue.store()
}

void ensureTelegramParty(String partyId, def ec) {
    if (!partyId) return
    try {
        def existingParty = ec.entity.find("mantle.party.Party")
            .condition("partyId", partyId)
            .disableAuthz()
            .one()
        if (existingParty) return

        ec.logger.info("Creating Party for Telegram user: ${partyId}")
        ec.service.sync().name("create#mantle.party.Party").parameters([
            partyId        : partyId,
            partyTypeEnumId: "PtyPerson",
            disabled       : "N"
        ]).disableAuthz().call()
    } catch (Exception e) {
        ec.logger.warn("Failed to ensure Party ${partyId}: ${e.message}")
    }
}

String formatProductListMessage(List products, def ec) {
    if (!products || products.isEmpty()) {
        return "🛍️ 当前尚未创建商品，请先通过 Web 控制台或调用 REST API 新增商品。"
    }
    StringBuilder sb = new StringBuilder("🛍️ 最新商品列表：\n")
    products.eachWithIndex { Map prod, int idx ->
        String name = (prod.productName ?: prod.ecommerceProductId ?: "未命名商品").toString()
        String currency = prod.currencyUomId ?: "CNY"
        def price = prod.price
        BigDecimal priceValue = null
        if (price instanceof BigDecimal) {
            priceValue = (BigDecimal) price
        } else if (price != null) {
            try {
                priceValue = new BigDecimal(price.toString())
            } catch (Exception ignored) { }
        }
        String priceText = priceValue != null ? ec.l10n.formatCurrency(priceValue, currency) : "未定价"
        Long stock = prod.stockQuantity instanceof Number ? ((Number) prod.stockQuantity).longValue() : 0L
        sb.append("${idx + 1}. ${name}\n")
        sb.append("   价格：${priceText} | 库存：${stock}\n")
        if (prod.productCategoryId) sb.append("   分类：${prod.productCategoryId}\n")
    }
    sb.append("\n📍 更多操作可在智能推荐控制台中完成。")
    return sb.toString()
}

String formatLowStockMessage(List products, def ec) {
    if (!products || products.isEmpty()) {
        return "📦 所有商品库存充足，暂无低库存提醒。"
    }
    StringBuilder sb = new StringBuilder("📦 低库存提醒：\n")
    products.eachWithIndex { Map prod, int idx ->
        String name = (prod.productName ?: prod.ecommerceProductId ?: "未命名商品").toString()
        Long stock = prod.stockQuantity instanceof Number ? ((Number) prod.stockQuantity).longValue() : 0L
        sb.append("${idx + 1}. ${name} - 剩余 ${stock} 件\n")
    }
    sb.append("\n建议尽快补货或调整库存。")
    return sb.toString()
}

String formatRecommendationMessage(List recommendations, def ec) {
    if (!recommendations || recommendations.isEmpty()) {
        return "🎯 暂无推荐结果，请先录入商品与客户偏好信息。"
    }
    StringBuilder sb = new StringBuilder("🎯 精选推荐商品：\n")
    recommendations.eachWithIndex { Map rec, int idx ->
        String name = (rec.productName ?: rec.ecommerceProductId ?: "未命名商品").toString()
        def price = rec.price
        String currency = rec.currencyUomId ?: "CNY"
        BigDecimal priceValue = null
        if (price instanceof BigDecimal) {
            priceValue = (BigDecimal) price
        } else if (price != null) {
            try {
                priceValue = new BigDecimal(price.toString())
            } catch (Exception ignored) { }
        }
        String priceText = priceValue != null ? ec.l10n.formatCurrency(priceValue, currency) : "未定价"
        sb.append("${idx + 1}. ${name} - ${priceText}\n")
    }
    sb.append("\n可继续描述客户需求，AI 将输出更精确推荐。")
    return sb.toString()
}

List<Map> fetchEcommerceProducts(def ec, int limit = 20, Map filters = [:]) {
    def find = ec.entity.find("marketplace.ecommerce.EcommerceProduct")
    if (filters.productCategoryId) find.condition("productCategoryId", filters.productCategoryId)
    if (filters.status) find.condition("status", filters.status)
    find.orderBy("-lastUpdatedDate")
    find.limit(limit)
    def entityList = find.list()
    return entityList ? entityList.collect { it.getMap(false) } : []
}

List<Map> fetchEcommerceRecommendations(def ec, int limit = 5) {
    def find = ec.entity.find("marketplace.ecommerce.EcommerceProduct")
            .condition("status", "ACTIVE")
    find.orderBy("-lastUpdatedDate")
    find.limit(limit)
    def entityList = find.list()
    return entityList ? entityList.collect { it.getMap(false) } : []
}

void handleSmartClassification(String chatId, String messageText, String sessionId, HttpClient httpClient, def ec) {
    Map classifyResult = ec.service.sync().name("mcp.routing.classify#UserIntent").parameters([
        userMessage: messageText,
        chatId     : chatId
    ]).call()

    String category = classifyResult.businessCategory ?: "SUPPLY_DEMAND_MATCHING"
    BigDecimal confidence = classifyResult.confidence instanceof BigDecimal ?
        (BigDecimal) classifyResult.confidence : new BigDecimal(classifyResult.confidence?.toString() ?: "0")
    confidence = confidence.max(BigDecimal.ZERO).min(BigDecimal.ONE)
    BigDecimal percent = confidence.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP)

    String categoryName
    switch (category) {
        case "HIVEMIND_PROJECT": categoryName = "蜂巢项目管理"; break
        case "ECOMMERCE": categoryName = "流行电商"; break
        case "ERP": categoryName = "大理石 ERP"; break
        default: categoryName = "智能供需匹配"; category = "SUPPLY_DEMAND_MATCHING"; break
    }

    sendTelegramMessage(chatId,
        "🤖 智能识别结果：${categoryName} (置信度 ${percent}%)\n\n正在为您处理后续操作……",
        httpClient, ec)

    def sessionValue = ec.entity.find("mcp.dialog.McpDialogSession")
        .condition("sessionId", sessionId)
        .disableAuthz()
        .one()
    String merchantId = sessionValue?.merchantId ?: chatId

    switch (category) {
        case "SUPPLY_DEMAND_MATCHING":
            MarketplaceMcpService marketplaceService = new MarketplaceMcpService(ec)
            Map<String, Object> result = marketplaceService.processMarketplaceMessage([
                sessionId : sessionId,
                message   : messageText,
                merchantId: merchantId
            ])
            String response = (result.aiResponse ?: result.error ?: "处理完成。").toString()
            sendTelegramMessage(chatId, response, httpClient, ec, createSupplyDemandSubMenu())
            break
        case "HIVEMIND_PROJECT":
            sendTelegramMessage(chatId,
                "🏗️ 您的需求更适合蜂巢项目管理，我将把信息同步到项目中心，稍后会有专人联系您。",
                httpClient, ec)
            break
        case "ECOMMERCE":
            sendTelegramMessage(chatId,
                "🛒 已识别为电商相关需求，我们会把信息路由到流行电商服务。",
                httpClient, ec)
            break
        case "ERP":
            sendTelegramMessage(chatId,
                "💼 您的需求属于大理石 ERP，我们正在准备标准化流程。",
                httpClient, ec)
            break
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
