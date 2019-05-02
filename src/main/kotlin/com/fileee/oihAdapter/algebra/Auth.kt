package com.fileee.oihAdapter.algebra

import arrow.Kind
import arrow.core.Either
import arrow.core.Option
import arrow.typeclasses.Monad

data class Credentials(
  val accessToken: String,
  val refreshToken: String
)

sealed class VerifyCredentialsException : Throwable() {
  object InvalidCredentials : VerifyCredentialsException()
  class BadApiResponse(val status: Int, val msg: String) : VerifyCredentialsException()
  class UnhandledException(val exc: Throwable) : VerifyCredentialsException()

  override fun toString(): String = when (this) {
    is InvalidCredentials -> "Failed to verify credentials, supplied token was invalid"
    is BadApiResponse -> "Bad response from the api. Status: $status. Message: $msg"
    is UnhandledException -> exc.toString()
  }

  override fun equals(other: Any?): Boolean = when (this) {
    is InvalidCredentials -> other is InvalidCredentials
    is BadApiResponse -> if (other is BadApiResponse) status == other.status && msg == other.msg else false
    is UnhandledException -> if (other is UnhandledException) exc == other.exc else false
  }
}

typealias VerifyCredentialsResult = Option<VerifyCredentialsException>

sealed class RefreshCredentialsException : Throwable() {
  object InvalidRefreshToken : RefreshCredentialsException()
  class BadApiResponse(val status: Int, val msg: String) : RefreshCredentialsException()
  class UnhandledException(val exc: Throwable) : RefreshCredentialsException()

  override fun toString(): String = when (this) {
    is InvalidRefreshToken -> "Failed to refresh credentials, supplied refresh token was invalid"
    is BadApiResponse -> "Bad response from the api. Status: $status. Message: $msg"
    is UnhandledException -> exc.toString()
  }

  override fun equals(other: Any?): Boolean = when (this) {
    is InvalidRefreshToken -> other is InvalidRefreshToken
    is BadApiResponse -> if (other is BadApiResponse) status == other.status && msg == other.msg else false
    is UnhandledException -> if (other is UnhandledException) exc == other.exc else false
  }
}

typealias RefreshCredentialsResult = Either<RefreshCredentialsException, Credentials>

/**
 * Basic algebra for dealing with authentication that supports verifying and refreshing oauth credentials
 */
interface AuthAlgebra<F> {
  val M: Monad<F>
  fun verifyCredentials(cred: Credentials): Kind<F, VerifyCredentialsResult>
  fun refreshCredentials(cred: Credentials): Kind<F, RefreshCredentialsResult>
}