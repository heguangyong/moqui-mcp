package org.moqui.ollama.stock;

import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;

import java.util.Arrays;

public class StockDataQuery {
    public static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client("http://127.0.0.1:8000");

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 获取股票数据集合
            Collection collection = client.getCollection("stock-knowledge", ef);

            // 查询股票数据（例如：查询“飞荣达”股票的相关数据）
            String query = "飞荣达";  // 查询的股票名称或股票代码
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

            // 遍历查询结果并输出
            if (qr.getDocuments() != null) {
                for (String doc : qr.getDocuments()) {
                    System.out.println("相关股票数据：" + doc);
                }
            } else {
                System.out.println("未检索到相关股票数据。");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
