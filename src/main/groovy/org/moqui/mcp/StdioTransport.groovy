package org.moqui.mcp;

public class StdioTransport implements Transport {
    @Override
    public void start(McpServer server) {
        System.out.println("STDIO Transport started for MCP server");
        // 简化的 STDIO 实现，实际需处理 JSON-RPC 输入输出
    }
}