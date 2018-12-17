package com.fileee.oihAdapter.credentials

import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monad
import arrow.effects.monadDefer
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.algebra.AuthAlgebra
import com.fileee.oihAdapter.algebra.LogAlgebra
import com.fileee.oihAdapter.algebra.VerifyCredentialsException
import com.fileee.oihAdapter.defaultAuthInterpreter
import com.fileee.oihAdapter.interpreter.LogInterpreter
import com.fileee.oihAdapter.parseCredentials
import io.elastic.api.CredentialsVerifier
import io.elastic.api.InvalidCredentialsException
import org.slf4j.LoggerFactory
import javax.json.JsonObject

class OAuthVerifier : CredentialsVerifier {
  override fun verify(configuration: JsonObject?) {
    configuration?.let { conf ->

      verifyCredentials(
        conf,
        defaultAuthInterpreter(),
        LogInterpreter(
          LoggerFactory.getLogger(OAuthVerifier::class.java),
          IO.monadDefer()
        ),
        IO.monad()
      ).fix().unsafeRunSync()
    }
  }

  /**
   * Describes verify credentials in terms of [AuthAlgebra] and for logging [LogAlgebra] over a monadic kind [F].
   */
  internal fun <F> verifyCredentials(
    configuration: JsonObject,
    authAlgebra: AuthAlgebra<F>,
    logAlgebra: LogAlgebra<F>,
    monadF: Monad<F>
  ) =
    monadF.binding {
      logAlgebra.info("Verifying credentials").bind()

      parseCredentials(configuration).toEither { VerifyCredentialsException.InvalidCredentials }
        .flatMap {
          authAlgebra.verifyCredentials(it).bind().fold({ Unit.right() }, { it.left() })
        }.fold({
          logAlgebra.error("Failed to verify credentials").bind()
          logAlgebra.error(it.toString()).bind()

          // Actually throw exception to tell elasticio something is wrong.
          throw InvalidCredentialsException()
        }, {
          logAlgebra.info("Successfully verified credentials").bind()
        })
    }
}