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
            return "我来帮您创建供应信息！请提供以下信息：\\n1. 产品名称\\n2. 类别\\n3. 数量\\n4. 价格\\n5. 联系方式\\n\\n您可以说：我要发布钢材供应100吨单价4500元"

        case "DEMAND_CREATION":
            return "我来帮您创建需求信息！请提供以下信息：\\n1. 产品名称\\n2. 类别\\n3. 需求数量\\n4. 预算\\n5. 期限\\n\\n您可以说：我需要钢材150吨预算680000元"

        case "MATCHING_ANALYSIS":
            return "正在为您分析匹配结果...\\n\\n根据您的历史数据，我找到了以下匹配机会：\\n✅ 高质量钢材供应商（匹配度：92%）\\n✅ 建材采购需求（匹配度：88%）\\n✅ 本地化配送优势（匹配度：85%）\\n\\n需要查看详细匹配报告吗？"

        case "DATA_STATISTICS":
            return "📊 您的marketplace数据概览：\\n\\n📈 本月供应发布：12条\\n📋 本月需求发布：8条\\n🎯 成功匹配：15个\\n💰 交易总额：￥456,800\\n⭐ 平均评分：4.3/5.0\\n\\n需要查看详细报告吗？"

        default:
            return "您好！我是智能marketplace助手🤖\\n\\n我可以帮您：\\n🔹 发布供应/需求信息\\n🔹 智能匹配分析\\n🔹 数据统计报告\\n🔹 交易流程指导\\n\\n请告诉我您需要什么帮助？"
    }
}

// 创建marketplace会话
Map createMarketplaceSession() {
    // 绝对确定此代码被执行的简单测试
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

// 智能推荐服务
Map getSmartRecommendations() {
    String merchantId = context.merchantId
    Integer maxResults = context.maxResults ?: 5
    BigDecimal minScore = context.minScore ?: 0.6

    ec.logger.info("=== getSmartRecommendations called ===")
    ec.logger.info("merchantId: ${merchantId}, maxResults: ${maxResults}, minScore: ${minScore}")

    if (!merchantId) {
        ec.message.addError("缺少必填参数：merchantId")
        return [success: false, error: "缺少必填参数：merchantId"]
    }

    try {
        // 生成基于商家历史的智能推荐（模拟数据）
        List<Map<String, Object>> recommendations = [
            [
                title: "高质量钢材供应商推荐",
                description: "基于您的历史需求，为您推荐优质钢材供应商",
                score: 0.92,
                category: "钢材",
                type: "SUPPLY_MATCH",
                details: [
                    supplier: "华东钢铁集团",
                    location: "上海市浦东新区",
                    priceRange: "4200-4800元/吨",
                    quality: "Q345B",
                    capacity: "月产能15000吨"
                ]
            ],
            [
                title: "建材采购需求匹配",
                description: "发现3个与您供应能力匹配的建材采购需求",
                score: 0.88,
                category: "建材",
                type: "DEMAND_MATCH",
                details: [
                    buyer: "江苏建设工程有限公司",
                    project: "工业园区基础设施建设",
                    quantity: "预计需求8000吨",
                    budget: "总预算3500万元",
                    timeline: "6个月交付期"
                ]
            ],
            [
                title: "物流优化建议",
                description: "基于地理位置分析，推荐最优配送路线",
                score: 0.85,
                category: "物流",
                type: "LOGISTICS_OPTIMIZATION",
                details: [
                    currentCost: "平均运输成本150元/吨",
                    optimizedCost: "优化后成本120元/吨",
                    savings: "预计节省20%运输成本",
                    route: "沪宁高速 → 京沪高速路线",
                    timeReduction: "减少运输时间2小时"
                ]
            ],
            [
                title: "市场价格趋势分析",
                description: "钢材市场价格上涨趋势，建议调整销售策略",
                score: 0.78,
                category: "市场分析",
                type: "MARKET_TREND",
                details: [
                    currentPrice: "当前市场均价4450元/吨",
                    trendDirection: "上涨趋势",
                    expectedIncrease: "预计上涨8-12%",
                    recommendation: "适当增加库存，延后大宗销售",
                    timeframe: "未来3个月"
                ]
            ],
            [
                title: "新客户开发机会",
                description: "识别到潜在的新客户群体，建议主动接触",
                score: 0.72,
                category: "客户开发",
                type: "CUSTOMER_OPPORTUNITY",
                details: [
                    targetIndustry: "新能源汽车制造",
                    potentialClients: "3家大型车企",
                    estimatedValue: "年采购量预计50000吨",
                    contactMethod: "通过行业展会和B2B平台",
                    successRate: "预计成功率65%"
                ]
            ]
        ]

        // 根据分数过滤和限制结果数量
        def filteredRecommendations = recommendations
            .findAll { it.score >= minScore }
            .sort { -it.score }
            .take(maxResults)

        ec.logger.info("=== Recommendations generated successfully ===")
        ec.logger.info("Total filtered recommendations: ${filteredRecommendations.size()}")

        return [
            success: true,
            recommendations: filteredRecommendations,
            totalCount: filteredRecommendations.size(),
            merchantId: merchantId,
            generatedAt: new Date(),
            criteria: [
                maxResults: maxResults,
                minScore: minScore
            ]
        ]

    } catch (Exception e) {
        ec.logger.error("Error getting smart recommendations", e)
        return [
            success: false,
            error: "获取推荐失败：${e.message}",
            merchantId: merchantId
        ]
    }
}

// 商家行为分析服务
Map analyzeMerchantBehavior() {
    String merchantId = context.merchantId
    Integer days = context.days ?: 30

    if (!merchantId) {
        ec.message.addError("缺少必填参数：merchantId")
        return [:]
    }

    try {
        // 计算时间范围
        Date fromDate = new Date(System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L))

        // 统计发布数量
        Long supplyCount = ec.entity.find("marketplace.listing.Listing")
            .condition("publisherId", merchantId)
            .condition("listingType", "SUPPLY")
            .condition("createdDate", EntityCondition.GREATER_THAN_EQUAL_TO, fromDate)
            .count()

        Long demandCount = ec.entity.find("marketplace.listing.Listing")
            .condition("publisherId", merchantId)
            .condition("listingType", "DEMAND")
            .condition("createdDate", EntityCondition.GREATER_THAN_EQUAL_TO, fromDate)
            .count()

        // 统计对话次数
        Long chatCount = ec.entity.find("McpDialogMessage")
            .condition([
                "sessionId", EntityCondition.IN,
                ec.entity.find("McpDialogSession")
                    .condition("merchantId", merchantId)
                    .getFieldList("sessionId")
            ])
            .condition("processedDate", EntityCondition.GREATER_THAN_EQUAL_TO, fromDate)
            .count()

        // 计算活跃度分数
        double activityScore = (supplyCount + demandCount) * 10 + chatCount * 2
        String activityLevel = activityScore > 100 ? "高" : (activityScore > 30 ? "中" : "低")

        return [
            success: true,
            merchantId: merchantId,
            analyzePeriod: days,
            supplyCount: supplyCount,
            demandCount: demandCount,
            chatCount: chatCount,
            activityScore: activityScore,
            activityLevel: activityLevel,
            analysis: [
                totalListings: supplyCount + demandCount,
                supplyDemandRatio: demandCount > 0 ? (supplyCount / demandCount) : supplyCount,
                avgListingsPerDay: (supplyCount + demandCount) / days,
                chatEngagement: chatCount / Math.max(1, supplyCount + demandCount)
            ]
        ]

    } catch (Exception e) {
        logger.error("Error analyzing merchant behavior", e)
        return [
            error: "分析商家行为失败：${e.message}"
        ]
    }
}