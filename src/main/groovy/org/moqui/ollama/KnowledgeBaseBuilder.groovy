package org.moqui.ollama
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;

public class KnowledgeBaseBuilder {
    public static void main(String[] args) {
        // 初始化 ChromaDB 客户端
        Client client = new Client("http://127.0.0.1:8000");
        client.reset();

        // 配置 Ollama Embedding Function（nomic-embed-text）
        System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
        EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

        // 创建知识库 Collection
        Collection collection = client.createCollection("chanlun-knowledge", null, true, ef);

        // 导入缠论知识（PDF内容分段后存入）
        List<String> pdfContents = Arrays.asList(
                "KDJ指标的计算方法与应用...",
                "趋势线的定义与判断...",
                "买点与卖点的理论模型..."
        );
        List<Map<String, String>> metadata = Arrays.asList(
                Map.of("section", "KDJ", "category", "Indicator Analysis"),
                Map.of("section", "趋势线", "category", "Trend Analysis"),
                Map.of("section", "买卖点", "category", "Trade Strategy")
        );

        // 添加嵌入到知识库
        collection.add(null, metadata, pdfContents, Arrays.asList("1", "2", "3"));
        System.out.println("知识库构建完成！");
    }
}
