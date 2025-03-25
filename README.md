## A moqui-wechat component

To install run (with moqui-framework):

    $ ./gradlew getComponent -Pcomponent=moqui-wechat

To test run(use --tests ""*WechatServices" match the case)：

    $ ./gradlew :runtime:component:moqui-wechat:test --tests "*WechatServices"


To build the `moqui-wechat` component and integrate a suitable AI tool, here are the steps and AI options you should consider:

To integrate **Ollama with Llama 3.1** into your **moqui-wechat** component using the **ollama4j plugin**, here is a refined and clear description of the process:

---

### Step 1: Choosing Ollama with Llama 3.1
You’ve selected **Ollama with Llama 3.1** as your private AI service due to its data privacy advantages and strong natural language understanding. This ensures all data interactions remain secure and within your infrastructure, making it ideal for sensitive ERP environments like Moqui.

#### Advantages:
- **Data Privacy**: Runs on your infrastructure, ensuring full control over ERP data.
- **Customization**: Llama 3.1 can be fine-tuned with domain-specific knowledge, allowing it to provide accurate responses tailored to Moqui’s ERP functionalities.
- **Scalable**: Supports private training, so you can periodically update the model with new ERP data as business needs evolve.

### Step 2: Integration Using ollama4j Plugin
You will integrate **Ollama** into **Moqui-WeChat** via the **ollama4j** plugin, which facilitates API interactions between the WeChat interface, Moqui’s ERP, and Ollama’s AI capabilities.

1. **WeChat Request Handler**:
   - Create a **request handler** in Moqui that captures questions from WeChat users, authenticates them, retrieves user roles, and sends the query to the Llama 3.1 model via the ollama4j plugin.
   - Example: A WeChat user asks about their department’s inventory status. Moqui fetches the user's role and filters the request based on their permissions before querying Llama 3.1.

2. **Response Processing and Filtering**:
   - After receiving the AI’s response, implement logic in Moqui to filter the information based on the user’s permissions (role-based access), ensuring users see only the data they are authorized to access.

3. **API-Based Interaction**:
   - Use the **ollama4j** plugin to handle API calls between Moqui and Ollama. When a WeChat user asks a question, the plugin sends the query to the Llama 3.1 model and returns the filtered result to Moqui for further processing before displaying it on WeChat.

### Step 3: Customization for Role-Based Access
Moqui’s **Party Authority** system must be integrated with the AI responses to ensure that users only see information permitted by their roles.

- **Role and Permission Mapping**: Each WeChat user’s role (e.g., warehouse manager) is mapped to specific permissions within Moqui’s Party Authority framework.
- **Response Filtering**: Moqui’s service layer filters the AI responses based on these permissions, allowing the AI to retrieve only the relevant data.

### Step 4: Data Training and Maintenance
To ensure the AI stays updated with current ERP data, you will need a regular training schedule for the Llama 3.1 model.

- **Automated Data Updates**: Build a mechanism within Moqui to periodically extract updated ERP data (e.g., inventory, financial reports) and use it to retrain the Llama 3.1 model.
- **Data Privacy Compliance**: As you own the infrastructure, Ollama can securely handle sensitive ERP data without concerns over third-party data exposure.

### Step 5: Implementation Plan

1. **API Setup**: Use the **ollama4j plugin** to establish API connections between Moqui, WeChat, and Ollama, enabling smooth data flow for natural language queries.
2. **Role-Based Filtering**: Implement Moqui’s logic for filtering responses based on the user’s Party Authority roles.
3. **Regular Data Training**: Build a pipeline that regularly trains Llama 3.1 with ERP updates to ensure accurate and current AI responses.

---

This approach enables a private, secure, and scalable AI-powered WeChat interaction system within the Moqui ERP environment using **Ollama with Llama 3.1** and the **ollama4j plugin**.

### WeChat public account AI integration
pay attention to the model llama version's params difference. llama3.1 / llama3.2
need update the ollama jar for the new version of the model.

- [x] call local ollama server with model llama3.2
  ```
  curl -X POST http://localhost:11434/api/generate \
     -H "Content-Type: application/json" \
     -d '{
           "model": "llama3.2",
           "prompt": "Hello, how are you?",
           "temperature": 0.7,
           "max_tokens": 100
         }'
  ```
- [x] call the remote ollama server with model llama3.1
  ```
  curl http://localhost:11434/api/generate -d '{
  "model": "llama3.1",
  "prompt": "Why is the sky blue?"
  }' -H "Content-Type: application/json"
  ```
- [x] call remote ollama server from local
  ```
  ssh -L 11434:localhost:11434 root@192.168.0.141   
  curl http://localhost:11434/api/generate -d '{
  "model": "llama3.1",
  "prompt": "Why is the sky blue?"
  }' -H "Content-Type: application/json"
  ```
- [x] moqui-wechat call ollama by moqui-wechat
  ```
  ./gradlew :runtime:component:moqui-wechat:test --info
  ```

### Use RAG Flow to make my private domain data have the AI ability

![RAG  FLOW](rag_flow.jpg)

#### Step1:setup python env , chromadb and run as server mode
- require python3+ env: current use python 3.12.3
- python -m venv myenv
- active the env: source ~/myenv/bin/activate\n
- install the chromadb: pip install chromadb
- run chromadb as server mode: chroma run --path /db_path
  ```
  //测试时允许重置数据库
  ALLOW_RESET=TRUE chroma run --path /Users/demo/chromadb
  ```

#### Step2: run ollama and use EmbeddingFunction
```
// 配置 Ollama Embedding Function
System.setProperty("OLLAMA_URL", "http://localhost:11434/api/embed");
EmbeddingFunction ef = new OllamaEmbeddingFunction(WithParam.baseAPI(System.getProperty("OLLAMA_URL")));

// 创建 HR 数据知识库 Collection
Collection collection = client.createCollection("hr-knowledge", null, true, ef);
```
#### Step2: load the test data and run the gradle script
- Use HrDataIndexer to import test data into chromadb
- add dependence of the chromadb:chromadb-java-client.jar 
- use some test data
```
城市	姓名	手机号
上海	闵大	13661410000
上海	李二	15601720001
上海	戚三	15290860002
上海	李四	17721390003
```
### Step3: run the query script
- more info will get more correct
```
// 查询城市和姓名相关信息（例如：查询上海的李蜜的手机号）
String query = "查询东莞常平镇的刘文博手机号";
Collection.QueryResponse qr = collection.query(Arrays.asList(query), 5, null, null, null);
```

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