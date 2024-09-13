package org.moqui.openai

import org.moqui.wechat.TextMsg

class OpenAIResponseHandler {

    static String processRequest(String weChatMessage) {
        String userQuery = weChatMessage // Assuming the message is passed directly
        String aiResponse = GPTService.getAnswer(userQuery)
        return aiResponse
    }

    static TextMsg generateResponse(String toUser, String fromUser, String aiResponse) {
        return new TextMsg(toUser, fromUser, aiResponse)
    }
}
