package org.moqui.mcp.core

import org.moqui.context.ExecutionContext
import tech.amikos.chromadb.Client
import io.github.ollama4j.OllamaAPI

class McpCoordinator {
    // 依赖注入
    private final ExecutionContext ec
    private final Client chromaClient
    private final OllamaAPI ollamaClient

    McpCoordinator(ExecutionContext ec) {
        this.ec = ec
        this.chromaClient = new Client(ec.getTool("ChromaDBConfig"))
        this.ollamaClient = new OllamaAPI(ec.getConf("ollama.url"))
    }

    // 消息处理主流程
    Map processMessage(Map input) {
        // Step 1: 意图识别
        String intent = detectIntent(input.text)

        // Step 2: 路由决策
        switch(intent) {
            case "ERP_OPERATION":
                return handleErpOperation(input)
            case "AI_QUERY":
                return handleAiQuery(input)
            default:
                return handleFallback(input)
        }
    }

    private String detectIntent(String text) {
        // 使用Ollama进行意图分类
        def response = ollamaClient.generate("llama3.2", """
            Classify the intent of this ERP-related query:
            ${text}
            Options: [ERP_OPERATION, AI_QUERY, OTHER]
        """)
        return response.trim()
    }

    private Map handleErpOperation(Map input) {
        // 调用Moqui服务门面
        return ec.service.sync().name("mcp#executeErpCommand")
                .parameters(input)
                .call()
    }
}