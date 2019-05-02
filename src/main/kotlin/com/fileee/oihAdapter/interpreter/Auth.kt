package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.core.left
import arrow.core.none
import arrow.core.right
import arrow.core.some
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.REFRESH_TOKEN_URL
import com.fileee.oihAdapter.USER_INFO
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.createHeaderFromCredentials
import com.fileee.oihAdapter.parseCredentials
import javax.json.Json

fun <F> authInterpreter(
  httpAlgebra: HttpAlgebra<F>,
  parseAlgebra: ParseAlgebra<F>,
  emitAlgebra: EmitAlgebra<F>,
  monadF: Monad<F>
): AuthAlgebra<F> =
  object : AuthAlgebra<F> {
    override val M: Monad<F> = monadF

    override fun verifyCredentials(cred: Credentials): Kind<F, VerifyCredentialsResult> =
      M.binding {
        val resp = httpAlgebra.httpGet(USER_INFO, createHeaderFromCredentials(cred)).bind()
          .fold({
            // short circuit with the error (cool hacky kotlin stuff, non-local returns ftw)
            return@binding VerifyCredentialsException.UnhandledException(it).some()
          }, { it })

        when (resp.responseStatus) {
          401, 403 -> {
            VerifyCredentialsException.InvalidCredentials.some()
          }
          in 400..599 -> {
            VerifyCredentialsException.UnhandledException(Exception("Bad response from fileee api. Status: ${resp.responseStatus} Message: ${resp.responseMessage}")).some()
          }
          else -> {
            none()
          }
        }
      }

    // FIXME This does not work correctly atm, fileee oauth works a bit different
    override fun refreshCredentials(cred: Credentials): Kind<F, RefreshCredentialsResult> =
      M.binding {
        val refreshBody = Json.createObjectBuilder()
          .add("grant_type", "TODO")
          .add("client_id", "TODO")
          .add("client_secret", "TODO")
          .add("redirect_uri", "TODO")
          .add("refresh_token", cred.refreshToken)
          .build()

        val resp = httpAlgebra.httpPost(
          REFRESH_TOKEN_URL,
          emptyMap(),
          refreshBody.toString()
        ).bind().fold({ return@binding RefreshCredentialsException.UnhandledException(it).left() }, { it })

        when (resp.responseStatus) {
          401 -> RefreshCredentialsException.InvalidRefreshToken.left()
          in 400..599 -> RefreshCredentialsException.UnhandledException(Exception("Bad response from fileee api. Status: ${resp.responseStatus} Message: ${resp.responseMessage}")).left()
          else -> {
            parseAlgebra.parseJsonObject(resp.responseMessage).bind()
              .fold({
                return@binding RefreshCredentialsException.UnhandledException(it).left()
              }, { conf ->
                parseCredentials(conf)
                  .fold({
                    RefreshCredentialsException.UnhandledException(Exception("Failed to parseAlgebra credentials")).left()
                  }, {
                    emitAlgebra.emitKeys(conf).bind()
                    it.right()
                  })
              })
          }
        }
      }
  }
