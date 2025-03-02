package org.moqui.ollama.stock

import tech.amikos.chromadb.Client
import tech.amikos.chromadb.Collection
import tech.amikos.chromadb.embeddings.EmbeddingFunction
import tech.amikos.chromadb.embeddings.WithParam
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern
import java.nio.file.Files

class BatchStockDataIndexer {
    // 配置常量
    static final String STOCK_DIR = "/Users/demo/Workspace/moqui/runtime/component/moqui-wechat/src/main/resources/stock/"
    static final Pattern STOCK_CODE_PATTERN = Pattern.compile('(?:SH|SZ)#(\\d+)\\.txt$', Pattern.CASE_INSENSITIVE)

    static final int THREAD_POOL_SIZE = 4

    static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000")
            def client = new Client(System.getProperty("CHROMA_URL"))
            client.reset()

            // 配置 Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed")
            def ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")))

            // 创建共享的 Collection
            def collection = client.createCollection("stock-data", null, true, ef)

            // 获取股票文件列表
            def stockDir = new File(STOCK_DIR)
            def stockFiles = stockDir.listFiles({ dir, name -> name.endsWith(".txt") } as FilenameFilter)
            if (!stockFiles) {
                System.err.println("未找到股票数据文件")
                return
            }

            println "发现股票文件数量: ${stockFiles.length}"

            // 创建线程池
            def executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
            def totalSuccess = new AtomicInteger(0)

            // 处理每个股票文件
            stockFiles.each { file ->
                def stockCode = extractStockCode(file.name)
                if (!stockCode) {
                    System.err.println("跳过无效文件名: ${file.name}")
                    return
                }

                executor.execute {
                    try {
                        def count = processStockFile(file, stockCode, collection)
                        totalSuccess.addAndGet(count)
                        printf "[%s] 处理完成，成功插入 %d 条数据%n", stockCode, count
                    } catch (e) {
                        System.err.printf "[%s] 处理失败: %s%n", stockCode, e.message
                    }
                }
            }

            // 关闭线程池并等待完成
            executor.shutdown()
            while (!executor.isTerminated()) {
                sleep(1000)
            }

            printf "全部处理完成！总共成功插入 %d 条数据%n", totalSuccess.get()
        } catch (e) {
            e.printStackTrace()
            System.err.println("主流程执行失败！")
        }
    }

    static int processStockFile(File file, String stockCode, Collection collection) {
        def successCount = new AtomicInteger(0)
        try {
            // 读取文件内容
            def dataContents = extractStockDataContents(file)
            printf "[%s] 读取到 %d 条数据%n", stockCode, dataContents.size()

            // 准备元数据和内容
            def metadataList = []
            dataContents.each { content ->
                def fields = content.split("\t")
                if (fields.length != 7) return

                metadataList << [
                        stock_code: stockCode,
                        date: fields[0].trim(),
                        open: fields[1].trim(),
                        high: fields[2].trim(),
                        low: fields[3].trim(),
                        close: fields[4].trim(),
                        volume: fields[5].trim(),
                        turnover: fields[6].trim()
                ]
            }

            // 批量插入数据
            def fileExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)
            def futures = []

            dataContents.eachWithIndex { content, index ->
                futures << fileExecutor.submit {
                    try {
                        collection.add(
                                null,
                                [metadataList[index]],
                                [content],
                                [UUID.randomUUID().toString()]
                        )
                        successCount.incrementAndGet()
                    } catch (e) {
                        System.err.printf "[%s] 插入失败第 %d 条: %s%n", stockCode, index, e.message
                    }
                }
            }

            // 等待当前文件任务完成
            futures.each { it.get() }
            fileExecutor.shutdown()
        } catch (e) {
            e.printStackTrace()
        }
        return successCount.get()
    }

    static String extractStockCode(String filename) {
        def matcher = STOCK_CODE_PATTERN.matcher(filename)
        matcher.find() ? matcher.group(1) : null
    }

    static List<String> extractStockDataContents(File dataFile) {
        def contents = []
        try (def reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(dataFile), "GB2312"))) {
            def skipHeader = true
            reader.eachLine { line ->
                line = line.trim()
                if (line.isEmpty() || line.startsWith("数据来源")) return
                if (skipHeader) {
                    skipHeader = false
                    return
                }
                contents << line
            }
        } catch (e) {
            e.printStackTrace()
        }
        return contents
    }
}