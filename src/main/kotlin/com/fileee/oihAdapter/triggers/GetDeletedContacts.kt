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

open class GetDeletedContacts : Module {
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
          arrow.effects.IO.monadDefer()
        ),
        LogInterpreter(
          LoggerFactory.getLogger(GetContacts::class.java),
          arrow.effects.IO.monadDefer()
        ),
        IO.monad()
      ).fix().unsafeRunSync()
    }
  }

  /**
   * Describes getDeletedContacts in terms of [ContactAlgebra], [EmitAlgebra] and some logging with [LogAlgebra] over a
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
      logAlgebra.info("Executing getDeletedContacts").bind()

      val lastModified = snapshot.flatMap { parseLastModified(DELETED_CONTACT_TYPE, it) }.getOrElse { 0 }

      parseCredentials(configuration).toEither { ContactException.AuthException }
        .flatMap {
          contactAlgebra.getDeletedContactList(lastModified, it).bind()
        }.fold({
          logAlgebra.error("Failed to execute getDeletedContacts").bind()
          logAlgebra.error(it.toString()).bind()

          emitAlgebra.emitError(it).bind()
        }, {
          logAlgebra.info("Successfully executed getDeletedContacts").bind()

          val snap = createNewSnapFromOld(DELETED_CONTACT_TYPE, snapshot)

          emitAlgebra.emitSnapshot(snap).bind()

          it.traverse(monadF) { it.traverse(monadF) { emitAlgebra.emitMessage(it) } }.bind()
        })
    }
}