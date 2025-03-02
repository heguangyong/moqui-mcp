package org.moqui.ollama.stock

import tech.amikos.chromadb.Client
import tech.amikos.chromadb.Collection
import tech.amikos.chromadb.embeddings.EmbeddingFunction
import tech.amikos.chromadb.embeddings.WithParam
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.chat.OllamaChatRequest
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder
import io.github.ollama4j.models.chat.OllamaChatMessageRole
import io.github.ollama4j.models.chat.OllamaChatResult

import java.text.SimpleDateFormat
import java.text.ParseException

class StockQueryWithAnalysis {

    static void main(String[] args) {
        queryStockData()
    }

    static void queryStockData() {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000")
            def client = new Client(System.getProperty("CHROMA_URL"))
            println "ChromaDB 客户端初始化完成"

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed")
            def ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")))
            println "Ollama Embedding Function 初始化完成"

            // 获取股票数据集合
            def collection = client.getCollection("stock-data", ef)
            println "获取 Collection 'stock-data'"

            // 查询所有数据
            def query = "002602 ST华通 日K线数据"
            println "查询条件: $query"
            def qr = collection.query([query], 1000, null, null, null)

            // 处理查询结果
            if (qr.documents && !qr.documents.isEmpty()) {
                println "查询到的文档条数：${qr.documents.size()}"
                def rawDataList = qr.documents[0] // 获取包含多行数据的列表
                println "rawData 类型：${rawDataList.getClass()}"
                println "原始数据：$rawDataList"

                // 筛选最近60条数据
                def recentData = extractRecentKLineData(rawDataList)
                if (!recentData.isEmpty()) {
                    // 调用 Ollama API
                    def host = "http://localhost:11434/"
                    def ollamaAPI = new OllamaAPI(host)
                    ollamaAPI.setRequestTimeoutSeconds(600)

                    def builder = OllamaChatRequestBuilder.getInstance("deepseek-r1")
                    def requestModel = builder
                            .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个股票分析助手，能根据股票的历史数据进行趋势分析并给出未来价格预测建议。请分析提供的数据，描述价格趋势，并预测未来一周的股价走势。")
                            .withMessage(OllamaChatMessageRole.USER,
                                    "以下是002602 ST华通 过去三个月的日K线数据（最近60条），请分析其价格趋势并预测未来一周的股价走势：\n" +
                                            recentData.join("\n"))
                            .build()

                    def chatResult = ollamaAPI.chat(requestModel)
                    println "股票分析结果：\n${chatResult.response}"
                } else {
                    println "未能提取到最近三个月的数据。"
                }
            } else {
                println "未找到该股票的相关数据。"
            }

        } catch (Exception e) {
            e.printStackTrace()
            System.err.println("查询过程中发生错误！")
        }
    }

    static List<String> extractRecentKLineData(rawDataList) {
        def entries = []
        def header = rawDataList[0] // 保存标题行

        rawDataList[1..-1].each { record ->
            if (record.trim().isEmpty()) return // 跳过空行
            def parts = record.split("\t")
            if (parts.length >= 7) {
                def date = parts[0].trim()
                def dateMillis = parseDateToMillis(date)
                if (dateMillis > 0) {
                    entries << [dateMillis: dateMillis, line: record.trim()]
                } else {
                    System.err.println("日期解析失败：$date")
                }
            } else {
                System.err.println("数据格式错误：$record")
            }
        }

        // 按日期降序排序
        entries.sort { a, b -> b.dateMillis <=> a.dateMillis }

        // 取最近60条（保留原始数据格式）
        def limit = Math.min(60, entries.size())
        def recentData = entries[0..<limit].collect { it.line }

        // 添加标题行到结果
        recentData.add(0, header.trim())

        println "筛选后的数据（最近${limit}条）：\n${recentData.join('\n')}"
        return recentData
    }

    static long parseDateToMillis(String dateStr) {
        def sdf = new SimpleDateFormat("yyyy/MM/dd")
        try {
            return sdf.parse(dateStr).time
        } catch (ParseException e) {
            e.printStackTrace()
            return 0
        }
    }
}