package org.moqui.mcp

import org.moqui.context.ExecutionContextFactory
import org.moqui.context.ToolFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WeChatToolFactory implements ToolFactory<WeChatAccessTokenManager> {
    protected final static Logger logger = LoggerFactory.getLogger(WeChatToolFactory.class)
    final static String TOOL_NAME = "WeChatAccessTokenManager"

    protected ExecutionContextFactory ecf = null
    protected WeChatAccessTokenManager accessTokenManager = null

    @Override
    String getName() { return TOOL_NAME }

    @Override
    void init(ExecutionContextFactory ecf) {
        this.ecf = ecf
        this.accessTokenManager = new WeChatAccessTokenManager()
        logger.info("WeChat AccessToken Manager Initialized.")
    }

    @Override
    void preFacadeInit(ExecutionContextFactory ecf) {
        // Optionally perform any additional setup before the facade is initialized
    }

    @Override
    WeChatAccessTokenManager getInstance(Object... parameters) {
        return accessTokenManager
    }

    @Override
    void destroy() {
        if (accessTokenManager != null) {
            accessTokenManager.shutdown()
            logger.info("WeChat AccessToken Manager Shutdown.")
        }
    }
}
