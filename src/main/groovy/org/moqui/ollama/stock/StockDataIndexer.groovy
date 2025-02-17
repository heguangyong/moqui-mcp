package org.moqui.ollama.stock;

import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class StockDataIndexer {
    public static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端（与原代码相同）
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client(System.getProperty("CHROMA_URL"));
            client.reset();

            // 配置 Ollama Embedding Function（与原代码相同）
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 创建股票数据知识库 Collection
            Collection collection = client.createCollection("stock-data", null, true, ef);

            // 读取并解析股票数据文件
            File dataFile = new File("/Users/demo/Workspace/moqui/runtime/component/moqui-wechat/src/main/resources/stock/SZ#300602.txt");
            List<String> dataContents = extractStockDataContents(dataFile);

            // 为每条股票数据生成元数据
            List<Map<String, String>> metadata = new ArrayList<>();
            for (String content : dataContents) {
                String[] fields = content.split(",");  // 假设使用逗号分隔
                if (fields.length != 6) {
                    System.err.println("无效数据格式: " + content);
                    continue;
                }

                metadata.add(new HashMap<String, String>() {{
                    put("date", fields[0].trim());     // 日期
                    put("open", fields[1].trim());     // 开盘价
                    put("high", fields[2].trim());     // 最高价
                    put("low", fields[3].trim());     // 最低价
                    put("close", fields[4].trim());    // 收盘价
                    put("volume", fields[5].trim());   // 成交量
                }});
            }

            // 并发处理嵌入（与原代码逻辑相同）
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < dataContents.size(); i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    collection.add(
                            null,
                            List.of(metadata.get(index)),
                            List.of(dataContents.get(index)),  // 使用原始数据作为文档内容
                            List.of(UUID.randomUUID().toString())
                    );
                }));
            }

            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            System.out.println("股票数据已成功构建并存储！");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("股票数据存储过程中出现错误！");
        }
    }

    /**
     * 提取股票数据文件内容（支持跳过标题行）
     */
    private static List<String> extractStockDataContents(File dataFile) {
        List<String> contents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                // 跳过标题行（例如：Date,Open,High,Low,Close,Volume）
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                contents.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("读取股票数据文件时发生错误！");
        }
        return contents;
    }
}