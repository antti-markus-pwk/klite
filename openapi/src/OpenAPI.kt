package klite.openapi

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.tags.Tag
import klite.*
import klite.StatusCode.Companion.OK
import klite.annotations.*
import java.math.BigDecimal
import java.net.URI
import java.net.URL
import java.time.*
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KParameter
import kotlin.reflect.KParameter.Kind.INSTANCE
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

// Spec: https://swagger.io/specification/
// Sample: https://github.com/OAI/OpenAPI-Specification/blob/main/examples/v3.0/api-with-examples.json

/**
 * Adds an /openapi endpoint to the context, describing all the routes.
 * Use @Operation swagger annotations to describe the routes.
 * @Parameter swagger annotation can be used on method parameters directly.
 *
 * To run Swagger-UI:
 *   add `before(CorsHandler())`
 *   `docker run -d -p 8080:8088 -e SWAGGER_JSON_URL=http://YOUR-IP:PORT/api/openapi swaggerapi/swagger-ui`
 *   Open http://localhost:8088
 */
fun Router.openApi(path: String = "/openapi", title: String = "API", version: String = "1.0.0") {
  get(path) {
    mapOf(
      "openapi" to "3.0.0",
      "info" to mapOf("title" to title, "version" to version),
      "servers" to listOf(mapOf("url" to fullUrl(prefix))),
      "tags" to toTags(routes),
      "paths" to routes.groupBy { pathParamRegexer.toOpenApi(it.path) }.mapValues { (_, routes) ->
        routes.associate(::toOperation)
      }
    )
  }
}

internal fun toTags(routes: List<Route>) = routes
  .map { it.handler }
  .filterIsInstance<FunHandler>()
  .map { it.instance::class.annotation<Tag>()?.toNonEmptyValues() ?: mapOf("name" to it.instance::class.simpleName) }
  .toSet()

internal fun toOperation(route: Route): Pair<String, Any> {
  val op = route.annotation<Operation>()
  return (op?.method?.trimToNull() ?: route.method.name).lowercase() to mapOf(
    "operationId" to route.handler.let { (if (it is FunHandler) it.instance::class.simpleName + "." + it.f.name else it::class.simpleName) },
    "tags" to listOfNotNull((route.handler as? FunHandler)?.let { it.instance::class.annotation<Tag>()?.name ?: it.instance::class.simpleName }),
    "parameters" to (route.handler as? FunHandler)?.let {
      it.params.filter { it.source != null }.map { p -> toParameter(p, op) }
    },
    "requestBody" to findRequestBody(route),
    "responses" to mapOf(
      OK.value to mapOf("description" to "OK")
    )
  ) + (op?.let { it.toNonEmptyValues { it.name != "method" } + mapOf(
    "responses" to op.responses.associate { it.responseCode to it.toNonEmptyValues { it.name != "responseCode" } }.takeIf { it.isNotEmpty() }
  ) } ?: emptyMap())
}

fun toParameter(p: Param, op: Operation? = null) = mapOf(
  "name" to p.name,
  "required" to (!p.p.isOptional && !p.p.type.isMarkedNullable),
  "in" to toParameterIn(p.source),
  "schema" to toSchema(p.p.type.classifier),
) + ((p.p.findAnnotation<Parameter>() ?: op?.parameters?.find { it.name == p.name })?.toNonEmptyValues() ?: emptyMap())

private fun toParameterIn(paramAnnotation: Annotation?) = when(paramAnnotation) {
  is HeaderParam -> ParameterIn.HEADER
  is QueryParam -> ParameterIn.QUERY
  is PathParam -> ParameterIn.PATH
  is CookieParam -> ParameterIn.COOKIE
  else -> null
}

private fun toSchema(type: KClassifier?): Map<String, Any> {
  val cls = type as KClass<*>
  val jsonType = when (cls) {
    Boolean::class -> "boolean"
    Number::class -> "integer"
    BigDecimal::class, Decimal::class, Float::class, Double::class -> "number"
    else -> if (cls == String::class || Converter.supports(cls)) "string" else "object"
  }
  val jsonFormat = when (cls) {
    LocalDate::class, Date::class -> "date"
    LocalTime::class -> "time"
    Instant::class, LocalDateTime::class -> "date-time"
    Period::class, Duration::class -> "duration"
    URI::class, URL::class -> "uri"
    UUID::class -> "uuid"
    else -> null
  }
  return mapOfNotNull(
    "type" to jsonType,
    "format" to jsonFormat,
    "enum" to if (cls.isSubclassOf(Enum::class)) cls.java.enumConstants.toList() else null,
    "properties" to if (jsonType == "object") type.publicProperties.associate { it.name to toSchema(it.returnType.classifier) } else null,
    "required" to if (jsonType == "object") type.publicProperties.filter { !it.returnType.isMarkedNullable }.map { it.name }.toSet() else null
  )
}

private fun findRequestBody(route: Route) = (route.handler as? FunHandler)?.params?.
  find { it.p.kind != INSTANCE && it.source == null && it.cls.java.packageName != "klite" }?.p?.toRequestBody()

private fun KParameter.toRequestBody() = mapOf("content" to mapOf(MimeTypes.json to mapOf("schema" to toSchema(type.classifier))))

internal fun <T: Annotation> T.toNonEmptyValues(filter: (KProperty1<T, *>) -> Boolean = {true}) =
  toValues(publicProperties.filter(filter)).filterValues { !isEmpty(it) }

private fun isEmpty(it: Any?): Boolean =
  it == "" || it == false ||
  (it as? Array<*>)?.isEmpty() == true ||
  (it as? Annotation)?.toNonEmptyValues()?.isEmpty() == true
