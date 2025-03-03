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
import java.util.Calendar

class StockQueryWithAnalysis {

    static void main(String[] args) {
        queryStockData()
    }

    static void queryStockData() {
        long startTime = System.currentTimeMillis() // 记录开始时间
        long timeoutMillis = 60000 // 60秒超时

        try {
            println "🔍 开始查询 ChromaDB..."
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            // **ChromaDB 查询**
            def client = new Client(System.getProperty("CHROMA_URL"))
            def collection = client.getCollection("stock-data", new OllamaEmbeddingFunction())
            def query = "002602 ST华通 日K线数据"
            def qr = collection.query([query], 1000, null, null, null)

            if (qr.documents && !qr.documents.isEmpty()) {
                def rawDataList = qr.documents[0] // 获取数据
                println "📊 原始数据: ${rawDataList.size()} 条"

                // **超时检查**
                if (System.currentTimeMillis() - startTime > timeoutMillis) {
                    println "⚠️ 超时退出，数据处理过慢"
                    return
                }

                def recentData = extractRecentKLineData(rawDataList)
                if (!recentData.isEmpty()) {
                    println "✅ 成功提取 ${recentData.size()} 条数据"
                    // 调用 Ollama API
                    def host = "http://localhost:11434/"
                    def ollamaAPI = new OllamaAPI(host)
                    ollamaAPI.setRequestTimeoutSeconds(600)

                    def builder = OllamaChatRequestBuilder.getInstance("deepseek-r1")
                    def requestModel = builder
                            .withMessage(OllamaChatMessageRole.SYSTEM, "你是一个股票分析助手，能根据股票的历史数据进行趋势分析并给出未来价格预测建议。请分析提供的数据，描述价格趋势，并预测未来一周的股价走势。")
                            .withMessage(OllamaChatMessageRole.USER,
                                    "以下是002602 ST华通 最近的日K线数据，请分析其价格趋势并预测未来一周的股价走势：\n" +
                                            recentData.join("\n"))
                            .build()

                    def chatResult = ollamaAPI.chat(requestModel)
                    println "股票分析结果：\n${chatResult.response}"
                } else {
                    println "⚠️ 没有符合条件的数据"
                }
            } else {
                println "❌ 未找到数据"
            }
        } catch (Exception e) {
            e.printStackTrace()
            System.err.println("❌ 查询过程中发生错误！")
        }
    }


    static List<String> extractRecentKLineData(rawDataList) {
        // 初始化entries列表
        def entries = []

        // （保持原有header和calendar初始化代码）
        def header = "日期\t开盘价\t最高价\t最低价\t收盘价\t成交量\t成交额"
        // 时间范围计算
        Calendar calendar = Calendar.getInstance()
        long currentMillis = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -3)
        long threeMonthsAgoMillis = calendar.timeInMillis

        // 数据预处理
        def dataLines = rawDataList instanceof List ? rawDataList.flatten() : []

        dataLines.each { record ->
            try {
                def parts = record.split("\t")
                if (parts.size() < 7) {
                    System.err.println "⚠️ 字段不足：${record.take(20)}..."
                    return
                }

                String dateStr = parts[0].trim()
                if (!dateStr.matches(/^\d{4}\/\d{2}\/\d{2}$/)) {
                    System.err.println "❌ 无效日期格式：$dateStr"
                    return
                }

                Date parsedDate = new SimpleDateFormat("yyyy/MM/dd").parse(dateStr)
                long dateMillis = parsedDate.time

                if (dateMillis < threeMonthsAgoMillis) {
                    return
                }

                entries << [dateMillis: dateMillis, line: record.trim()]
            } catch (Exception e) {
                System.err.println "🔥 处理异常：${e.message} (数据：${record.take(30)})"
            }
        }

        // ==== 添加空值检查 ====
        if (entries.isEmpty()) {
            println "⚠️ 未找到有效数据条目"
            return []
        }

        // ==== 修复排序语法 ====
        entries.sort { a, b ->
            b.dateMillis <=> a.dateMillis  // 显式比较
        }

        // ==== 修复数据截取方式 ====
        def limit = Math.min(60, entries.size())
        def recentData = entries[0..<limit].collect { it.line }

        // （保持添加header逻辑）
        recentData.add(0, header.trim())

        return recentData
    }


    static long parseDateToMillis(String dateStr) {
        if (!dateStr || !dateStr.matches("\\d{4}/\\d{2}/\\d{2}")) {
            System.err.println("⚠️ 无效日期格式: $dateStr")
            return 0
        }

        def sdf = new SimpleDateFormat("yyyy/MM/dd")
        try {
            return sdf.parse(dateStr).time
        } catch (ParseException e) {
            System.err.println("❌ 日期解析失败: $dateStr")
            return 0
        }
    }

}
