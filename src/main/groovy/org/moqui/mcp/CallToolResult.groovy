package org.moqui.mcp;

public class CallToolResult {
    private final Object result;
    private final boolean error;

    public CallToolResult(Object result, boolean error) {
        this.result = result;
        this.error = error;
    }

    public Object getResult() { return result; }
    public boolean isError() { return error; }
}