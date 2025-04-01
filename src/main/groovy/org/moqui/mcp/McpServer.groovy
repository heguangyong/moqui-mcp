package org.moqui.mcp

import org.moqui.context.ExecutionContext

class McpServer {
    /*
     * Starts the MCP server, exposing Moqui services as MCP tools.
     * @param ecfi ExecutionContext for accessing Moqui services and entities.
     */
    static void start(ExecutionContext ecfi) {
        // Load all MCP tools from the database
        def tools = []
        ecfi.entity.find("org.moqui.mcp.McpTool").list().each { tool ->
            tools << new org.moqui.mcp.Tool(
                    tool.toolName,
                    tool.description,
                    tool.inputSchema,
                    { exchange, args ->
                        def result = ecfi.service.sync().name(tool.serviceName)
                                .parameters(args).call()
                        return new org.moqui.mcp.CallToolResult(result, false)
                    }
            )
        }

        // Start the MCP server with STDIO transport
        def server = org.moqui.mcp.McpServer.sync(new org.moqui.mcp.StdioTransport())
                .serverInfo("moqui-mcp", "1.0.0")
                .addTools(tools)
                .build()
        server.start()
    }
}