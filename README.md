##系统先生」业务架构设计
```mermaid
flowchart TD
    subgraph 生态要素
        P1[当事人管理] -->|身份认证| P2[业务准入]
        P1 -->|关系图谱| P3[交易网络]
        Q1[权限体系] -->|角色定义| Q2[流程控制]
        Q1 -->|访问策略| Q3[数据安全]
        R1[资源中枢] -->|结构化存储| R2[实体仓库]
        R1 -->|非结构化处理| R3[知识库]
    end

    subgraph 运行表现
        S1[健康度监测] -->|企业活跃度| S2[经济仪表盘]
        S3[异常预警] -->|信用评估| S4[风险雷达]
        S5[效率优化] -->|智能调度| S6[资源热力图]
    end

    subgraph 交互界面
        T1[政企门户] -->|数据申报| T2[自动化审核]
        T3[市民APP] -->|服务请求| T4[智能应答]
        T5[IoT控制台] -->|设备监控| T6[预测性维护]
    end

    生态要素 -->|数据供给| 运行表现
    运行表现 -->|决策反馈| 生态要素
    交互界面 -->|人机交互| 生态要素
```

##1. 当事人管理体系  
##2. 权限治理体系
```mermaid
pie
    title 权限控制维度
    "业务领域" : 35
    "数据敏感级" : 25
    "时空约束" : 20
    "操作类型" : 20
```

##🌐 统一业务架构图（Unified Business Architecture）
```mermaid
flowchart TB
subgraph 统一业务架构["统一业务架构 - 系统先生的行为逻辑"]
direction TB

A1[生态角色定义]
A2[业务场景目录]
A3[典型业务流程]
A4[交互方式]
A5[绩效目标]

A1 --> A2
A2 --> A3
A3 --> A4
A4 --> A5

A1 -->|如：企业、员工、服务商、市政| A3
A2 -->|如：招聘、采购、项目、租赁| A3
A3 -->|如：审批流、合同签署、反馈流| A4
A4 -->|如：自然语言、流程按钮、事件驱动| A5
A5 -->|如：成交周期、满意度、活跃度| A1
end


```
##3. 资源中枢架构  
混合存储策略：

| 数据类型         | 存储方案                   | 检索方式           |
|------------------|---------------------------|--------------------|
| 企业注册信息     | PostgreSQL（结构化）       | SQL查询            |
| 合同文档         | MinIO + ChromaDB          | 语义检索           |
| 传感器时序数据   | TimescaleDB               | 时间序列分析       |
| 市民反馈         | Elasticsearch             | 全文检索           |


##1. 企业健康诊断
```mermaid
sequenceDiagram
    企业->>+系统先生: 提交经营数据
    系统先生->>+AI分析引擎: 生成诊断报告
    AI分析引擎-->>-系统先生: 包含风险指标
    系统先生->>+资源调度中心: 获取优化建议
    资源调度中心-->>-系统先生: 推荐方案
    系统先生-->>-企业: 诊断报告+建议
```
##演进路线建议
```mermaid
flowchart LR
    A[基础平台搭建] --> B[数据贯通]
    B --> C[权限治理]
    C --> D[智能增强]
    D --> E[生态扩展]
    
    subgraph 阶段里程碑
        A -->|2023Q4| 完成核心业务迁移
        B -->|2024Q1| 实现80%数据连通
        C -->|2024Q2| 建立动态权限模型
        D -->|2024Q3| 上线预测性维护
        E -->|2025Q1| 开放开发者生态
    end
```

##数字孪生可视化
```mermaid
flowchart LR
    V1[3D城市模型] --> V2[经济脉搏]
    V1 --> V3[资源流动]
    V1 --> V4[异常告警]
    V2 -->|颜色编码| V5[活跃度]
    V3 -->|粒子效果| V6[物流轨迹]
    V4 -->|热力图| V7[风险区域]
```

##🧠 系统治理与健康监控架构（Governance & Observation）
```mermaid
flowchart TB
    subgraph 系统治理与健康监控["系统治理与健康监控 - 系统先生的状态表现"]
        direction TB
        B1[实时监控指标] --> B2[数据采集器]
        B1 --> B3[系统响应分析]
        B1 --> B4[服务健康检查]
        
        B2 -->|如：事件流、接口状态、流量异常| B5[预警机制]
        B3 -->|如：延迟高、出错频繁| B5
        B4 -->|如：调用失败率、数据同步率| B5
        B5 --> B6[调控机制]
        B6 --> B7[规则优化建议]
        B6 --> B8[动态工作流调整]
        B7 -->|反馈给| D[规则引擎]
        B8 -->|反馈给| Q[智能工作流]
    end

```

##🧩 全景架构整合图（系统先生完整图）
```mermaid
flowchart TD
    subgraph 平台架构[统一平台架构 - 系统先生的身体]
        direction TB
        A[业务流程引擎] --> B[服务编排]
        B --> C[实体数据模型]
        C --> D[规则引擎]
        D --> E[事务管理]
        E --> F[(统一数据仓库)]
    end

    subgraph 集成架构[外部资源集成 - 系统的感知系统]
        direction TB
        G[ERP适配器] --> H[数据标准化管道]
        I[CRM适配器] --> H
        J[IoT网关] --> H
        K[第三方API] --> H
        H -->|ETL| F
        H -->|实时消息| M[消息总线]
    end

    subgraph 智能架构[大模型智能协同 - 系统的思考能力]
        direction TB
        L[自然语言接口] --> N[AI决策中心]
        O[预测分析引擎] --> N
        P[知识图谱] --> N
        N --> Q[智能工作流]
        Q -->|优化建议| B
        Q -->|动态规则| D
    end

    F --> N
    M --> N
    N --> B
    B --> G
    B --> K

    subgraph 业务架构[统一业务架构 - 系统的行为逻辑]
        direction TB
        A1[生态角色] --> A2[业务场景]
        A2 --> A3[业务流程]
        A3 --> A4[交互方式]
        A4 --> A5[绩效目标]
    end

    subgraph 治理架构[系统治理与监控 - 系统的自我调节]
        direction TB
        B1[监控指标] --> B2[采集器]
        B2 --> B5[预警机制]
        B5 --> B6[调控机制]
        B6 --> B7[规则反馈]
        B6 --> B8[流程调整]
        B7 --> D
        B8 --> Q
    end

    F --> B1
    Q --> A3
    A3 --> B1
```

##统一平台架构设计
```mermaid
flowchart TD
    subgraph 内部系统[结构化统一信息化系统 - Moqui Core]
        direction TB
        A[业务流程引擎] -->|执行| B[服务编排]
        B --> C[实体数据模型]
        C --> D[规则引擎]
        D --> E[事务管理]
        E --> F[(统一数据仓库)]
    end

    subgraph 资源集成[外部资源集成 - Moqui-Camel]
        direction TB
        G[ERP系统适配器] --> H[数据标准化管道]
        I[CRM系统适配器] --> H
        J[IoT设备网关] --> H
        K[第三方API] --> H
        H -->|ETL| F
        H -->|实时消息| M[消息总线]
    end

    subgraph 智能协同[大模型生态 - Moqui-MCP]
        direction TB
        L[自然语言接口] --> N[AI决策中心]
        O[预测分析引擎] --> N
        P[知识图谱] --> N
        N --> Q[智能工作流]
        Q -->|优化建议| B
        Q -->|动态规则| D
    end

    F -->|结构化数据| N
    M -->|事件驱动| N
    N -->|增强决策| B
    B -->|执行结果| G
    B -->|服务调用| K
```

## MCP核心功能
```mermaid
flowchart TD
subgraph MoquiFramework
MCP[moqui-mcp组件]
end

    subgraph MCP内部结构
        direction TB
        MCP --> Tools[工具层]
        MCP --> Services[服务层]
        MCP --> Entities[实体层]
        MCP --> Scripts[脚本层]
    end

    Tools --> Ollama4J
    Tools --> ChromaClient
    Services --> AI_Service
    Services --> VectorDB_Service
    Entities --> KnowledgeBase
    Scripts --> InitData
```
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
    




```
## A moqui-wechat component

To install run (with moqui-framework):

    $ ./gradlew getComponent -Pcomponent=moqui-wechat

To test run(use --tests ""*WechatServices" match the case)：

    $ ./gradlew :runtime:component:moqui-wechat:test --tests "*WechatServices"


To build the `moqui-wechat` component and integrate a suitable AI tool, here are the steps and AI options you should consider:

To integrate **Ollama with Llama 3.1** into your **moqui-wechat** component using the **ollama4j plugin**, here is a refined and clear description of the process:


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
