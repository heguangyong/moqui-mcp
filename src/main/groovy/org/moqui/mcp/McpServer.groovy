package org.moqui.mcp

import org.moqui.context.ExecutionContext
import io.modelcontextprotocol.sdk.McpServer
import io.modelcontextprotocol.sdk.server.StdioServerTransport
import io.modelcontextprotocol.sdk.Tool

class McpServer {
    static void start(ExecutionContext ecfi) {
        // Load MCP tools from database
        def tools = []
        ecfi.entity.find("org.moqui.mcp.McpTool").list().each { tool ->
            tools << new Tool(
                    tool.toolName,
                    tool.description,
                    tool.inputSchema,
                    { exchange, args ->
                        def result = ecfi.service.sync().name(tool.serviceName)
                                .parameters(args).call()
                        return new Tool.CallToolResponse(content: [[type: "text", text: result.toString()]])
                    }
            )
        }

        // Start MCP server
        def server = McpServer.sync(new StdioServerTransport())
                .serverInfo("moqui-mcp", "1.0.0")
                .addTools(tools)
                .build()
        server.start()
    }
}