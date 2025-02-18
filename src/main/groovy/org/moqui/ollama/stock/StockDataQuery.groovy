package org.moqui.ollama.stock;

import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

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
            Collection collection = client.getCollection("stock-data", ef);

            // 用户输入查询
            String query = "分析飞荣达这支股票的近期走势";  // 示例输入

            // 解析查询内容（提取股票名称）
            String stockName = parseStockName(query); // 假设解析股票名称
            Date startDate = new Date();  // 使用当前日期

            if (stockName == null) {
                System.out.println("无法解析查询内容，请检查输入！");
                return;
            }

            // 获取最近三个月的日期范围
            Date threeMonthsAgo = getThreeMonthsAgo(startDate);
            String formattedDate = formatDateFilter(threeMonthsAgo);

            // 打印调试信息
            System.out.println("查询条件: 股票名称 = " + stockName + ", 最近三个月开始日期 = " + formattedDate);

            // 执行查询，只传递股票名称
            Collection.QueryResponse qr = collection.query(
                    Arrays.asList(stockName),  // 股票名称
                    60,  // 返回前 60 条结果
                    null,  // 省略额外查询参数
                    null,  // 可选参数
                    null   // 可选参数
            );

            // 获取查询结果并过滤出最近三个月的数据
            if (qr.getDocuments() != null) {
                List<String> recentData = new ArrayList<>();
                for (String doc : qr.getDocuments()) {
                    if (isWithinLastThreeMonths(doc, threeMonthsAgo)) {
                        recentData.add(doc);  // 添加符合条件的数据
                    }
                }

                // 输出结果
                if (!recentData.isEmpty()) {
                    for (String doc : recentData) {
                        System.out.println("相关股票数据：" + doc);
                    }
                } else {
                    System.out.println("未找到最近三个月的相关股票数据。");
                }
            } else {
                System.out.println("未检索到相关股票数据。");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析查询中的股票名称（这里假设查询中包含股票名称）
     */
    private static String parseStockName(String query) {
        // 简单示例：从查询中提取股票名称（例如，直接提取“飞荣达”）
        // 可以使用正则或更复杂的NLP方法来改进此功能
        if (query.contains("飞荣达")) {
            return "飞荣达";  // 返回匹配的股票名称
        }
        return null;  // 如果未匹配到
    }

    /**
     * 格式化时间过滤条件
     */
    private static String formatDateFilter(Date startDate) {
        // 将当前日期转为字符串格式，用于查询过滤
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
        return sdf.format(startDate); // 格式化为 "yyyy/MM/dd"
    }

    /**
     * 计算三个月前的日期
     */
    private static Date getThreeMonthsAgo(Date currentDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(currentDate);
        calendar.add(Calendar.MONTH, -3);  // 设置为三个月前
        return calendar.getTime();
    }

    /**
     * 检查股票数据是否在最近三个月内
     */
    private static boolean isWithinLastThreeMonths(String document, Date threeMonthsAgo) {
        // 从文档中提取日期并与三个月前的日期进行比较
        // 假设每个文档包含日期字段，格式为 "yyyy/MM/dd"

        // 示例，假设文档中有日期字段“日期”，并格式化为String形式
        String dateString = extractDateFromDocument(document);  // 提取日期
        if (dateString == null) {
            return false;  // 如果没有日期，跳过
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
            Date docDate = sdf.parse(dateString);

            // 返回该日期是否在最近三个月内
            return !docDate.before(threeMonthsAgo);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 从文档中提取日期（假设格式固定，简单示例）
     */
    private static String extractDateFromDocument(String document) {
        // 这里假设文档是一个字符串，并且日期格式为 "yyyy/MM/dd"
        // 实际应用中，可能需要解析 JSON 或从结构化数据中提取日期字段
        // 简单示例：假设文档中包含日期字段
        if (document.contains("2025/")) {
            return document.substring(document.indexOf("2025/"), document.indexOf("2025/") + 10);  // 提取日期
        }
        return null;  // 未找到日期
    }
}
