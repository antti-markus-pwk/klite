package klite.jdbc

import Routes
import SomeReposponse
import klite.Server
import klite.annotations.annotated
import klite.json.JsonBody
import klite.json.JsonHttpClient
import klite.register
import kotlinx.coroutines.runBlocking
import net.oddpoet.expect.expect
import net.oddpoet.expect.extension.equal
import org.junit.jupiter.api.Test
import java.net.http.HttpClient
import java.time.Duration.ofSeconds

class ServerIntegrationTest {
  @Test fun requests() {
    val port = (Math.random() * 60000 + 1000).toInt()
    val server = Server(port = port).apply {
      registry.register(HttpClient.newBuilder().connectTimeout(ofSeconds(5)).build())
      use(JsonBody())
      context("/") {
        get("hello") { "Hello" }
      }
      context("/api") {
        useOnly<JsonBody>()
        annotated<Routes>()
      }
      start(gracefulStopDelaySec = -1)
    }

    runBlocking {
      val http = JsonHttpClient(server.registry, "http://localhost:$port")
      expect(http.get<String>("/hello")).to.equal("\"Hello\"")
      expect(http.get<SomeReposponse>("/api/hello")).to.equal(SomeReposponse("Hello"))
    }
    server.stop()
  }
}
