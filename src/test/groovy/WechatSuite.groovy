import org.junit.jupiter.api.AfterAll
import org.junit.platform.suite.api.SelectClasses
import org.junit.platform.suite.api.Suite
import org.moqui.Moqui

@Suite
@SelectClasses([WechatServiceTests.class])
class WechatSuite {
    @AfterAll
    static void destroyMoqui() {
        Moqui.destroyActiveExecutionContextFactory()
    }
}
