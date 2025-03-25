package org.moqui.mcp.wechat

import org.moqui.mcp.WechatMsg

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.CompletableFuture

@CompileStatic
class McpWechatServlet extends HttpServlet {
    // 改造后的异步处理
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        McpRequest mcpReq = convertWechatMessage(req)

        // 异步提交到MCP处理队列
        CompletableFuture<McpResponse> future = McpQueue.submit(mcpReq)

        // 先返回临时响应
        sendTempResponse(resp, "请求已接收，处理中...")

        // 异步处理完成后发送最终结果
        future.thenAccept(mcpResp -> {
            sendWechatMessage(mcpReq.userId, mcpResp.content)
        })
    }

    private void sendTempResponse(HttpServletResponse resp, String text) {
        // 符合微信5秒响应要求的快速回复
        resp.setContentType("text/xml")
        new WechatMsg(...).send().withWriter(resp.writer)
    }
}