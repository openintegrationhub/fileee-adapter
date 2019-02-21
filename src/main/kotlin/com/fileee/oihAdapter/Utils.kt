package com.fileee.oihAdapter

import arrow.core.*
import arrow.effects.IO
import arrow.effects.async
import arrow.effects.monad
import arrow.effects.monadDefer
import com.fileee.oihAdapter.algebra.Credentials
import com.fileee.oihAdapter.algebra.TimeStamp
import com.fileee.oihAdapter.algebra.UUID
import com.fileee.oihAdapter.interpreter.*
import io.elastic.api.EventEmitter
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.json.Json
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonString

fun parseCredentials(conf: JsonObject): Option<Credentials> {
  val oauth = conf[OAUTH_KEY]
  return when (oauth) {
    is JsonObject -> {
      val accessTokenJsonVal = oauth[ACCESS_TOKEN]
      val refreshTokenJsonVal = oauth[REFRESH_TOKEN]
      val accessToken = when (accessTokenJsonVal) {
        is JsonString -> accessTokenJsonVal.string.some()
        else -> none()
      }
      val refreshToken = when (refreshTokenJsonVal) {
        is JsonString -> refreshTokenJsonVal.string.some()
        else -> none()
      }

      Option.applicative().map(accessToken, refreshToken) { (token, refresh) ->
        Credentials(token, refresh)
      }.fix()
    }
    else -> none()
  }
}

fun createHeaderFromCredentials(credentials: Credentials): Map<String, String> =
  mapOf(Pair("Authorization", "Bearer ${credentials.accessToken}"))

fun getId(body: JsonObject): Option<UUID> {
  val id = body["id"]
  return when (id) {
    is JsonString -> id.string.some()
    else -> none()
  }
}

// this below is technically impure, but ignored (for now) for simplicity
internal fun createNewSnapFromOld(type: String, snapshot: Option<JsonObject>): JsonObject {
  val base = snapshot.fold({ Json.createObjectBuilder() }, {
    val builder = Json.createObjectBuilder()
    it.forEach { key, v -> builder.add(key, v) }
    builder
  })
  val time = Instant.now().toEpochMilli()

  base.add(
    type,
    Json.createObjectBuilder()
      .add(MODIFIED_AFTER_KEY, time)
      .build()
  )

  return base.build()
}

internal fun parseLastModified(type: String, snap: JsonObject): Option<TimeStamp> {
  val contactSnap = snap[type]
  return when (contactSnap) {
    is JsonObject -> {
      val time = contactSnap[MODIFIED_AFTER_KEY]
      when (time) {
        is JsonNumber -> time.longValueExact().some()
        else -> none()
      }
    }
    else -> none()
  }
}

fun defaultContactInterpreter(
  eventEmitter: EventEmitter
) =
  ContactInterpreter(
    defaultHttpInterpreter(),
    ParseInterpreter(
      IO.monadDefer()
    ),
    authInterpreter(
      defaultHttpInterpreter(),
      ParseInterpreter(
        IO.monadDefer()
      ),
      EmitInterpreter(eventEmitter.some(), IO.monadDefer()),
      IO.monad()
    ),
    IO.async()
  )

fun defaultAuthInterpreter() =
  authInterpreter(
    defaultHttpInterpreter(),
    ParseInterpreter(IO.monadDefer()),
    EmitInterpreter(none(), IO.monadDefer()),
    IO.monad()
  )

fun defaultHttpInterpreter() =
  HttpInterpreter(
    LogInterpreter(LoggerFactory.getLogger(HttpInterpreter::class.java), IO.monadDefer()),
    IO.async()
  )