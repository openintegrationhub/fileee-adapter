package com.fileee.oihAdapter.actions

import arrow.core.flatMap
import arrow.core.some
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monad
import arrow.effects.monadDefer
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.algebra.ContactAlgebra
import com.fileee.oihAdapter.algebra.ContactException
import com.fileee.oihAdapter.algebra.EmitAlgebra
import com.fileee.oihAdapter.algebra.LogAlgebra
import com.fileee.oihAdapter.defaultContactInterpreter
import com.fileee.oihAdapter.getId
import com.fileee.oihAdapter.interpreter.EmitInterpreter
import com.fileee.oihAdapter.interpreter.LogInterpreter
import com.fileee.oihAdapter.parseCredentials
import io.elastic.api.ExecutionParameters
import io.elastic.api.Module
import org.slf4j.LoggerFactory
import javax.json.JsonObject

open class LookupContact : Module {

  override fun execute(parameters: ExecutionParameters?) {
    parameters?.let { parameters ->
      getContact(
        parameters.message.body,
        parameters.configuration,
        defaultContactInterpreter(
          parameters.eventEmitter
        ),
        EmitInterpreter(parameters.eventEmitter.some(), IO.monadDefer()),
        LogInterpreter(
          LoggerFactory.getLogger(LookupContact::class.java),
          IO.monadDefer()
        ),
        IO.monad()
      ).fix().unsafeRunSync()
    }
  }

  /**
   * Describes getContact in terms of [ContactAlgebra], [EmitAlgebra] and some logging with [LogAlgebra] over a
   *  monadic kind [F].
   */
  internal fun <F> getContact(
    input: JsonObject,
    configuration: JsonObject,
    contactAlgebra: ContactAlgebra<F>,
    emitAlgebra: EmitAlgebra<F>,
    logAlgebra: LogAlgebra<F>,
    monadF: Monad<F>
  ) =
    monadF.binding {
      logAlgebra.info("Executing getContact").bind()

      getId(input).toEither { ContactException.MissingOrInvalidId }
        .flatMap { id ->
          parseCredentials(configuration).map { Pair(id, it) }.toEither { ContactException.AuthException }
        }.flatMap { (id, cred) ->
          contactAlgebra.getContact(id, cred).bind()
        }.fold({
          logAlgebra.error("Failed to execute getContact").bind()
          logAlgebra.error(it.toString()).bind()

          emitAlgebra.emitError(it).bind()
        }, {
          logAlgebra.info("Successfully executed getContact").bind()

          emitAlgebra.emitMessage(it).bind()
        })
    }
}