package org.moqui.mcp.erp

class ErpServiceProxy {
    // 库存操作示例
    @Service
    Map receiveInventory(Map params) {
        return ec.service.sync().name("mantle.receipt.PurchaseReceipt")
                .parameters([
                        poNumber: params.poNo,
                        items: params.items.collect {
                            [productId: it.productId, quantity: it.qty]
                        }
                ])
                .call()
    }

    // 带补偿的事务操作
    @Retryable(maxAttempts=3)
    Map adjustInventory(String productId, int delta) {
        try {
            return ec.service.run("inventory#adjustStock", productId, delta)
        } catch (Exception e) {
            // 自动触发补偿操作
            ec.service.run("inventory#rollbackAdjust", productId, delta)
            throw e
        }
    }
}