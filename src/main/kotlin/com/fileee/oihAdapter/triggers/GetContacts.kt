package com.fileee.oihAdapter.triggers

import arrow.core.*
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monad
import arrow.effects.monadDefer
import arrow.instances.traverse
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.*
import com.fileee.oihAdapter.algebra.ContactAlgebra
import com.fileee.oihAdapter.algebra.ContactException
import com.fileee.oihAdapter.algebra.EmitAlgebra
import com.fileee.oihAdapter.algebra.LogAlgebra
import com.fileee.oihAdapter.interpreter.EmitInterpreter
import com.fileee.oihAdapter.interpreter.LogInterpreter
import io.elastic.api.ExecutionParameters
import io.elastic.api.Module
import org.slf4j.LoggerFactory
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

      val lastModified = snapshot.flatMap { parseLastModified(CONTACT_TYPE, it) }.getOrElse { 0 }

      parseCredentials(configuration).toEither { ContactException.AuthException }
        .flatMap {
          contactAlgebra.getContactList(lastModified, it).bind()
        }.fold({
          logAlgebra.error("Failed to execute getContacts").bind()
          logAlgebra.error(it.toString()).bind()

          emitAlgebra.emitError(it).bind()
        }, {
          logAlgebra.info("Successfully executed getContacts").bind()

          val snap = createNewSnapFromOld(CONTACT_TYPE, snapshot)

          emitAlgebra.emitSnapshot(snap).bind()

          it.traverse(monadF) { it.traverse(monadF) { emitAlgebra.emitMessage(it) } }.bind()
        })
    }
}