package com.fileee.oihAdapter.algebra

import arrow.Kind
import arrow.core.Either
import arrow.typeclasses.Monad
import javax.json.JsonArray
import javax.json.JsonObject

sealed class ParseException : Throwable() {
  class FailedToParse(val input: String) : ParseException()
  class UnhandledException(val exc: Throwable) : ParseException()

  override fun toString(): String = when (this) {
    is FailedToParse -> "Failed to parseAlgebra json $input"
    is UnhandledException -> exc.toString()
  }

  override fun equals(other: Any?): Boolean = when (this) {
    is FailedToParse -> if (other is FailedToParse) input == other.input else false
    is UnhandledException -> if (other is UnhandledException) exc == other.exc else false
  }
}

typealias ParseResult<T> = Either<ParseException, T>

interface ParseAlgebra<F> {
  val M: Monad<F>
  fun parseJsonObject(json: String): Kind<F, ParseResult<JsonObject>>
  fun parseJsonArray(json: String): Kind<F, ParseResult<JsonArray>>
}