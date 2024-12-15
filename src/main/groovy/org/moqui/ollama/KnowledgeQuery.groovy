package org.moqui.ollama

import tech.amikos.chromadb.Client
import tech.amikos.chromadb.Collection

public class KnowledgeQuery {
    public static void main(String[] args) {
        // 初始化 ChromaDB 客户端
        Client client = new Client("http://127.0.0.1:8000");
        Collection collection = client.getCollection("chanlun-knowledge");

        // 查询缠论相关内容
        String query = "如何判断趋势线的支撑位和压力位？";
        Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

        // 输出检索结果
        qr.getResults().forEach(result -> {
            System.out.println("相关知识：" + result.getMetadata().get("section") + " -> " + result.getDocument());
        });
    }
}
