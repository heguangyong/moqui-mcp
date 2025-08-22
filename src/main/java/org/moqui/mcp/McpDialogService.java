package org.moqui.mcp;

import org.moqui.context.ExecutionContext;
import org.moqui.entity.EntityValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

public class McpDialogService {
    private static final Logger logger = LoggerFactory.getLogger(McpDialogService.class);
    private final ExecutionContext ec;
    private final HttpClient httpClient;

    // Ollama API配置
    private static final String OLLAMA_BASE_URL = "http://localhost:11434";
    private static final String MODEL_NAME = "gpt-oss:20b";

    public McpDialogService(ExecutionContext ec) {
        this.ec = ec;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 处理用户消息的主要入口
     */
    public Map<String, Object> processUserMessage(Map<String, Object> context) {
        String sessionId = (String) context.get("sessionId");
        String message = (String) context.get("message");
        String messageType = (String) context.get("messageType");

        logger.info("Processing message for session: {}, type: {}", sessionId, messageType);

        try {
            // 获取会话信息
            EntityValue session = ec.getEntity().find("McpDialogSession")
                    .condition("sessionId", sessionId)
                    .one();

            if (session == null) {
                return Map.of("error", "Session not found");
            }

            // 构建对话上下文
            String conversationContext = buildConversationContext(sessionId, session);

            // 生成AI响应
            String aiResponse = generateAiResponse(message, conversationContext,
                    (String) session.get("currentPhase"));

            // 保存对话记录
            saveDialogMessage(sessionId, message, aiResponse, messageType);

            // 分析响应并更新项目状态
            Map<String, Object> analysisResult = analyzeAiResponse(sessionId, aiResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("aiResponse", aiResponse);
            result.put("currentPhase", session.get("currentPhase"));
            result.putAll(analysisResult);

            return result;

        } catch (Exception e) {
            logger.error("Error processing dialog message", e);
            return Map.of("error", "Processing failed: " + e.getMessage());
        }
    }

    /**
     * 构建对话上下文
     */
    private String buildConversationContext(String sessionId, EntityValue session) {
        StringBuilder context = new StringBuilder();

        // 添加项目阶段信息
        String currentPhase = (String) session.get("currentPhase");
        context.append("当前项目阶段: ").append(getPhaseDescription(currentPhase)).append("\n");

        // 获取历史对话
        List<EntityValue> messages = ec.getEntity().find("McpDialogMessage")
                .condition("sessionId", sessionId)
                .orderBy("-processedDate")
                .limit(10)
                .list();

        context.append("历史对话:\n");
        for (EntityValue msg : messages) {
            context.append("用户: ").append(msg.get("content")).append("\n");
            context.append("AI: ").append(msg.get("aiResponse")).append("\n");
        }

        return context.toString();
    }

    /**
     * 调用Ollama生成AI响应
     */
    private String generateAiResponse(String userMessage, String context, String currentPhase) throws Exception {
        String prompt = buildPrompt(userMessage, context, currentPhase);

        String requestBody = String.format("""
            {
                "model": "%s",
                "prompt": "%s",
                "stream": false,
                "options": {
                    "temperature": 0.7,
                    "top_p": 0.9
                }
            }
            """, MODEL_NAME, escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_BASE_URL + "/api/generate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            // 简单的JSON解析，实际项目中建议使用Jackson或Gson
            String responseBody = response.body();
            int startIndex = responseBody.indexOf("\"response\":\"") + 12;
            int endIndex = responseBody.lastIndexOf("\"}");
            return responseBody.substring(startIndex, endIndex)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"");
        } else {
            throw new RuntimeException("Ollama API call failed: " + response.statusCode());
        }
    }

    /**
     * 构建针对不同阶段的专业提示词
     */
    private String buildPrompt(String userMessage, String context, String currentPhase) {
        String basePrompt = """
            你是一个专业的营销项目顾问，正在帮助小企业主制定数字化营销方案。
            你需要引导客户完成以下三个阶段：
            1. 需求收集确认（场景边界、角色、流程）
            2. 功能设计（基于需求的具体功能规划）
            3. 任务分解（将设计转化为可执行的开发任务）

            对话上下文：
            %s

            用户当前消息：%s

            """;

        String phaseSpecificPrompt = switch (currentPhase) {
            case "requirement" -> """
                当前阶段：需求收集确认
                请重点关注以下三个方面：
                1. 场景边界：客户的业务范围、目标用户群体、主要业务场景
                2. 角色：涉及的用户角色（客户、管理员、客服等）
                3. 流程：关键业务流程（获客、转化、服务等）

                请用引导性问题帮助客户澄清这些要点，每次聚焦一个方面。
                """;
            case "design" -> """
                当前阶段：功能设计
                基于已确认的需求，请帮助客户设计具体功能：
                1. 微信公众号功能规划
                2. 小程序功能模块
                3. 后台管理系统功能
                4. 数据分析和报表需求

                请提供具体、可实现的功能建议。
                """;
            case "task" -> """
                当前阶段：任务分解
                将设计方案分解为具体的开发任务：
                1. 按优先级排序任务
                2. 估算开发时间
                3. 确定交付里程碑
                4. 明确验收标准

                请生成详细的项目计划。
                """;
            default -> "";
        };

        return String.format(basePrompt + phaseSpecificPrompt, context, userMessage);
    }

    /**
     * 分析AI响应，判断是否需要阶段转换
     */
    private Map<String, Object> analyzeAiResponse(String sessionId, String aiResponse) {
        Map<String, Object> result = new HashMap<>();

        // 简单的关键词分析，实际项目中可以使用更sophisticated的NLP
        if (aiResponse.contains("需求已确认") || aiResponse.contains("进入设计阶段")) {
            result.put("nextAction", "PHASE_TRANSITION");
            result.put("suggestedPhase", "design");
        } else if (aiResponse.contains("设计完成") || aiResponse.contains("开始任务分解")) {
            result.put("nextAction", "PHASE_TRANSITION");
            result.put("suggestedPhase", "task");
        } else if (aiResponse.contains("项目计划确认")) {
            result.put("nextAction", "PROJECT_READY");
        }

        return result;
    }

    /**
     * 保存对话消息
     */
    private void saveDialogMessage(String sessionId, String userMessage, String aiResponse, String messageType) {
        String messageId = ec.getEntity().sequencedIdPrimary("org.moqui.mcp.McpDialog."+sessionId,null,10L);
        ec.getService().sync().name("create#McpDialogMessage").parameters(Map.of(
                "messageId", messageId,
                "sessionId", sessionId,
                "messageType", messageType,
                "content", userMessage,
                "aiResponse", aiResponse,
                "processedDate", ec.getUser().getNowTimestamp()
        )).call();
    }

    /**
     * 获取阶段描述
     */
    private String getPhaseDescription(String phase) {
        return switch (phase) {
            case "requirement" -> "需求收集确认";
            case "design" -> "功能设计";
            case "task" -> "任务分解";
            default -> "未知阶段";
        };
    }

    /**
     * 转义JSON字符串
     */
    private String escapeJson(String input) {
        return input.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}