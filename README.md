## MCP核心功能

### 消息处理流程
```mermaid
sequenceDiagram
    participant WeChat
    participant MCP
    participant Ollama
    participant MoquiERP
    
    WeChat->>MCP: 用户消息(XML)
    MCP->>Ollama: 意图识别请求
    Ollama-->>MCP: 意图分类结果
    alt ERP操作
        MCP->>MoquiERP: 调用ServiceFacade
        MoquiERP-->>MCP: 操作结果
    else AI查询
        MCP->>ChromaDB: 向量检索
        ChromaDB-->>MCP: 参考数据
        MCP->>Ollama: 生成回答
    end
    MCP->>WeChat: 最终响应
    


