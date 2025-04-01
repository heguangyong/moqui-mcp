package org.moqui.mcp;

import java.util.Map
import java.util.function.BiFunction;

public class Tool {
    private final String name;
    private final String description;
    private final String inputSchema;
    private final BiFunction<Object, Map<String, Object>, CallToolResult> handler;

    public Tool(String name, String description, String inputSchema,
                BiFunction<Object, Map<String, Object>, CallToolResult> handler) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
        this.handler = handler;
    }

    public CallToolResult execute(Object exchange, Map<String, Object> args) {
        return handler.apply(exchange, args);
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInputSchema() { return inputSchema; }
}