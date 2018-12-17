package com.fileee.oihAdapter.algebra

import arrow.Kind
import arrow.core.Either
import arrow.typeclasses.Monad

sealed class HttpException : Throwable() {
  object NoConnection : HttpException()
  class UnhandledException(val exc: Throwable) : HttpException()

  override fun toString(): String = when (this) {
    is NoConnection -> "No connection"
    is UnhandledException -> exc.toString()
  }

  override fun equals(other: Any?): Boolean = when (this) {
    is NoConnection -> other is NoConnection
    is UnhandledException -> if (other is UnhandledException) exc == other.exc else false
  }
}

data class HttpResult(
  val responseStatus: Int,
  val responseMessage: String
)

typealias HttpResponse = Either<HttpException, HttpResult>

/**
 * Simple algebra for modeling httpAlgebra requests
 */
interface HttpAlgebra<F> {
  val M: Monad<F>
  fun httpGet(path: String, headers: Map<String, String>): Kind<F, HttpResponse>
  fun httpPost(path: String, headers: Map<String, String>, body: String): Kind<F, HttpResponse>
  fun httpPut(path: String, headers: Map<String, String>, body: String): Kind<F, HttpResponse>
  fun httpDelete(path: String, headers: Map<String, String>): Kind<F, HttpResponse>
}