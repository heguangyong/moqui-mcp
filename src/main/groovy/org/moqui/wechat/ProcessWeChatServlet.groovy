package org.moqui.wechat

import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.response.OllamaAsyncResultStreamer
import io.github.ollama4j.types.OllamaModelType
import org.apache.commons.codec.digest.DigestUtils
import org.moqui.context.ExecutionContextFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ProcessWeChatServlet extends HttpServlet {
    protected final static Logger logger = LoggerFactory.getLogger(ProcessWeChatServlet.class)
    private static final String TOKEN = "moquiwechat"
    private static final String ENCODING_AES_KEY = "06MFgfg3RkV2CfT0lrrS9gZMQOJe5BIp55tNiBHIv26"
    private static final String IV = ENCODING_AES_KEY.substring(0, 16)

    // Create an ExecutorService to handle asynchronous tasks
    private final ExecutorService executorService = Executors.newCachedThreadPool();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String signature = req.getParameter("signature")
        String timestamp = req.getParameter("timestamp")
        String nonce = req.getParameter("nonce")
        String echostr = req.getParameter("echostr")

        if (verifySignature(signature, timestamp, nonce)) {
            resp.writer.write(echostr)
        } else {
            resp.setStatus(HttpServletResponse.SC_FORBIDDEN)
        }
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Step 2: Receive message from WeChat user
        String weChatMessage = request.reader.text;

        // Step 3: Acknowledge receipt quickly to avoid WeChat timeout
        response.getWriter().write("Message received, processing...");

        // Step 4: Submit a task to handle the Ollama async call without blocking the servlet
        executorService.submit(() -> {
            HttpURLConnection connection = null;
            try {
                def recMsg = parseXml(weChatMessage);
                if (recMsg instanceof TextMsg) {
                    TextMsg textMsg = recMsg;
                    String toUser = textMsg.FromUserName;
                    String fromUser = textMsg.ToUserName;

                    // Call the Ollama API asynchronously and send the response to WeChat user
                    String ollamaResponse = callOllamaAsync(weChatMessage);
                    def replyMsg = new TextMsg(toUser, fromUser, ollamaResponse);

                    // Send the constructed XML response (make a POST request to WeChat API)
                    // Get the access token from the WeChatAccessTokenManager
                    ExecutionContextFactory ecf = request.getAttribute("ecf");
                    WeChatAccessTokenManager tokenManager = (WeChatAccessTokenManager) ecf.getTool("WeChatAccessTokenManager");
                    String accessToken = tokenManager.getAccessToken();
                    connection = (HttpURLConnection) new URL("https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=" + accessToken).openConnection();
                    connection.setDoOutput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "application/xml"); // Set content type

                    try (OutputStream os = connection.getOutputStream()) {
                        os.write(replyMsg.send().getBytes("UTF-8"));
                    }

                    int responseCode = connection.getResponseCode();
                    if (responseCode != HttpURLConnection.HTTP_OK) {
                        logger.error("Failed to send message to WeChat user: HTTP code {}", responseCode);
                    }
                }
            } catch (Exception e) {
                logger.error("Error processing WeChat message: {}", e.getMessage());
                // Optionally handle failure (e.g., notify the user)
            } finally {
                if (connection != null) {
                    connection.disconnect(); // Ensure the connection is closed
                }
            }
        });
    }

    // Step 5: This method calls Ollama API asynchronously and returns the complete response.
    private static String callOllamaAsync(String prompt) throws Exception {
        // Initialize the Ollama API connection
        String host = "http://localhost:11434/";  // Change to your Ollama API URL
        OllamaAPI ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setRequestTimeoutSeconds(60);

        // Generate asynchronous request to Ollama with a polling mechanism
        OllamaAsyncResultStreamer streamer = ollamaAPI.generateAsync(OllamaModelType.LLAMA3_1, prompt, false);

        // Poll interval in milliseconds
        int pollIntervalMilliseconds = 1000;
        StringBuilder completeResponse = new StringBuilder();

        while (true) {
            // Fetch partial tokens from the stream
            String tokens = streamer.getStream().poll();
            if (tokens != null) {
                completeResponse.append(tokens);  // Collect tokens for the full response
            }

            // Break loop when streaming is complete
            if (!streamer.isAlive()) {
                break;
            }
            Thread.sleep(pollIntervalMilliseconds);  // Polling interval
        }

        // Print and return the complete response from Ollama
        System.out.println("Complete Response: " + completeResponse.toString());
        return completeResponse.toString();  // Return the full response for WeChat reply
    }


    private static boolean verifySignature(String signature, String timestamp, String nonce) {
        logger.info("Timestamp: ${timestamp}, Nonce: ${nonce}, Signature: ${signature}")
        String[] params = [TOKEN, timestamp, nonce]
        Arrays.sort(params)
        String toHash = params.join("")
        String calculatedSignature = DigestUtils.sha1Hex(toHash)
        return calculatedSignature == signature
    }

    private static TextMsg parseXml(String xmlData) {
        if (xmlData?.trim()) {
            def xml = new XmlParser().parseText(xmlData)
            def msgType = xml.MsgType.text()
            return msgType == 'text' ? new TextMsg(xml) : (msgType == 'image' ? new ImageMsg(xml) : null)
        }
        return null
    }
}
