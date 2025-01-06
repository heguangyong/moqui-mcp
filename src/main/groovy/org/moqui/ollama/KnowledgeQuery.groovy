package org.moqui.ollama

import tech.amikos.chromadb.Client
import tech.amikos.chromadb.Collection
import tech.amikos.chromadb.embeddings.EmbeddingFunction
import tech.amikos.chromadb.embeddings.WithParam
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction

public class KnowledgeQuery {
    public static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client("http://127.0.0.1:8000");
            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));
            Collection collection = client.getCollection("chanlun-knowledge",ef);

            // 查询缠论相关内容
            String query = "如何判断趋势线的支撑位和压力位？";
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

            // 遍历查询结果并输出
            if (qr.getDocuments() != null) {
                for (String doc : qr.getDocuments()) {
                    System.out.println("相关文档：" + doc);
                }
            } else {
                System.out.println("未检索到相关文档。");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}