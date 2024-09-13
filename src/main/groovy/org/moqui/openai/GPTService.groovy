package org.moqui.openai

import com.unfbx.chatgpt.OpenAiStreamClient
import com.unfbx.chatgpt.entity.chat.ChatCompletion
import com.unfbx.chatgpt.entity.chat.Message
import com.unfbx.chatgpt.sse.ConsoleEventSourceListener
import groovy.json.JsonSlurper
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit;

class GPTService {
    protected final static Logger logger = LoggerFactory.getLogger(GPTService.class)
    private static final String OPENAI_API_KEY = ""
    private static final String OPENAI_API_KEY2 = ""

    private static final String API_URL = "https://api.openai.com/v1/chat/completions"
    private static OkHttpClient client

    // Initialize OkHttp client with optional proxy
    static {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor({ message -> logger.debug(message) })
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        // Optionally set up proxy (if needed)
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890))
        client = new OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .proxy(proxy) // Comment this out if no proxy is needed
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    /**
     * Sends a message to OpenAI GPT model and returns the response.
     * @param userMessage The input message from the user.
     * @param model Optional model name (e.g., gpt-3.5-turbo, gpt-4)
     * @param maxTokens Maximum number of tokens to generate in the completion.
     * @return Generated completion response from GPT.
     */
    static String getChatCompletion(String userMessage, String model = "gpt-3.5-turbo", int maxTokens = 1000) {
        // Create the JSON payload for the chat completion request
        def payload = [
                model: model,
                messages: [[role: "user", content: userMessage]],
                max_tokens: maxTokens,
                temperature: 0.7
        ]
        def requestBody = RequestBody.create(MediaType.parse("application/json"), new JsonSlurper().parseText(payload.toString()))

        // Build the HTTP request to OpenAI
        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $OPENAI_API_KEY")
                .addHeader("Content-Type", "application/json")
                .build()

        try {
            Response response = client.newCall(request).execute()
            if (!response.isSuccessful()) {
                logger.error("Failed to get a response from OpenAI: ${response.code()} - ${response.message()}")
                return "Error: Unable to retrieve response."
            }

            // Parse the response from OpenAI
            def jsonResponse = new JsonSlurper().parseText(response.body().string())
            def chatMessage = jsonResponse.choices?.first()?.message?.content ?: "No response from GPT"
            return chatMessage.trim()
        } catch (IOException e) {
            logger.error("Error in calling OpenAI API: ${e.message}", e)
            return "Error: Failed to communicate with OpenAI."
        }
    }

    /**
     * A test method to demonstrate simple usage.
     */
    static void main(String[] args) {
//        String userMessage = "Tell me a joke."
//        String response = getChatCompletion(userMessage)
//        println("GPT Response: $response")
        // Optionally set up proxy (if needed)
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 7890))
        // Build OkHttpClient with proxy
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .proxy(proxy)
                .build();

        //创建流式输出客户端
        OpenAiStreamClient client = OpenAiStreamClient.builder()
                .apiKey(Arrays.asList(OPENAI_API_KEY,OPENAI_API_KEY2))
                .okHttpClient(httpClient)  // Pass the OkHttpClient with proxy configuration
                .build();
        //聊天
        ConsoleEventSourceListener eventSourceListener = new ConsoleEventSourceListener();
        Message message = Message.builder().role(Message.Role.USER).content("你好！").build();
        ChatCompletion chatCompletion = ChatCompletion.builder().messages(Arrays.asList(message)).build();
        client.streamChatCompletion(chatCompletion, eventSourceListener);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
