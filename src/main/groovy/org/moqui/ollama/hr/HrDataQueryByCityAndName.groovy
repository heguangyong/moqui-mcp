package org.moqui.ollama.hr;

import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;

import java.util.Arrays;

public class HrDataQueryByCityAndName {
    public static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client("http://127.0.0.1:8000");

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 获取 HR 数据集合
            Collection collection = client.getCollection("hr-knowledge", ef);

            // 查询城市和姓名相关信息（例如：查询上海的李蜜的手机号）
            String query = "查询东莞常平镇的刘文博手机号";
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

            // 遍历查询结果并输出
            if (qr.getDocuments() != null) {
                for (String doc : qr.getDocuments()) {
                    System.out.println("相关文档：" + doc);  // 输出相关文档（包含城市、姓名和手机号）
                }
            } else {
                System.out.println("未检索到相关文档。");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
