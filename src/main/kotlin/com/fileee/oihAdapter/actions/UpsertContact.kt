package com.fileee.oihAdapter.actions

import arrow.core.left
import arrow.core.some
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monad
import arrow.effects.monadDefer
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.defaultContactInterpreter
import com.fileee.oihAdapter.getId
import com.fileee.oihAdapter.interpreter.EmitInterpreter
import com.fileee.oihAdapter.interpreter.LogInterpreter
import com.fileee.oihAdapter.parseCredentials
import io.elastic.api.ExecutionParameters
import io.elastic.api.Module
import org.slf4j.LoggerFactory
import javax.json.JsonObject


open class UpsertContact : Module {

  override fun execute(parameters: ExecutionParameters?) {
    parameters?.let { parameters ->
      upsertContact(
        parameters.message.body,
        parameters.configuration,
        defaultContactInterpreter(
          parameters.eventEmitter
        ),
        EmitInterpreter(parameters.eventEmitter.some(), IO.monadDefer()),
        LogInterpreter(
          LoggerFactory.getLogger(UpsertContact::class.java),
          IO.monadDefer()
        ),
        IO.monad()
      ).fix().unsafeRunSync()
    }
  }

  /**
   * Describes upsertContact in terms of [ContactAlgebra], [EmitAlgebra] and some logging with [LogAlgebra] over a
   *  monadic kind [F].
   */
  internal fun <F> upsertContact(
    contact: Contact,
    configuration: JsonObject,
    contactAlgebra: ContactAlgebra<F>,
    emitAlgebra: EmitAlgebra<F>,
    logAlgebra: LogAlgebra<F>,
    monadF: Monad<F>
  ) =
    monadF.binding {
      logAlgebra.info("Executing upsert contact").bind()

      parseCredentials(configuration).fold({ ContactException.AuthException.left() }, { cred ->
        getId(contact).fold({
          contactAlgebra.createContact(contact, cred).bind()
        }, {
          contactAlgebra.updateContact(it, contact, cred).bind()
        })
      }).fold({
        logAlgebra.error("Failed to execute upsertContact").bind()
        logAlgebra.error(it.toString()).bind()

        emitAlgebra.emitError(it).bind()
      }, {
        logAlgebra.info("Successfully executed upsertContact").bind()

        emitAlgebra.emitMessage(it).bind()
      })
    }
}