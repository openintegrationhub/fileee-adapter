package com.fileee.oihAdapter.generators

import arrow.core.*
import arrow.data.Nel
import com.fileee.oihAdapter.*
import com.fileee.oihAdapter.algebra.Credentials
import com.sun.org.apache.regexp.internal.RE
import io.kotlintest.properties.Gen
import javax.json.*

fun <T> Gen<T>.rand() = random().first()

fun <T> Gen<T>.nel(): Gen<Nel<T>> =
  Gen.create {
    Nel.fromListUnsafe(
      (0..Gen.choose(1, 100).rand())
        .map { rand() }
    )
  }

fun <T> Gen<T>.option() : Gen<Option<T>> =
  orNull().map { it.toOption() }

// Generate random valid json
val jsonBoolGen : Gen<JsonValue> = Gen.bool().map { b ->
  if (b) JsonValue.TRUE
  else JsonValue.FALSE
}

val jsonStringGen : Gen<JsonValue> = Gen.string().map { s ->
  Json.createObjectBuilder().add("1", s).build()["1"]!!
}

val jsonNumberGen : Gen<JsonValue> = Gen.int().map { i ->
  Json.createObjectBuilder().add("1", i).build()["1"]!!
}

val jsonArrayGen : Gen<JsonArray> = object : Gen<JsonArray> {
  override fun constants(): Iterable<JsonArray> =
    listOf(Json.createArrayBuilder().build())

  override fun random(): Sequence<JsonArray> =
    generateSequence {
      val build = Json.createArrayBuilder()
      for (i in 1..Gen.choose(0, 10).random().first()) {
        build.add(jsonValueGen.random().first())
      }
      build.build()
    }
}

val jsonSimpleArrayGen : Gen<JsonArray> = object : Gen<JsonArray> {
  override fun constants(): Iterable<JsonArray> =
    listOf(Json.createArrayBuilder().build())

  override fun random(): Sequence<JsonArray> =
    generateSequence {
      val build = Json.createArrayBuilder()
      for (i in 1..Gen.choose(0, 10).random().first()) {
        build.add(jsonSimpleValueGen.random().first())
      }
      build.build()
    }

}

val jsonObjectGen: Gen<JsonObject> = object : Gen<JsonObject> {
  override fun random(): Sequence<JsonObject> =
    generateSequence {
      val build = Json.createObjectBuilder()
      for (i in 1..Gen.choose(0, 10).random().first()) {
        build.add(Gen.string().random().first(), jsonValueGen.random().first())
      }
      build.build()
    }

  override fun constants(): Iterable<JsonObject> = listOf(
    Json.createObjectBuilder().build()
  )
}

val jsonSimpleObjGen : Gen<JsonObject> = object : Gen<JsonObject> {
  override fun constants(): Iterable<JsonObject> =
    listOf(Json.createObjectBuilder().build())

  override fun random(): Sequence<JsonObject> =
    generateSequence {
      val build = Json.createObjectBuilder()
      for (i in 1..Gen.choose(0, 10).random().first()) {
        build.add(Gen.string().random().first(), jsonSimpleValueGen.random().first())
      }
      build.build()
    }

}

val jsonValueGen : Gen<JsonValue> = Gen.oneOf(
  jsonBoolGen,
  jsonStringGen,
  jsonNumberGen,
  Gen.create { JsonValue.NULL },
  jsonSimpleObjGen.map { it as JsonValue },
  jsonSimpleArrayGen.map { it as JsonValue }
)

val jsonSimpleValueGen : Gen<JsonValue> = Gen.oneOf(
  jsonBoolGen,
  jsonStringGen,
  jsonNumberGen,
  Gen.create { JsonValue.NULL }
)

fun JsonObject.toBuilder(): JsonObjectBuilder {
  val build = Json.createObjectBuilder()
  mapValues { (k, v) ->
    build.add(k, v)
  }
  return build
}

// Generators for stuff
// valid credentials generator
val credGen : Gen<Credentials> = object : Gen<Credentials> {

  override fun constants(): Iterable<Credentials> = listOf()

  override fun random(): Sequence<Credentials> =
    generateSequence { Credentials(Gen.string().random().first(), Gen.string().random().first()) }
}

// generates invalid and valid json indicated by it being either left or right
val credJsonGen : Gen<Either<JsonObject, JsonObject>> = Gen.oneOf<Either<JsonObject, JsonObject>>(
  credGen.map { toJson(it).right() },
  jsonObjectGen
    .filter {
      it[OAUTH_KEY]?.let {
        it.valueType != JsonValue.ValueType.OBJECT ||
          (
            ((it as JsonObject)[ACCESS_TOKEN]?.valueType != JsonValue.ValueType.STRING) &&
              (it[REFRESH_TOKEN]?.valueType != JsonValue.ValueType.STRING)
            )
      } ?: true
    }
    .map { it.left() }
)

fun toJson(cred: Credentials) =
  Json.createObjectBuilder()
    .add(OAUTH_KEY, Json.createObjectBuilder()
      .add(ACCESS_TOKEN, cred.accessToken)
      .add(REFRESH_TOKEN, cred.refreshToken))
    .build()

val idObjGen : Gen<JsonObject> = jsonObjectGen.map {
  it.toBuilder()
    .add("id", Gen.string().random().first())
    .build()
}

val jsonIdObjGen : Gen<Either<JsonObject, JsonObject>> = Gen.oneOf<Either<JsonObject, JsonObject>>(
  idObjGen.map { it.right() },
  jsonObjectGen
    // filter potentially correct vals
    .filter { it["id"]?.valueType != JsonValue.ValueType.STRING }
    .map { it.left() }
)

fun lastModifiedGen(type: String) : Gen<JsonObject> = jsonObjectGen.map {
  it.toBuilder()
    .add(type, Json.createObjectBuilder()
      .add(MODIFIED_AFTER_KEY, Gen.positiveIntegers().rand())
    )
    .build()
}

fun jsonLastModifiedGen(type: String) : Gen<Either<JsonObject, JsonObject>> = Gen.oneOf<Either<JsonObject, JsonObject>>(
  lastModifiedGen(type).map { it.right() },
  jsonObjectGen
    // filter potentially correct values
    .filter {
      it[type]?.let {
        it.valueType != JsonValue.ValueType.OBJECT ||
          (it as JsonObject)[MODIFIED_AFTER_KEY]?.valueType != JsonValue.ValueType.NUMBER
      } ?: true
    }
    .map { it.left() }
)

fun main(args: Array<String>) {
  for (i in 1..100) {
    println(i)
    val str = jsonArrayGen.random().first().toString()
    println(str)
    Json.createReader(
      str.reader()
    ).readArray()
  }
}