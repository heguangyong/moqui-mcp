package org.moqui.ollama

import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.generate.OllamaStreamHandler
import io.github.ollama4j.models.response.Model
import io.github.ollama4j.models.response.ModelDetail
import io.github.ollama4j.models.response.OllamaResult
import io.github.ollama4j.types.OllamaModelType
import io.github.ollama4j.utils.OptionsBuilder

class OllamaAPIService {
    public static void main(String[] args) {
        String host = "http://localhost:11434/"

        OllamaAPI ollamaAPI = new OllamaAPI(host)

//        boolean isOllamaServerReachable = ollamaAPI.ping()
//
//        println "Is Ollama server running: $isOllamaServerReachable"
//        List<Model> models = ollamaAPI.listModels()
//
//        models.forEach(model -> System.out.println(model.getName()))
//        ModelDetail modelDetails = ollamaAPI.getModelDetails(OllamaModelType.LLAMA3_1)
//
//        System.out.println(modelDetails)

//        OllamaResult result = ollamaAPI.generate(OllamaModelType.LLAMA3_1, "Who are you?", new OptionsBuilder().build())
//        System.out.println(result.getResponse())

        // define a stream handler (Consumer<String>)
//        OllamaStreamHandler streamHandler = (s) -> {
//            System.out.println(s);
//        };

        // Should be called using seperate thread to gain non blocking streaming effect.
//        OllamaResult result = ollamaAPI.generate(OllamaModelType.LLAMA3_1,
//                "What is the capital of France? And what's France's connection with Mona Lisa?",
//                new OptionsBuilder().build(), streamHandler);
//
//        System.out.println("Full response: " + result.getResponse());
    }
}
