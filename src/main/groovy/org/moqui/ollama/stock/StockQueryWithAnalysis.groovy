package org.moqui.ollama.stock;

import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;
import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatResult

import java.text.ParseException
import java.text.SimpleDateFormat

public class StockQueryWithAnalysis {

    public static void main(String[] args) {
        queryStockData();
    }

    public static void queryStockData() {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client("http://127.0.0.1:8000");

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 获取股票数据集合
            Collection collection = client.getCollection("stock-data", ef);

            // 查询所有数据
            String query = "飞荣达 300602 日K线数据";
            System.out.println("查询条件: " + query);
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), null, null, null, null);

            // 处理查询结果
            StringBuilder retrievedData = new StringBuilder();
            if (qr.getDocuments() != null && !qr.getDocuments().isEmpty()) {
                System.out.println("查询到的文档：");
                for (String doc : qr.getDocuments()) {
                    System.out.println(doc);
                    retrievedData.append(doc).append("\n");
                }

                String stockData = retrievedData.toString();
                System.out.println("查询到的股票数据：\n" + stockData);

                // 筛选最近六个月的数据
                List<String> recentData = extractRecentKLineData(stockData, 6);
                if (!recentData.isEmpty()) {
                    // 调用 Ollama API
                    String host = "http://localhost:11434/";
                    OllamaAPI ollamaAPI = new OllamaAPI(host);
                    ollamaAPI.setRequestTimeoutSeconds(600);

                    OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("deepseek-r1");
                    OllamaChatRequest requestModel = builder
                            .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个股票分析助手，能根据股票的历史数据进行分析，并给出预测建议。")
                            .withMessage(OllamaChatMessageRole.USER,
                                    "以下是用户请求的股票数据分析：\n查询股票：\n飞荣达 (300602)\n\n过去三个月的日K线数据：\n" +
                                            String.join("\n", recentData))
                            .build();

                    OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
                    System.out.println("股票分析结果：\n" + chatResult.getResponse());
                } else {
                    System.out.println("未能提取到最近三个月的数据。");
                }
            } else {
                System.out.println("未找到该股票的相关数据。");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("查询过程中发生错误！");
        }
    }

    private static List<String> extractRecentKLineData(String stockData, int monthsAgo) {
        String[] lines = stockData.split("\n");
        List<StockEntry> entries = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\t");
            if (parts.length >= 7) {
                String date = parts[0];
                long dateMillis = parseDateToMillis(date);
                entries.add(new StockEntry(dateMillis, line));
            }
        }

        // 按日期降序排序
        entries.sort((e1, e2) -> Long.compare(e2.dateMillis, e1.dateMillis));

        // 取最近60条（约三个月）
        int limit = Math.min(60, entries.size());
        List<String> recentData = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            recentData.add(entries.get(i).line);
        }

        System.out.println("筛选后的数据：\n" + String.join("\n", recentData));
        return recentData;
    }

    private static long parseDateToMillis(String dateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        try {
            return sdf.parse(dateStr).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    private static class StockEntry {
        long dateMillis;
        String line;

        StockEntry(long dateMillis, String line) {
            this.dateMillis = dateMillis;
            this.line = line;
        }
    }
}