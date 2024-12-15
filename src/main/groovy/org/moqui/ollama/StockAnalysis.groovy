package org.moqui.ollama

import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.chat.OllamaChatResult


public class StockAnalysis {
    public static void main(String[] args) {
        // 初始化 Ollama API 客户端
        String host = "http://localhost:11434/";
        OllamaAPI ollamaAPI = new OllamaAPI(host);

        // 查询知识库（假设已获取检索结果）
        String retrievedKnowledge = "趋势线的定义与判断...\n买点与卖点的理论模型...";

        // 股票数据（过去半年日 K 数据）
        String kLineData = """
            Date       Open    High    Low     Close
            2024-06-01 100.5   102.3   99.8    101.2
            2024-06-02 101.2   103.0   100.5   102.8
            2024-06-03 102.8   104.5   101.5   103.6
            ...
        """;

        // 构建分析上下文
        String prompt = "以下是缠论的技术分析知识和日 K 数据，请分析这只股票的走势及买卖点：\n\n"
        + "缠论知识：\n" + retrievedKnowledge + "\n\n"
        + "股票日 K 数据：\n" + kLineData;

        // 调用 Mistral 分析
        // start conversation with model
        OllamaChatResult result = ollamaAPI.chat(prompt);
        System.out.println("分析结果：\n" + result.getText());
    }
}
