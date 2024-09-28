package org.moqui.wechat

import groovy.xml.MarkupBuilder

// Base class for common message properties, renamed to WechatMsg
abstract class WechatMsg {
    protected String ToUserName
    protected String FromUserName
    protected String CreateTime
    protected String MsgType

    WechatMsg(xmlData) {
        this.ToUserName = xmlData.ToUserName.text()
        this.FromUserName = xmlData.FromUserName.text()
        this.CreateTime = xmlData.CreateTime.text()
        this.MsgType = xmlData.MsgType?.text() ?: '' // Set MsgType from xml or default to an empty string
    }

    WechatMsg(String toUser, String fromUser, String msgType) {
        this.ToUserName = toUser
        this.FromUserName = fromUser
        this.MsgType = msgType
        this.CreateTime = (System.currentTimeMillis() / 1000).toString()
    }

    // ParseXml method
    static WechatMsg parseXml(String xmlData) {
        if (xmlData?.trim()) {
            def xml = new XmlParser().parseText(xmlData)
            def msgType = xml.MsgType?.text() ?: '' // Get MsgType safely

            return msgType == 'text' ? new TextMsg(xml) : (msgType == 'image' ? new ImageMsg(xml) : null) as WechatMsg
        }
        return null
    }

    // Abstract method to be implemented in subclasses for sending messages
    abstract String send()
}

// TextMsg class extending WechatMsg
class TextMsg extends WechatMsg {
    protected String Content

    TextMsg(xmlData) {
        super(xmlData)
        this.Content = xmlData.Content.text()
        this.MsgType = 'text' // Set MsgType to 'text'
    }

    TextMsg(String toUser, String fromUser, String content) {
        super(toUser, fromUser, 'text')
        this.Content = content
    }

    @Override
    String send() {
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)
        xml.xml {
            ToUserName(ToUserName)
            FromUserName(FromUserName)
            CreateTime(CreateTime)
            MsgType(MsgType)
            Content(Content)
        }
        return writer.toString()
    }
}

// ImageMsg class extending WechatMsg
class ImageMsg extends WechatMsg {
    protected String MediaId

    ImageMsg(xmlData) {
        super(xmlData)
        this.MediaId = xmlData.MediaId.text()
        this.MsgType = 'image' // Set MsgType to 'image'
    }

    ImageMsg(String toUser, String fromUser, String mediaId) {
        super(toUser, fromUser, 'image')
        this.MediaId = mediaId
    }

    @Override
    String send() {
        def writer = new StringWriter()
        def xml = new MarkupBuilder(writer)
        xml.xml {
            ToUserName(ToUserName)
            FromUserName(FromUserName)
            CreateTime(CreateTime)
            MsgType(MsgType)
            Image {
                MediaId(MediaId)
            }
        }
        return writer.toString()
    }
}


