# klite-json

Klite lightweight json body parser/renderer without external dependencies.

No magic type coercion by default (e.g. empty string into 0), but can be overridden.

Supports Kotlin data and inline/value classes (unlike jackson), reuses type conversion from Klite's core [Converter](../core/src/Converter.kt).

```kotlin
use<JsonBody>()
```
or
```kotlin
useOnly<JsonBody>()
```

## Options

[JsonMapper](src/JsonMapper.kt) supports some configuration options through constructor parameters, e.g.

```kotlin
use(JsonBody(JsonMapper(renderNulls = true, keys = SnakeCase, values = object: ValueConverter<Any?>() {
  override fun to(o: Any?) = when(o) {
    is LocalDate -> dateFormat.format(o)
    else -> o
  }
})))
```

## Annotations

[@JsonProperty and @JsonIgnore](src/JsonMapper.kt) are provided, similar to Jackson

Note: `is`-prefixed boolean properties are rendered/parsed with `is` intact, but most Java json parsers would strip the `is` prefix.
Use `@JsonProperty` with custom name if you want stripped `is` prefixes.

## Integrations

[JsonHttpClient](src/JsonHttpClient.kt) is provided to do async json requests to other services.

## TypeScript type generation

Use [TSGenerator](src/TSGenerator.kt) to generate TypeScript types for data/enum classes in your project,
so that you can write type-safe code in frontend.

This is a pure Kotlin alternative to [jvm2dts](https://github.com/codeborne/jvm2dts), which takes Converter into account.

See [types.ts sample Gradle task](../sample/build.gradle.kts)
