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
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client(System.getProperty("CHROMA_URL"));
            client.reset();

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 创建股票数据知识库 Collection
            Collection collection = client.createCollection("stock-knowledge", null, true, ef);

            // 读取并解析股票数据文件
            File dataFile = new File("/Users/demo/Workspace/moqui/runtime/component/moqui-wechat/src/main/resources/stock/SZ#300602.txt");
            List<String> stockData = extractStockData(dataFile);

            // 为每条数据生成元数据
            List<Map<String, String>> metadata = new ArrayList<>();
            for (int i = 0; i < stockData.size(); i++) {
                String[] fields = stockData.get(i).split("\t");

                // 确保每行数据至少有 7 个字段
                if (fields.length >= 7) {
                    metadata.add(Map.of(
                            "date", fields[0],
                            "open", fields[1],
                            "high", fields[2],
                            "low", fields[3],
                            "close", fields[4],
                            "volume", fields[5],
                            "amount", fields[6]
                    ));
                } else {
                    // 打印或记录错误，跳过该行
                    System.err.println("数据格式不正确，跳过该行: " + stockData.get(i));
                }
            }

            // 并发处理嵌入
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < stockData.size(); i++) {
                int index = i; // lambda 表达式需要 final 或 effectively final 的变量
                futures.add(executor.submit(() -> {
                    collection.add(
                            null,
                            List.of(metadata.get(index)),
                            List.of(stockData.get(index)),
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
     * 提取股票数据文件的内容，每行作为一个数据记录。
     */
    private static List<String> extractStockData(File dataFile) {
        List<String> contents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(dataFile), "GB2312"))) {
            String line;
            // 跳过文件的前几行（例如标题行）
            reader.readLine(); // 跳过标题行
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    if (line.split("\t").length < 7) {
                        System.err.println("数据格式错误: " + line); // 输出不正确的行
                    } else {
                        contents.add(line.trim());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("读取股票数据文件时发生错误！");
        }
        return contents;
    }
}
