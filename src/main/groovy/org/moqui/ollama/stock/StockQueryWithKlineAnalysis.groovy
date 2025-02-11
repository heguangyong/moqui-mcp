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
import io.github.ollama4j.models.chat.OllamaChatResult;

import java.util.Arrays;
import java.text.ParseException;
import java.text.SimpleDateFormat;

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
            Collection collection = client.getCollection("stock-knowledge", ef);

            // 用户查询条件（例如：查询“飞荣达”股票的分析数据）
            String query = "飞荣达 300602 日K线数据";
            System.out.println("查询条件: " + query); // 打印查询条件
            Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);

            // 处理查询结果
            StringBuilder retrievedData = new StringBuilder();
            if (qr.getDocuments() != null && !qr.getDocuments().isEmpty()) {
                // 打印查询到的文档
                System.out.println("查询到的文档：");
                for (String doc : qr.getDocuments()) {
                    System.out.println(doc); // 打印每一条数据
                    if (doc.contains("飞荣达")) {
                        retrievedData.append(doc).append("\n");
                    }
                }

                // 如果找到了相关数据
                if (retrievedData.length() > 0) {
                    // 调用方法获取最近六个月的日K线数据
                    String stockData = retrievedData.toString();
                    System.out.println("查询到的股票数据：\n" + stockData);

                    // 筛选最近六个月的数据
                    var recentData = extractRecentKLineData(stockData, 6);
                    if (!recentData.isEmpty()) {
                        // 准备 Ollama API 调用
                        String host = "http://localhost:11434/";
                        OllamaAPI ollamaAPI = new OllamaAPI(host);
                        ollamaAPI.setRequestTimeoutSeconds(600); // 设置请求超时时间

                        // 构建聊天请求（包含系统提示和用户消息）
                        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance("deepseek-coder-v2");
                        OllamaChatRequest requestModel = builder
                                .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个股票分析助手，能根据股票的历史数据进行分析，并给出预测建议。")
                                .withMessage(OllamaChatMessageRole.USER,
                                        "以下是用户请求的股票数据分析：\n查询股票：\n飞荣达 (300602)\n\n过去六个月的日K线数据：\n" +
                                                String.join("\n", recentData))
                                .build();

                        // 调用 chat 方法获取查询结果
                        OllamaChatResult chatResult = ollamaAPI.chat(requestModel);
                        System.out.println("股票分析结果：\n" + chatResult.getResponse());
                    } else {
                        System.out.println("未能提取到最近六个月的数据。");
                    }
                } else {
                    System.out.println("未找到该股票的相关数据。");
                }

            } else {
                System.out.println("未找到该股票的相关数据。");
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("查询过程中发生错误！");
        }
    }

    // 筛选最近六个月的K线数据
    private static java.util.List<String> extractRecentKLineData(String stockData, int monthsAgo) {
        String[] lines = stockData.split("\n");
        StringBuilder recentData = new StringBuilder();
        int count = 0;

        // 当前时间（这里假设为当前系统日期）
        long currentTime = System.currentTimeMillis();

        // 每月大约20个交易日，假设为120个交易日，半年大概120个交易日
        long sixMonthsInMillis = 120 * 24 * 60 * 60 * 1000L; // 120个交易日的毫秒数

        System.out.println("当前时间：" + currentTime); // 输出当前时间（毫秒）

        // 通过时间戳来筛选数据
        for (int i = lines.length - 1; i >= 0 && count < 120; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) {
                String[] parts = line.split("\t"); // 假设以tab分隔
                if (parts.length >= 6) {
                    String date = parts[0]; // 取日期（假设格式为 yyyy/MM/dd）
                    long dateMillis = parseDateToMillis(date); // 将日期转换为毫秒
                    System.out.println("处理日期：" + date + " 转换为毫秒：" + dateMillis);

                    // 判断该日期是否在六个月范围内
                    if (currentTime - dateMillis <= sixMonthsInMillis) {
                        recentData.append(line).append("\n");
                        count++;
                    }
                }
            }
        }

        // 输出筛选后的数据
        System.out.println("筛选后的数据：\n" + recentData.toString());

        // 返回过去六个月的K线数据
        return Arrays.asList(recentData.toString().split("\n"));
    }

    // 将日期转换为毫秒
    private static long parseDateToMillis(String dateStr) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd"); // 假设日期格式为 "yyyy/MM/dd"
        try {
            return sdf.parse(dateStr).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
