package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.core.Try
import arrow.effects.typeclasses.MonadDefer
import com.fileee.oihAdapter.algebra.ParseAlgebra
import com.fileee.oihAdapter.algebra.ParseException
import com.fileee.oihAdapter.algebra.ParseResult
import javax.json.Json
import javax.json.JsonArray
import javax.json.JsonException
import javax.json.JsonObject
import javax.json.stream.JsonParsingException

class ParseInterpreter<F>(
  override val M: MonadDefer<F>
) : ParseAlgebra<F> {

  override fun parseJsonObject(json: String): Kind<F, ParseResult<JsonObject>> =
    M {
      Try {
        Json.createReader(json.reader()).readObject()
      }.toEither().mapLeft {
        when (it) {
          is JsonException, is JsonParsingException -> ParseException.FailedToParse(json)
          else -> ParseException.UnhandledException(it)
        }
      }
    }

  override fun parseJsonArray(json: String): Kind<F, ParseResult<JsonArray>> =
    M {
      Try {
        Json.createReader(json.reader()).readArray()
      }.toEither().mapLeft {
        when (it) {
          is JsonException, is JsonParsingException -> ParseException.FailedToParse(json)
          else -> ParseException.UnhandledException(it)
        }
      }
    }
}