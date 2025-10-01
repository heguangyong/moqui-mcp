package org.moqui.mcp;

import org.moqui.context.ExecutionContext;
import org.moqui.entity.EntityValue;
import org.moqui.entity.EntityList;
import org.moqui.marketplace.matching.SmartMatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.math.BigDecimal;

/**
 * Marketplace AI Agent MCP Service
 * 基于Claude API的智能marketplace撮合服务
 */
public class MarketplaceMcpService {
    private static final Logger logger = LoggerFactory.getLogger(MarketplaceMcpService.class);
    private final ExecutionContext ec;
    private final HttpClient httpClient;
    private final SmartMatchingEngine matchingEngine;

    // Claude API配置
    private static final String CLAUDE_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String MODEL_NAME = "claude-3-5-sonnet-20241022";

    public MarketplaceMcpService(ExecutionContext ec) {
        this.ec = ec;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.matchingEngine = new SmartMatchingEngine(ec);
    }

    /**
     * 处理marketplace对话消息的主要入口
     */
    public Map<String, Object> processMarketplaceMessage(Map<String, Object> context) {
        String sessionId = (String) context.get("sessionId");
        String message = (String) context.get("message");
        String merchantId = (String) context.get("merchantId");

        logger.info("Processing marketplace message for session: {}, merchant: {}", sessionId, merchantId);

        try {
            // 获取或创建会话
            EntityValue session = getOrCreateSession(sessionId, merchantId);

            // 分析用户意图
            String intent = analyzeUserIntent(message);

            // 根据意图处理请求
            Map<String, Object> result = new HashMap<>();
            switch (intent) {
                case "PUBLISH_SUPPLY":
                    result = handlePublishSupply(session, message);
                    break;
                case "PUBLISH_DEMAND":
                    result = handlePublishDemand(session, message);
                    break;
                case "SEARCH_LISTINGS":
                    result = handleSearchListings(session, message);
                    break;
                case "VIEW_MATCHES":
                    result = handleViewMatches(session, message);
                    break;
                case "GET_STATS":
                    result = handleGetStats(session, message);
                    break;
                default:
                    result = handleGeneralChat(session, message);
            }

            // 生成AI响应
            String aiResponse = generateClaudeResponse(message, buildContextForClaudeCall(session, intent), intent);

            // 保存对话记录
            saveDialogMessage(sessionId, message, aiResponse, intent);

            result.put("aiResponse", aiResponse);
            result.put("intent", intent);

            return result;

        } catch (Exception e) {
            logger.error("Error processing marketplace message", e);
            return Map.of(
                "error", "处理失败: " + e.getMessage(),
                "aiResponse", "抱歉，系统暂时无法处理您的请求，请稍后再试。"
            );
        }
    }

    /**
     * 分析用户意图
     */
    private String analyzeUserIntent(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("发布") || lowerMessage.contains("供应") || lowerMessage.contains("出售")) {
            return "PUBLISH_SUPPLY";
        } else if (lowerMessage.contains("需要") || lowerMessage.contains("购买") || lowerMessage.contains("求购")) {
            return "PUBLISH_DEMAND";
        } else if (lowerMessage.contains("搜索") || lowerMessage.contains("查找") || lowerMessage.contains("寻找")) {
            return "SEARCH_LISTINGS";
        } else if (lowerMessage.contains("匹配") || lowerMessage.contains("推荐")) {
            return "VIEW_MATCHES";
        } else if (lowerMessage.contains("统计") || lowerMessage.contains("数据") || lowerMessage.contains("报告")) {
            return "GET_STATS";
        }

        return "GENERAL_CHAT";
    }

    /**
     * 处理发布供应信息
     */
    private Map<String, Object> handlePublishSupply(EntityValue session, String message) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 提取商品信息 (简化版本，实际应该使用更复杂的NLP)
            Map<String, Object> extractedInfo = extractProductInfo(message);

            if (isCompleteSupplyInfo(extractedInfo)) {
                // 创建supply listing
                Map<String, Object> listingParams = new HashMap<>();
                listingParams.put("listingType", "SUPPLY");
                listingParams.put("publisherId", session.get("merchantId"));
                listingParams.putAll(extractedInfo);

                Map<String, Object> createResult = ec.getService().sync()
                    .name("marketplace.MarketplaceServices.create#Listing")
                    .parameters(listingParams)
                    .call();

                if (createResult.containsKey("listingId")) {
                    String listingId = (String) createResult.get("listingId");

                    // 立即查找匹配
                    List<Map<String, Object>> matches = matchingEngine.findMatchesForListing(
                        listingId, 3, new BigDecimal("0.6"));

                    result.put("success", true);
                    result.put("listingId", listingId);
                    result.put("matches", matches);
                    result.put("matchCount", matches.size());
                } else {
                    result.put("error", "创建listing失败");
                }
            } else {
                result.put("needMoreInfo", true);
                result.put("missingFields", getMissingFields(extractedInfo));
            }
        } catch (Exception e) {
            logger.error("Error handling publish supply", e);
            result.put("error", "处理发布供应请求失败");
        }

        return result;
    }

    /**
     * 处理发布需求信息
     */
    private Map<String, Object> handlePublishDemand(EntityValue session, String message) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> extractedInfo = extractProductInfo(message);

            if (isCompleteDemandInfo(extractedInfo)) {
                Map<String, Object> listingParams = new HashMap<>();
                listingParams.put("listingType", "DEMAND");
                listingParams.put("publisherId", session.get("merchantId"));
                listingParams.putAll(extractedInfo);

                Map<String, Object> createResult = ec.getService().sync()
                    .name("marketplace.MarketplaceServices.create#Listing")
                    .parameters(listingParams)
                    .call();

                if (createResult.containsKey("listingId")) {
                    String listingId = (String) createResult.get("listingId");

                    List<Map<String, Object>> matches = matchingEngine.findMatchesForListing(
                        listingId, 3, new BigDecimal("0.6"));

                    result.put("success", true);
                    result.put("listingId", listingId);
                    result.put("matches", matches);
                    result.put("matchCount", matches.size());
                }
            } else {
                result.put("needMoreInfo", true);
                result.put("missingFields", getMissingFields(extractedInfo));
            }
        } catch (Exception e) {
            logger.error("Error handling publish demand", e);
            result.put("error", "处理发布需求请求失败");
        }

        return result;
    }

    /**
     * 处理搜索listings
     */
    private Map<String, Object> handleSearchListings(EntityValue session, String message) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> searchParams = extractSearchParams(message);
            searchParams.put("pageSize", 5); // 限制返回数量

            Map<String, Object> searchResult = ec.getService().sync()
                .name("marketplace.MarketplaceServices.search#Listings")
                .parameters(searchParams)
                .call();

            result.put("success", true);
            result.put("listings", searchResult.get("listings"));
            result.put("totalCount", searchResult.get("totalCount"));

        } catch (Exception e) {
            logger.error("Error handling search listings", e);
            result.put("error", "搜索失败");
        }

        return result;
    }

    /**
     * 处理查看匹配
     */
    private Map<String, Object> handleViewMatches(EntityValue session, String message) {
        Map<String, Object> result = new HashMap<>();

        try {
            String merchantId = (String) session.get("merchantId");

            // 获取该商家的最新listings
            EntityList merchantListings = ec.getEntity().find("marketplace.listing.Listing")
                .condition("publisherId", merchantId)
                .condition("status", "ACTIVE")
                .orderBy("-lastUpdatedStamp")
                .limit(3)
                .list();

            List<Map<String, Object>> allMatches = new ArrayList<>();
            for (EntityValue listing : merchantListings) {
                String listingId = listing.getString("listingId");
                List<Map<String, Object>> matches = matchingEngine.findMatchesForListing(
                    listingId, 2, new BigDecimal("0.5"));

                for (Map<String, Object> match : matches) {
                    match.put("sourceListing", listing);
                    allMatches.add(match);
                }
            }

            result.put("success", true);
            result.put("matches", allMatches);
            result.put("matchCount", allMatches.size());

        } catch (Exception e) {
            logger.error("Error handling view matches", e);
            result.put("error", "获取匹配信息失败");
        }

        return result;
    }

    /**
     * 处理获取统计信息
     */
    private Map<String, Object> handleGetStats(EntityValue session, String message) {
        try {
            return ec.getService().sync()
                .name("marketplace.MarketplaceServices.get#MarketplaceStats")
                .call();
        } catch (Exception e) {
            logger.error("Error handling get stats", e);
            return Map.of("error", "获取统计信息失败");
        }
    }

    /**
     * 处理一般对话
     */
    private Map<String, Object> handleGeneralChat(EntityValue session, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("chatMode", true);
        return result;
    }

    /**
     * 调用Claude API生成响应
     */
    private String generateClaudeResponse(String userMessage, String context, String intent) throws Exception {
        String apiKey = System.getProperty("anthropic.api.key");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("未配置Claude API密钥");
        }

        String prompt = buildMarketplacePrompt(userMessage, context, intent);

        String requestBody = String.format(
            "{\"model\":\"%s\",\"max_tokens\":1024,\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
            MODEL_NAME,
            escapeJson(prompt)
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(CLAUDE_API_URL))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // 简化的JSON解析
            String responseBody = response.body();
            int startIndex = responseBody.indexOf("\"text\":\"") + 8;
            int endIndex = responseBody.lastIndexOf("\"}]}");
            return responseBody.substring(startIndex, endIndex)
                .replace("\\n", "\n")
                .replace("\\\"", "\"");
        } else {
            throw new RuntimeException("Claude API调用失败: " + response.statusCode());
        }
    }

    /**
     * 构建marketplace专用的提示词
     */
    private String buildMarketplacePrompt(String userMessage, String context, String intent) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("你是一个专业的农贸市场AI助手，帮助商家进行智能供需撮合。\n\n");

        switch (intent) {
            case "PUBLISH_SUPPLY":
                prompt.append("用户想要发布供应信息。请帮助他们完善商品信息，包括：商品名称、数量、价格、品质等级、配送方式等。\n");
                break;
            case "PUBLISH_DEMAND":
                prompt.append("用户想要发布需求信息。请帮助他们明确需求详情，包括：商品名称、数量、期望价格、品质要求、交付时间等。\n");
                break;
            case "SEARCH_LISTINGS":
                prompt.append("用户想要搜索商品信息。请帮助他们精确搜索条件，并解读搜索结果。\n");
                break;
            case "VIEW_MATCHES":
                prompt.append("用户想要查看匹配推荐。请解读匹配结果，说明推荐理由，帮助用户决策。\n");
                break;
            case "GET_STATS":
                prompt.append("用户想要了解市场统计。请解读数据，提供市场洞察和建议。\n");
                break;
            default:
                prompt.append("请自然地回应用户的问题，如有需要可引导用户使用marketplace功能。\n");
        }

        prompt.append("\n上下文信息:\n").append(context);
        prompt.append("\n\n用户消息: ").append(userMessage);
        prompt.append("\n\n请用简洁、友好的语言回复，重点突出关键信息。");

        return prompt.toString();
    }

    // 辅助方法...
    private EntityValue getOrCreateSession(String sessionId, String merchantId) {
        EntityValue session = ec.getEntity().find("McpDialogSession")
            .condition("sessionId", sessionId)
            .one();

        if (session == null) {
            ec.getService().sync().name("create#McpDialogSession").parameters(Map.of(
                "sessionId", sessionId,
                "customerId", merchantId,
                "merchantId", merchantId,
                "currentPhase", "marketplace",
                "status", "ACTIVE",
                "createdDate", ec.getUser().getNowTimestamp()
            )).call();

            session = ec.getEntity().find("McpDialogSession")
                .condition("sessionId", sessionId)
                .one();
        }

        return session;
    }

    private String buildContextForClaudeCall(EntityValue session, String intent) {
        StringBuilder context = new StringBuilder();
        context.append("会话模式: ").append(intent).append("\n");
        context.append("商家ID: ").append(session.get("merchantId")).append("\n");

        // 添加最近的对话历史
        EntityList recentMessages = ec.getEntity().find("McpDialogMessage")
            .condition("sessionId", session.get("sessionId"))
            .orderBy("-processedDate")
            .limit(3)
            .list();

        if (!recentMessages.isEmpty()) {
            context.append("最近对话:\n");
            for (EntityValue msg : recentMessages) {
                context.append("用户: ").append(msg.get("content")).append("\n");
                context.append("助手: ").append(msg.get("aiResponse")).append("\n");
            }
        }

        return context.toString();
    }

    private void saveDialogMessage(String sessionId, String userMessage, String aiResponse, String intent) {
        String messageId = ec.getEntity().sequencedIdPrimary("org.moqui.mcp.MarketplaceDialog." + sessionId, null, 10L);
        ec.getService().sync().name("create#McpDialogMessage").parameters(Map.of(
            "messageId", messageId,
            "sessionId", sessionId,
            "messageType", intent,
            "content", userMessage,
            "aiResponse", aiResponse,
            "processedDate", ec.getUser().getNowTimestamp()
        )).call();
    }

    private Map<String, Object> extractProductInfo(String message) {
        // 简化版本的信息提取
        Map<String, Object> info = new HashMap<>();

        // 实际项目中应该使用更复杂的NLP
        String[] keywords = message.split("\\s+");
        for (String keyword : keywords) {
            if (keyword.matches("\\d+斤|\\d+公斤")) {
                info.put("quantity", keyword.replaceAll("[^\\d]", ""));
                info.put("quantityUnit", keyword.replaceAll("\\d+", ""));
            }
            if (keyword.matches("\\d+元|\\d+块")) {
                info.put("priceMin", keyword.replaceAll("[^\\d]", ""));
            }
        }

        // 商品名称识别（简化版）
        if (message.contains("菠菜")) info.put("title", "菠菜");
        else if (message.contains("白菜")) info.put("title", "白菜");
        else if (message.contains("萝卜")) info.put("title", "萝卜");

        // 品类识别
        if (message.contains("蔬菜") || message.contains("菜")) {
            info.put("category", "VEGETABLE");
        }

        return info;
    }

    private Map<String, Object> extractSearchParams(String message) {
        Map<String, Object> params = new HashMap<>();

        if (message.contains("蔬菜")) params.put("category", "VEGETABLE");
        if (message.contains("供应")) params.put("listingType", "SUPPLY");
        if (message.contains("需求")) params.put("listingType", "DEMAND");

        return params;
    }

    private boolean isCompleteSupplyInfo(Map<String, Object> info) {
        return info.containsKey("title") && info.containsKey("quantity");
    }

    private boolean isCompleteDemandInfo(Map<String, Object> info) {
        return info.containsKey("title") && info.containsKey("quantity");
    }

    private List<String> getMissingFields(Map<String, Object> info) {
        List<String> missing = new ArrayList<>();
        if (!info.containsKey("title")) missing.add("商品名称");
        if (!info.containsKey("quantity")) missing.add("数量");
        if (!info.containsKey("category")) missing.add("品类");
        return missing;
    }

    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}