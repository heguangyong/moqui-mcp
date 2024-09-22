package org.moqui.ollama

import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.generate.OllamaStreamHandler
import io.github.ollama4j.models.response.OllamaResult
import io.github.ollama4j.types.OllamaModelType
import io.github.ollama4j.utils.OptionsBuilder

class OllamaAPIService {

    static void askAQuestionAboutTheModel(){
        // Adjust the host to point to the SSH tunnel if necessary
        String host = "http://localhost:11434/"

        // Initialize the Ollama API client
        OllamaAPI ollamaAPI = new OllamaAPI(host)
        OllamaResult result =
                ollamaAPI.generate(OllamaModelType.LLAMA3_1, "Who are you?", new OptionsBuilder().build());

        System.out.println(result.getResponse());
    }

    static void askAQuestionReceivingTheAnswerStreamed(String question) {
        // Adjust the host to point to the SSH tunnel if necessary
        String host = "http://localhost:11434/"

        // Initialize the Ollama API client
        OllamaAPI ollamaAPI = new OllamaAPI(host)

        // Define a stream handler to process the response stream
        OllamaStreamHandler streamHandler = { String s ->
            println(s)
        }

        // Adjusting the method call to include the missing boolean parameter (e.g., for streaming)
        boolean stream = false // or false, depending on your requirements
        // Make the API request using the Ollama3.1 model
        OllamaResult result = ollamaAPI.generate(OllamaModelType.LLAMA3_1,
                question,stream,
                new OptionsBuilder().build(), streamHandler)

        // Print the full response at the end
        println("Full response: " + result.getResponse())
    }

    static void main(String[] args) {
        askAQuestionAboutTheModel()
//        askAQuestionReceivingTheAnswerStreamed("单挑")
    }
}
