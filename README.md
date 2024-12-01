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