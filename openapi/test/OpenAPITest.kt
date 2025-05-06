package klite.openapi

import ch.tutteli.atrium.api.fluent.en_GB.toContainExactly
import ch.tutteli.atrium.api.fluent.en_GB.toEqual
import ch.tutteli.atrium.api.verbs.expect
import io.mockk.every
import io.mockk.mockk
import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn.*
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.tags.Tag
import klite.HttpExchange
import klite.MimeTypes
import klite.RequestMethod.GET
import klite.RequestMethod.POST
import klite.Route
import klite.Router
import klite.StatusCode.Companion.BadRequest
import klite.StatusCode.Companion.Found
import klite.StatusCode.Companion.NoContent
import klite.StatusCode.Companion.OK
import klite.StatusCode.Companion.Unauthorized
import klite.annotations.*
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.*

class OpenAPITest {
  data class User(val name: String, val id: UUID = UUID.randomUUID())
  fun userSchema(response: Boolean = false) = mapOf(MimeTypes.json to mapOf(
    "schema" to mapOf(
      "type" to "object",
      "properties" to mapOf(
        "name" to mapOf("type" to "string"),
        "id" to mapOf("type" to "string", "format" to "uuid")
      ),
      "required" to (if (response) setOf("name", "id") else setOf("name"))
    )
  ))

  @Test fun nonEmptyValues() {
    @Tag(name = "hello") class Dummy {}
    expect(Dummy::class.annotation<Tag>()!!.toNonEmptyValues()).toEqual(mutableMapOf("name" to "hello"))
  }

  @Test fun `route classes to tags`() {
    expect(toTags(listOf(Route(GET, "/hello".toRegex(), handler = FunHandler(this, OpenAPITest::toString)))))
      .toContainExactly(mapOf("name" to "OpenAPITest"))
  }

  @Test fun `route classes to tags with Tag annotation`() {
    @Tag(name = "my-tag", description = "My description") class MyRoutes {}
    expect(toTags(listOf(Route(GET, "/hello".toRegex(), handler = FunHandler(MyRoutes(), MyRoutes::toString)))))
      .toContainExactly(mapOf("name" to "my-tag", "description" to "My description"))
  }

  @Test fun `annotated klite route`() {
    class MyRoutes {
      fun saveUser(@PathParam userId: UUID, @QueryParam force: Boolean = false) = null
    }
    expect(toOperation(Route(POST, "/x".toRegex(), handler = FunHandler(MyRoutes(), MyRoutes::saveUser)))).toEqual("post" to mapOf(
      "operationId" to "MyRoutes.saveUser",
      "tags" to listOf("MyRoutes"),
      "parameters" to listOf(
        mapOf("name" to "userId", "required" to true, "in" to PATH, "schema" to mapOf("type" to "string", "format" to "uuid")),
        mapOf("name" to "force", "required" to false, "in" to QUERY, "schema" to mapOf("type" to "boolean"))
      ),
      "requestBody" to null,
      "responses" to mapOf(OK to mapOf("description" to "OK", "content" to mapOf(MimeTypes.json to mapOf("schema" to mapOf("type" to "null"))))),
      "security" to null
    ))
  }

  @Test fun parameters() {
    class MyRoutes {
      fun withParams(@PathParam date: LocalDate, @QueryParam simpleString: String?, @CookieParam dow: List<DayOfWeek>) = null
    }
    expect(FunHandler(MyRoutes(), MyRoutes::withParams).params.filter { it.source != null }.map { toParameter(it) }).toContainExactly(
      mapOf("name" to "date", "required" to true, "in" to PATH, "schema" to mapOf("type" to "string", "format" to "date")),
      mapOf("name" to "simpleString", "required" to false, "in" to QUERY, "schema" to mapOf("type" to "string")),
      mapOf("name" to "dow", "required" to true, "in" to COOKIE, "schema" to mapOf("type" to "array", "items" to mapOf("type" to "string", "enum" to DayOfWeek.values().toList())))
    )
  }

  @Test fun `request body`() {
    class MyRoutes {
      fun saveUser(e: HttpExchange, @PathParam userId: UUID, body: User) {}
    }
    expect(toOperation(Route(POST, "/x".toRegex(), handler = FunHandler(MyRoutes(), MyRoutes::saveUser)))).toEqual("post" to mapOf(
      "operationId" to "MyRoutes.saveUser",
      "tags" to listOf("MyRoutes"),
      "parameters" to listOf(
        mapOf("name" to "userId", "required" to true, "in" to PATH, "schema" to mapOf("type" to "string", "format" to "uuid"))
      ),
      "requestBody" to mapOf("content" to userSchema(), "required" to true),
      "responses" to mapOf(NoContent to mapOf("description" to "No content")),
      "security" to null
    ))
  }

  @Test fun `request body from annotation's implementation field`() {
    class MyRoutes {
      @RequestBody(description = "Application and applicant", content = [Content(mediaType = MimeTypes.json, schema = Schema(implementation = User::class))])
      @ApiResponse(responseCode = "400", description = "Very bad request")
      @ApiResponse(responseCode = "401", description = "Unauthorized")
      @SecurityRequirement(name = "MySecurity")
      fun saveUser(e: HttpExchange): User = User("x", UUID.randomUUID())
    }
    expect(toOperation(Route(POST, "/x".toRegex(), handler = FunHandler(MyRoutes(), MyRoutes::saveUser), annotations = MyRoutes::saveUser.annotations))).toEqual("post" to mapOf(
      "operationId" to "MyRoutes.saveUser",
      "tags" to listOf("MyRoutes"),
      "parameters" to emptyList<Any>(),
      "requestBody" to mapOf("description" to "Application and applicant", "required" to true, "content" to userSchema()),
      "responses" to mapOf(
        OK to mapOf("description" to "OK", "content" to userSchema(response = true)),
        BadRequest to mapOf("description" to "Very bad request"),
        Unauthorized to mapOf("description" to "Unauthorized"),
      ),
      "security" to listOf(mapOf("MySecurity" to emptyList<String>()))
    ))
  }

  @Test fun `anonymous route`() {
    expect(toOperation(Route(POST, "/x".toRegex()) {})).toEqual("post" to mapOf(
      "operationId" to null,
      "tags" to emptyList<Any>(),
      "parameters" to null,
      "requestBody" to null,
      "responses" to mapOf(OK to mapOf("description" to "OK")),
      "security" to null
    ))
  }

  @Test fun `anonymous route with annotation`() {
    val route = Route(POST, "/x".toRegex()) @Operation(
      operationId = "opId",
      tags = ["my-tag"],
      summary = "summary",
      parameters = [Parameter(name = "param", description = "description", `in` = QUERY)],
      responses = [ApiResponse(description = "desc", responseCode = "302")]
    ) {}
    expect(toOperation(route)).toEqual("post" to mapOf(
      "operationId" to "opId",
      "tags" to listOf("my-tag"),
      "parameters" to listOf(mapOf("name" to "param", "in" to QUERY, "description" to "description")),
      "requestBody" to null,
      "responses" to mapOf(Found to mapOf("description" to "desc")),
      "summary" to "summary",
      "security" to null
    ))
  }

  @Test fun `definition and security`() {
    @SecurityScheme(name = "bearerApiKey", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "API-Key", paramName = "Authorization")
    @OpenAPIDefinition(info = Info(title = "Mega API", version = "1.x"), security = [SecurityRequirement(name = "bearerApiKey")])
    class MyRoutes

    mockk<HttpExchange>(relaxed = true).apply {
      every { fullUrl(any()) } returns URI("https://base")
      every { route } returns Route(GET, "/".toRegex(), MyRoutes::class.annotations) {}
      val openApi = mockk<Router>(relaxed = true).generateOpenAPI()
      expect(openApi).toEqual(mapOf(
        "openapi" to "3.1.0",
        "info" to mapOf("title" to "Mega API", "version" to "1.x"),
        "servers" to listOf(mapOf("url" to URI("https://base"))),
        "tags" to emptySet<String>(),
        "components" to mapOf(
          "securitySchemes" to mapOf("bearerApiKey" to mapOf("type" to SecuritySchemeType.HTTP, "scheme" to "bearer", "bearerFormat" to "API-Key", "name" to "Authorization"))
        ),
        "paths" to emptyMap<String, Any?>(),
        "security" to listOf(mapOf("bearerApiKey" to emptyList<Any>()))
      ))
    }
  }
}
