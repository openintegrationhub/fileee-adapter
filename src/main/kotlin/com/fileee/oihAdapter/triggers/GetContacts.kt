package com.fileee.oihAdapter.triggers

import arrow.core.*
import arrow.effects.*
import arrow.instances.traverse
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.CONTACT_TYPE
import com.fileee.oihAdapter.MODIFIED_AFTER_KEY
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.defaultContactInterpreter
import com.fileee.oihAdapter.interpreter.EmitInterpreter
import com.fileee.oihAdapter.interpreter.LogInterpreter
import com.fileee.oihAdapter.parseCredentials
import io.elastic.api.ExecutionParameters
import io.elastic.api.Module
import org.slf4j.LoggerFactory
import java.time.Instant
import javax.json.Json
import javax.json.JsonNumber
import javax.json.JsonObject

open class GetContacts : Module {
  override fun execute(parameters: ExecutionParameters?) {
    parameters?.let { parameters ->
      getContactList(
        parameters.snapshot.toOption(),
        parameters.configuration,
        defaultContactInterpreter(
          parameters.eventEmitter
        ),
        EmitInterpreter(
          parameters.eventEmitter.some(),
          IO.monadDefer()
        ),
        LogInterpreter(
          LoggerFactory.getLogger(GetContacts::class.java),
          IO.monadDefer()
        ),
        IO.monad()
      ).fix().unsafeRunSync()
    }
  }

  /**
   * Describes getContactList in terms of [ContactAlgebra], [EmitAlgebra] and some logging with [LogAlgebra] over a
   *  monadic kind [F].
   */
  internal fun <F> getContactList(
    snapshot: Option<JsonObject>,
    configuration: JsonObject,
    contactAlgebra: ContactAlgebra<F>,
    emitAlgebra: EmitAlgebra<F>,
    logAlgebra: LogAlgebra<F>,
    monadF: Monad<F>
  ) =
    monadF.binding {
      logAlgebra.info("Executing getContacts").bind()

      val lastModified = snapshot.flatMap { parseLastModified(it) }.getOrElse { 0 }

      parseCredentials(configuration).toEither { ContactException.AuthException }
        .flatMap {
          contactAlgebra.getContactList(lastModified, it).bind()
        }.fold({
          logAlgebra.error("Failed to execute getContacts").bind()
          logAlgebra.error(it.toString()).bind()

          emitAlgebra.emitError(it).bind()
        }, {
          logAlgebra.info("Successfully executed getContacts").bind()

          val snap = createNewSnapFromOld(snapshot)

          emitAlgebra.emitSnapshot(snap).bind()

          it.traverse(monadF) { it.traverse(monadF) { emitAlgebra.emitMessage(it) } }.bind()
        })
    }

  // this below is technically impure, but ignored (for now) for simplicity
  internal fun createNewSnapFromOld(snapshot: Option<JsonObject>): JsonObject {
    val base = snapshot.fold({ Json.createObjectBuilder() }, {
      val builder = Json.createObjectBuilder()
      it.forEach { key, v -> builder.add(key, v) }
      builder
    })
    val time = Instant.now().toEpochMilli()

    base.add(
      CONTACT_TYPE,
      Json.createObjectBuilder()
        .add(MODIFIED_AFTER_KEY, time)
        .build()
    )

    return base.build()
  }

  internal fun parseLastModified(snap: JsonObject): Option<TimeStamp> {
    val contactSnap = snap[CONTACT_TYPE]
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
}