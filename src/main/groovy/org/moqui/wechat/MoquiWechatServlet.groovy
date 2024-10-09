package org.moqui.wechat

import groovy.transform.CompileStatic
import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.response.OllamaAsyncResultStreamer
import io.github.ollama4j.types.OllamaModelType
import org.apache.commons.codec.digest.DigestUtils
import org.moqui.impl.context.ExecutionContextFactoryImpl
import org.moqui.impl.context.ExecutionContextImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.ServletConfig
import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@CompileStatic
class MoquiWechatServlet extends HttpServlet {
    protected final static Logger logger = LoggerFactory.getLogger(MoquiWechatServlet.class)
    private static final String TOKEN = "moquiwechat"
    private static final String ENCODING_AES_KEY = "06MFgfg3RkV2CfT0lrrS9gZMQOJe5BIp55tNiBHIv26"
    private static final String IV = ENCODING_AES_KEY.substring(0, 16)

    // Create an ExecutorService to handle asynchronous tasks
    private final ExecutorService executorService = Executors.newCachedThreadPool()

    MoquiWechatServlet() {
        super()
    }

    @Override
    void init(ServletConfig config) throws ServletException {
        super.init(config)
        String webappName = config.getInitParameter("moqui-name") ?: config.getServletContext().getInitParameter("moqui-name")
        logger.info("${config.getServletName()} initialized for webapp ${webappName}")
    }

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
        logger.info("request: " + request)
        ExecutionContextFactoryImpl ecfi = (ExecutionContextFactoryImpl) request.getServletContext().getAttribute("executionContextFactory")
        String moquiWebappName = request.getServletContext().getInitParameter("moqui-name")

        if (ecfi == null || moquiWebappName == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "System is initializing, try again soon.")
            return
        }

        long startTime = System.currentTimeMillis()

        // Destroy active ExecutionContext if one exists
        ExecutionContextImpl activeEc = ecfi.activeContext.get()
        if (activeEc != null) {
            logger.warn("Existing ExecutionContext detected, destroying: user=${activeEc.user.username}")
            activeEc.destroy()
        }

        // Create a new ExecutionContext
        ExecutionContextImpl ec = ecfi.getEci()
        try {
            ec.initWebFacade(moquiWebappName, request, response)
            ec.web.requestAttributes.put("moquiRequestStartTime", startTime)

            // Step 2: Receive message from WeChat user
            String weChatMessage = request.reader.text
            logger.info("Received WeChat message: " + weChatMessage)
            WechatMsg recMsg = WechatMsg.parseXml(weChatMessage)

            // Check if the message is valid and determine the type
            if (recMsg != null) {
                String toUser = recMsg.FromUserName
                String fromUser = recMsg.ToUserName
                String replyText

                if (recMsg.MsgType == 'text') {
                    // Handle text message
                    logger.info("Processing text message")
                    replyText = "Text message received: " + recMsg.Content
                } else if (recMsg.MsgType == 'image') {
                    // Handle image message
                    logger.info("Processing image message")
                    replyText = "Image message received with MediaId: " + recMsg.MediaId
                } else {
                    // Handle unknown message type
                    logger.warn("Unknown message type received: " + recMsg.MsgType)
                    replyText = "Unsupported message type received."
                }

                // Step 3: Acknowledge receipt quickly to avoid WeChat timeout
                WechatMsg replyMsg = new WechatMsg(toUser, fromUser, 'text', replyText)
                logger.info("Replying to WeChat: " + replyMsg.send())
                response.getWriter().write(replyMsg.send())

                // Fetch WeChat access token using ExecutionContext's tool manager
                WeChatAccessTokenManager tokenManager = (WeChatAccessTokenManager) ecfi.getTool("WeChatAccessTokenManager", WeChatAccessTokenManager.class)
                String accessToken = tokenManager.getAccessToken()

                // Step 4: Prepare to send a response to the WeChat user
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.weixin.qq.com/cgi-bin/message/custom/send?access_token=" + accessToken).openConnection()
                connection.setDoOutput(true)
                connection.setRequestMethod("POST")
                connection.setRequestProperty("Content-Type", "application/xml")

                // Step 5: Asynchronously process Ollama response
                executorService.submit(() -> {
                    try {
                        String ollamaResponse = callOllamaAsync(weChatMessage)
                        WechatMsg ollamaReplyMsg = new WechatMsg(toUser, fromUser, 'text', ollamaResponse)

                        try (OutputStream os = connection.getOutputStream()) {
                            os.write(ollamaReplyMsg.send().getBytes("UTF-8"))
                        }

                        int responseCode = connection.getResponseCode()
                        if (responseCode != HttpURLConnection.HTTP_OK) {
                            logger.error("Failed to send message to WeChat: HTTP code {}", responseCode)
                        }
                    } catch (Exception e) {
                        logger.error("Error processing WeChat message: {}", e.getMessage())
                    } finally {
                        connection.disconnect()
                    }
                })
            }
        } catch (Throwable t) {
            logger.error("Error processing WeChat request", t)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal server error")
        } finally {
            ec.destroy()
        }
    }


    private static String callOllamaAsync(String prompt) throws Exception {
        String host = "http://localhost:11434/" // Change to your Ollama API URL
        OllamaAPI ollamaAPI = new OllamaAPI(host)
        ollamaAPI.setRequestTimeoutSeconds(60)

        OllamaAsyncResultStreamer streamer = ollamaAPI.generateAsync(OllamaModelType.LLAMA3_1, prompt, false)

        StringBuilder completeResponse = new StringBuilder()
        while (streamer.isAlive()) {
            String tokens = streamer.getStream().poll()
            if (tokens != null) {
                completeResponse.append(tokens)
            }
            Thread.sleep(1000)
        }
        return completeResponse.toString()
    }

    private static boolean verifySignature(String signature, String timestamp, String nonce) {
        logger.info("Verifying signature: timestamp=${timestamp}, nonce=${nonce}, signature=${signature}")
        String[] params = [TOKEN, timestamp, nonce]
        Arrays.sort(params)
        String toHash = params.join("")
        String calculatedSignature = DigestUtils.sha1Hex(toHash)
        return calculatedSignature == signature
    }

}
