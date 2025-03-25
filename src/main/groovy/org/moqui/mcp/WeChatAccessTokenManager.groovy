package org.moqui.mcp

import groovy.json.JsonSlurper
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class WeChatAccessTokenManager {
    protected final static Logger logger = LoggerFactory.getLogger(WeChatAccessTokenManager.class)

    private static final String APP_ID = "wxc42ba3b82548c8b6"
    private static final String APP_SECRET = "4f48a639f2ead638940cda0458931ac4"
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=${APP_ID}&secret=${APP_SECRET}"

    private String accessToken = ""
    private long expiresIn = 0
    private long lastFetchedTime = 0
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()

    WeChatAccessTokenManager() {
        // Initialize the token refresh task
        scheduler.scheduleAtFixedRate({ refreshToken() }, 0, 1, TimeUnit.HOURS)
    }

    private synchronized void refreshToken() {
        try {
            def url = new URL(TOKEN_URL)
            def connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            def response = connection.content.text
            def jsonResponse = new JsonSlurper().parseText(response)

            if (jsonResponse.access_token && jsonResponse.expires_in) {
                accessToken = jsonResponse.access_token
                expiresIn = jsonResponse.expires_in * 1000 // Convert to milliseconds
                lastFetchedTime = System.currentTimeMillis()

                logger.info( "Successfully fetched new access token: ${accessToken}")
            } else {
                logger.info( "Failed to fetch access token: ${jsonResponse}")
            }
        } catch (Exception e) {
            logger.info( "Error while fetching access token: ${e.message}")
            e.printStackTrace()
        }
    }

    String getAccessToken() {
        long currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchedTime >= expiresIn - 60000) { // Refresh token 1 minute before expiry
            refreshToken()
        }
        return accessToken
    }

    void shutdown() {
        scheduler.shutdown()
    }
}
