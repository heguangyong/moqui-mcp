package org.moqui.ollama

import io.github.ollama4j.OllamaAPI
import io.github.ollama4j.models.chat.OllamaChatMessage
import io.github.ollama4j.models.chat.OllamaChatMessageRole
import io.github.ollama4j.models.chat.OllamaChatRequest
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder
import io.github.ollama4j.models.chat.OllamaChatResult
import io.github.ollama4j.models.generate.OllamaStreamHandler
import io.github.ollama4j.models.response.OllamaAsyncResultStreamer
import io.github.ollama4j.models.response.OllamaResult
import io.github.ollama4j.types.OllamaModelType
import io.github.ollama4j.utils.OptionsBuilder
import io.github.ollama4j.utils.SamplePrompts

import java.util.concurrent.CompletableFuture

class OllamaService {

    static void whoAreYou(){
        // Adjust the host to point to the SSH tunnel if necessary
        String host = "http://localhost:11434/"

        // Initialize the Ollama API client
        OllamaAPI ollamaAPI = new OllamaAPI(host)
        ollamaAPI.setRequestTimeoutSeconds(60);
        boolean stream = false // or false, depending on your requirements
        OllamaResult result =
                ollamaAPI.generate(OllamaModelType.LLAMA3_1, "Who are you?", stream,new OptionsBuilder().build());

        System.out.println(result.getResponse());
    }

    static void chat(){
        String host = "http://localhost:11434/";

        OllamaAPI ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setRequestTimeoutSeconds(60);
        OllamaChatRequestBuilder builder = OllamaChatRequestBuilder.getInstance(OllamaModelType.LLAMA3_1);

        // create first user question
        OllamaChatRequest requestModel = builder.withMessage(OllamaChatMessageRole.USER, "What is the capital of France?")
                .build();

        // start conversation with model
        OllamaChatResult chatResult = ollamaAPI.chat(requestModel);

        System.out.println("First answer: " + chatResult.getResponse());

        // create next userQuestion
        requestModel = builder.withMessages(chatResult.getChatHistory()).withMessage(OllamaChatMessageRole.USER, "And what is the second largest city?").build();

        // "continue" conversation with model
        chatResult = ollamaAPI.chat(requestModel);

        System.out.println("Second answer: " + chatResult.getResponse());

        System.out.println("Chat History: " + chatResult.getChatHistory());
    }

    static void QueryDatabase() {
        // Set the host to the local Ollama service through SSH tunnel
        String host = "http://localhost:11434/";

        // Initialize the Ollama API client
        OllamaAPI ollamaAPI = new OllamaAPI(host);
        ollamaAPI.setRequestTimeoutSeconds(60);
        // Define the prompt
        String prompt =
                SamplePrompts.getSampleDatabasePromptWithQuestion(
                        "List all customer names who have bought one or more products");
        boolean stream = false // or false, depending on your requirements
        // Call the Ollama API and get the result
        OllamaResult result =
                ollamaAPI.generate(OllamaModelType.SQLCODER, prompt, stream,new OptionsBuilder().build());

        // Print the response
        System.out.println(result.getResponse());
    }

    static CompletableFuture<String> asyncAskQuestion(String question) {
        return CompletableFuture.supplyAsync(() -> {
            String host = "http://localhost:11434/";
            OllamaAPI ollamaAPI = new OllamaAPI(host);
            ollamaAPI.setRequestTimeoutSeconds(60);
            String prompt = question;

            OllamaAsyncResultStreamer streamer = ollamaAPI.generateAsync(OllamaModelType.LLAMA3_1, prompt, false);
            int pollIntervalMilliseconds = 1000;

            StringBuilder completeResponse = new StringBuilder();

            while (true) {
                try {
                    String tokens = streamer.getStream().poll();
                    if (tokens != null) {
                        completeResponse.append(tokens);
                    }

                    if (!streamer.isAlive()) {
                        break;
                    }

                    Thread.sleep(pollIntervalMilliseconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Streaming interrupted", e);
                }
            }

            // Return the complete response as a string
            return completeResponse.toString();
        });
    }

    static void main(String[] args) {
        chat()
    }
}
