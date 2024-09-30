package org.moqui.wechat

import groovy.xml.MarkupBuilder

// Unified class for both text and image messages
class WechatMsg {
    protected String ToUserName
    protected String FromUserName
    protected String CreateTime
    protected String MsgType // This will be either 'text' or 'image'
    protected String Content // Used for text messages
    protected String MediaId // Used for image messages

    // Constructor to parse from XML data
    WechatMsg(xmlData) {
        this.ToUserName = xmlData.ToUserName.text()
        this.FromUserName = xmlData.FromUserName.text()
        this.CreateTime = xmlData.CreateTime.text()
        this.MsgType = xmlData.MsgType?.text() ?: '' // Set MsgType from XML or default to empty string

        if (this.MsgType == 'text') {
            this.Content = xmlData.Content.text() // Set Content for text message
        } else if (this.MsgType == 'image') {
            this.MediaId = xmlData.MediaId.text() // Set MediaId for image message
        }
    }

    // Constructor to create from provided data
    WechatMsg(String toUser, String fromUser, String msgType, String contentOrMediaId) {
        this.ToUserName = toUser
        this.FromUserName = fromUser
        this.MsgType = msgType
        this.CreateTime = (System.currentTimeMillis() / 1000).toString()

        // Assign the appropriate field based on message type
        if (msgType == 'text') {
            this.Content = contentOrMediaId // For text messages
        } else if (msgType == 'image') {
            this.MediaId = contentOrMediaId // For image messages
        }
    }

    // Method to parse incoming XML and return WechatMsg
    static WechatMsg parseXml(String xmlData) {
        if (xmlData?.trim()) {
            def xml = new XmlParser().parseText(xmlData)
            return new WechatMsg(xml)
        }
        return null
    }

    // Method to send the message in XML format
    String send() {
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)

        // Build the XML based on message type
        xml.xml {
            ToUserName(ToUserName)
            FromUserName(FromUserName)
            CreateTime(CreateTime)
            MsgType(MsgType)

            if (MsgType == 'text') {
                Content(Content) // For text messages
            } else if (MsgType == 'image') {
                Image {
                    MediaId(MediaId) // For image messages
                }
            }
        }
        return writer.toString()
    }
}
