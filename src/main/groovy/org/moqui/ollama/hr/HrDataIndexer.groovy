package org.moqui.ollama.hr;

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

public class HrDataIndexer {
    public static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client(System.getProperty("CHROMA_URL"));
            client.reset();

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 创建 HR 数据知识库 Collection
            Collection collection = client.createCollection("hr-knowledge", null, true, ef);

            // 读取并解析文件内容
            File dataFile = new File("/Users/demo/Workspace/moqui/runtime/component/moqui-wechat/src/main/resources/20250117.txt");
            List<String> dataContents = extractDataContents(dataFile);

            // 为每条数据生成元数据
            List<Map<String, String>> metadata = new ArrayList<>();
            for (int i = 0; i < dataContents.size(); i++) {
                String[] fields = dataContents.get(i).split("\t"); // Assuming tab-delimited data
                metadata.add(Map.of(
                        "city", fields[0],
                        "name", fields[1],
                        "phone", fields[2]
                ));
            }

            // 并发处理嵌入
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < dataContents.size(); i++) {
                int index = i; // lambda 表达式需要 final 或 effectively final 的变量
                futures.add(executor.submit(() -> {
                    collection.add(
                            null,
                            List.of(metadata.get(index)),
                            List.of(dataContents.get(index)),
                            List.of(UUID.randomUUID().toString())
                    );
                }));
            }

            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            System.out.println("HR 数据已成功构建并存储！");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("HR 数据存储过程中出现错误！");
        }
    }

    /**
     * 提取人员数据文件的内容，每行作为一个数据记录。
     */
    private static List<String> extractDataContents(File dataFile) {
        List<String> contents = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(dataFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    contents.add(line.trim());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("读取 HR 数据文件时发生错误！");
        }
        return contents;
    }
}
