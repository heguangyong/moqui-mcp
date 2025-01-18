package org.moqui.ollama.hr;

import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatResult;

import java.util.Arrays;

public class HrQueryWithKnowledge {

    public static void main(String[] args) {
        queryHrData();
    }

    public static void queryHrData() {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client("http://127.0.0.1:8000");

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 获取 HR 数据集合
            Collection collection = client.getCollection("hr-knowledge", ef);

            // 用户查询条件（例如：查询上海的李蜜的手机号）
            String query = "查询上海的李蜜的手机号";
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

            // 处理查询结果
            StringBuilder retrievedData = new StringBuilder();
            if (qr.getDocuments() != null && !qr.getDocuments().isEmpty()) {
                // 只返回完全匹配的结果，确保查询精确
                for (String doc : qr.getDocuments()) {
                    if (doc.contains("上海") && doc.contains("李蜜")) {
                        retrievedData.append(doc).append("\n");
                    }
                }

                // 如果找到了完全匹配的文档
                if (retrievedData.length() > 0) {
                    // 准备 Ollama API 调用
                    String host = "http://localhost:11434/";
                    OllamaAPI ollamaAPI = new OllamaAPI(host);
                    ollamaAPI.setRequestTimeoutSeconds(600); // 设置请求超时时间

                    // 构建聊天请求（包含系统提示和用户消息）
                    OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("0xroyce/plutus");
                    OllamaChatRequest requestModel = builder
                            .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个人力资源管理助手，能快速根据查询条件返回相关人员的详细信息。")
                            .withMessage(OllamaChatMessageRole.USER,
                                    "以下是根据用户查询的数据：\n" +
                                            "查询条件：\n" + query + "\n\n" +
                                            "人力资源数据：\n" + retrievedData.toString())
                            .build();

                    // 调用 chat 方法获取查询结果
                    OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
                    System.out.println("查询结果：\n" + chatResult.getResponse());
                } else {
                    System.out.println("未找到该人员相关信息。");
                }

            } else {
                System.out.println("未找到该人员相关信息。");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("查询过程中发生错误！");
        }
    }
}
