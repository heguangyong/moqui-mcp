package org.moqui.ollama;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import tech.amikos.chromadb.Client;
import tech.amikos.chromadb.Collection;
import tech.amikos.chromadb.embeddings.EmbeddingFunction;
import tech.amikos.chromadb.embeddings.WithParam;
import tech.amikos.chromadb.embeddings.ollama.OllamaEmbeddingFunction;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class KnowledgeBaseBuilder {
    public static void main(String[] args) {
        try {
            // 初始化 ChromaDB 客户端
            System.setProperty("CHROMA_URL", "http://127.0.0.1:8000");
            Client client = new Client(System.getProperty("CHROMA_URL"));
            client.reset();

            // 配置 Ollama Embedding Function
            System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
            EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

            // 创建知识库 Collection
            Collection collection = client.createCollection("chanlun-knowledge", null, true, ef);

            // 提取 PDF 内容
            File pdfFile = new File("/Users/demo/Workspace/moqui/runtime/component/moqui-wechat/src/main/resources/chanlun.pdf");
            List<String> pdfContents = extractPdfContents(pdfFile);

            // 为每段内容生成元数据
            List<Map<String, String>> metadata = new ArrayList<>();
            for (int i = 0; i < pdfContents.size(); i++) {
                metadata.add(Map.of(
                        "section", "Section " + (i + 1),
                        "category", "Chan Theory"
                ));
            }

            // 并发处理嵌入
            ExecutorService executor = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < pdfContents.size(); i++) {
                int index = i; // lambda 表达式需要 final 或 effectively final 的变量
                futures.add(executor.submit(() -> {
                    collection.add(
                            null,
                            List.of(metadata.get(index)),
                            List.of(pdfContents.get(index)),
                            List.of(UUID.randomUUID().toString())
                    );
                }));
            }

            // 等待所有任务完成
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            System.out.println("知识库构建完成！");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("知识库构建过程中出现错误！");
        }
    }

    /**
     * 提取 PDF 文件内容，并按段落切分。
     */
    private static List<String> extractPdfContents(File pdfFile) {
        List<String> contents = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            int numberOfPages = document.getNumberOfPages();

            // 按页提取内容并进一步分段
            for (int page = 1; page <= numberOfPages; page++) {
                pdfStripper.setStartPage(page);
                pdfStripper.setEndPage(page);
                String pageContent = pdfStripper.getText(document).trim();

                // 将每页内容按段落切分
                if (!pageContent.isEmpty()) {
                    String[] paragraphs = pageContent.split("\\n+");
                    for (String paragraph : paragraphs) {
                        if (!paragraph.trim().isEmpty()) {
                            contents.add(paragraph.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("解析 PDF 文件时发生错误！");
        }
        return contents;
    }
}
