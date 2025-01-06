package org.moqui.ollama;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;

public class StockAnalysis {

    public static void main(String[] args) {
        analyzeStock();
    }

    public static void analyzeStock() {
        // 配置 Ollama API 的主机地址
        String host = "http://localhost:11434/";

        // 初始化 Ollama API 客户端
        OllamaAPI ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setRequestTimeoutSeconds(120); // 设置请求超时时间

        // 查询知识库（假设已获取检索结果）
        String retrievedKnowledge = """
            趋势线的定义与判断...
            买点与卖点的理论模型...
        """;

        // 股票数据（过去半年日 K 数据）
        String kLineData = """
            Date       Open    High    Low     Close
            2024-06-01 100.5   102.3   99.8    101.2
            2024-06-02 101.2   103.0   100.5   102.8
            2024-06-03 102.8   104.5   101.5   103.6
            ...
        """;

        // 构建聊天请求（含系统提示和用户消息）
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("mistral"); // 模型名称为 "mistral"
        OllamaChatRequest requestModel = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个使用缠论进行股票分析的智能分析师。")
                .withMessage(OllamaChatMessageRole.USER,
                        "以下是缠论的技术分析知识和日 K 数据，请分析这只股票的走势及买卖点：\n\n" +
                                "缠论知识：\n" + retrievedKnowledge + "\n\n" +
                                "股票日 K 数据：\n" + kLineData)
                .build();

        try {
            // 调用 chat 方法获取分析结果
            OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
            System.out.println("分析结果：\n" + chatResult.getResponse());
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("股票分析时发生错误！");
        }
    }
}
