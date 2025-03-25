package org.moqui.mcp.erp

class McpDataIndexer {
    void indexErpData() {
        // 从Moqui动态获取数据
        def products = ec.entity.find("mantle.product.Product").list()

        // 批量生成向量
        chromaClient.batchUpsert(
                ids: products.collect { it.productId },
                documents: products.collect { generateDoc(it) },
                embeddings: ollama.embedBatch(products.collect { it.description })
        )
    }

    private String generateDoc(product) {
        """
        Product: ${product.productName}
        ID: ${product.productId}
        Category: ${product.categoryId}
        Current Stock: ${ec.service.quick().name("getStockLevel")
                .parameter("productId", product.productId).call().quantity}
        """
    }
}