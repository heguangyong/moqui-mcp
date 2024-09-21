package org.moqui.ollama

import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.generate.OllamaStreamHandler
import io.github.ollama4j.models.response.OllamaResult
import io.github.ollama4j.types.OllamaModelType
import io.github.ollama4j.utils.OptionsBuilder

class OllamaAPIService {

    static void askQuestion(String question) {
        // Adjust the host to point to the SSH tunnel if necessary
        String host = "http://localhost:11434/"

        // Initialize the Ollama API client
        OllamaAPI ollamaAPI = new OllamaAPI(host)

        // Define a stream handler to process the response stream
        OllamaStreamHandler streamHandler = { String s ->
            println(s)
        }

        // Make the API request using the Ollama3.1 model
        OllamaResult result = ollamaAPI.generate(OllamaModelType.LLAMA3_1,
                "What is the capital of France? And what's France's connection with Mona Lisa?",
                new OptionsBuilder().build(), streamHandler)

        // Print the full response at the end
        println("Full response: " + result.getResponse())
    }
}
