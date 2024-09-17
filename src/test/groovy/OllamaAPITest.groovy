import io.github.ollama4j.OllamaAPI
import org.junit.Test;

class OllamaAPITest {
    @Test
    void testOllamaServerReachability() {
        def ollamaAPI = new OllamaAPI()
        assert ollamaAPI.ping()
    }
}

