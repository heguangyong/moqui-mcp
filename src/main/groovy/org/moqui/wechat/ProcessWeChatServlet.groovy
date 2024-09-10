package org.moqui.wechat

import groovy.xml.MarkupBuilder
import groovy.util.XmlParser
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.ServletException
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.io.IOException
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

import org.moqui.openai.OpenAIResponseHandler

class ProcessWeChatServlet extends HttpServlet {
    protected final static Logger logger = LoggerFactory.getLogger(ProcessWeChatServlet.class)
    private static final String TOKEN = "moquiwechat"
    private static final String ENCODING_AES_KEY = "06MFgfg3RkV2CfT0lrrS9gZMQOJe5BIp55tNiBHIv26"
    private static final String IV = ENCODING_AES_KEY.substring(0, 16)

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
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String message = req.reader.text

        if (message) {
            handleIncomingMessage(message, resp)
        } else {
            resp.writer.write("success")
        }

    }

    private boolean verifySignature(String signature, String timestamp, String nonce) {
        logger.info("Timestamp: ${timestamp}, Nonce: ${nonce}, Signature: ${signature}")
        String[] params = [TOKEN, timestamp, nonce]
        Arrays.sort(params)
        String toHash = params.join("")
        String calculatedSignature = DigestUtils.sha1Hex(toHash)
        return calculatedSignature == signature
    }

    private void handleIncomingMessage(String message, HttpServletResponse resp) throws IOException {
        def recMsg = parseXml(message)
        logger.info("Received message of type: ${recMsg?.getClass()?.name}")
        if (recMsg instanceof TextMsg) {
            TextMsg textMsg = recMsg
            String toUser = textMsg.FromUserName
            String fromUser = textMsg.ToUserName
            // Call OpenAI GPT Service to get the response
            String content = OpenAIResponseHandler.processRequest(textMsg.Content)
            def replyMsg = new TextMsg(toUser, fromUser, content)
            resp.writer.write(replyMsg.send())
        } else {
            resp.writer.write("success")
        }
    }

    private TextMsg parseXml(String xmlData) {
        if (xmlData?.trim()) {
            def xml = new XmlParser().parseText(xmlData)
            def msgType = xml.MsgType.text()
            return msgType == 'text' ? new TextMsg(xml) : (msgType == 'image' ? new ImageMsg(xml) : null)
        }
        return null
    }
}
