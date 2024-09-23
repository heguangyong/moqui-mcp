import org.moqui.context.ExecutionContext
import org.moqui.Moqui
import org.moqui.ollama.OllamaService
import spock.lang.Shared
import spock.lang.Specification

class WechatServiceTests extends Specification {
    @Shared
    ExecutionContext ec

    def setupSpec() {
        // Initialize Moqui framework, get the execution context (ec)
        ec = Moqui.getExecutionContext()
    }

    def cleanupSpec() {
        ec.destroy()
    }

    def setup() {
        ec.user.loginUser("john.doe", "moqui")
        ec.artifactExecution.disableAuthz()
        ec.transaction.begin(null)
    }

    def cleanup() {
        ec.transaction.commit()
        ec.artifactExecution.enableAuthz()
        ec.user.logoutUser()
    }

    def "Try asking who are you with WechatService"(){
        given:

        when:
        // Test the Ollama service call through the OllamaAPIService
        OllamaService.whoAreYou() // Adjust as necessary for your method

        then:
        noExceptionThrown() // Basic check that it runs without errors
        // Add assertions here based on the expected behavior of your API service
    }

    def "Try asking sync ask question with WechatService"() {
        given:

        when:
        // Test the Ollama service call through the OllamaAPIService
        OllamaService.syncAskQuestion("where is the capital of France?") // Adjust as necessary for your method

        then:
        noExceptionThrown() // Basic check that it runs without errors
        // Add assertions here based on the expected behavior of your API service
        response.contains("Paris") // Adjust this assertion based on expected output
    }


    def "Try asking async ask question with WechatService"(){
        given:

        when:
        // Test the Ollama service call through the OllamaAPIService
        OllamaService.asyncAskQuestion("List all cricket world cup teams of 2019.") // Adjust as necessary for your method

        then:
        noExceptionThrown() // Basic check that it runs without errors
        // Add assertions here based on the expected behavior of your API service
    }
}
