package org.moqui.openai

import org.moqui.context.ToolFactory
import org.moqui.context.ExecutionContextFactory

class OpenAIToolFactory implements ToolFactory {

    @Override
    void init(ExecutionContextFactory ecf) {
        // Initialize any OpenAI setup if needed
    }

    @Override
    void destroy() {
        // Cleanup if needed
    }

    @Override
    Object getInstance(Object... args) {
        return new GPTService()
    }

    @Override
    String getName() {
        return "OpenAI"
    }
}
