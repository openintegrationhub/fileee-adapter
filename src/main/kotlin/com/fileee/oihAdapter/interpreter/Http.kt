package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.core.Try
import arrow.core.left
import arrow.core.right
import arrow.effects.typeclasses.Async
import arrow.effects.typeclasses.milliseconds
import arrow.effects.typeclasses.seconds
import arrow.typeclasses.binding
import arrow.typeclasses.bindingCatch
import com.fileee.oihAdapter.Schedule
import com.fileee.oihAdapter.algebra.*
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.httpDelete
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.httpPut
import com.github.kittinunf.result.Result

typealias HttpFuelResp = Triple<Request, Response, Result<String, FuelError>>

/**
 * Interpreter for [HttpAlgebra]. This raises the constraint for [M] from [Monad] to [Async]. [Async] models
 *  asynchronous behaviour and is a requirement for [Schedule].
 */
class HttpInterpreter<F>(
  val logAlgebra: LogAlgebra<F>,
  override val M: Async<F>
) : HttpAlgebra<F> {

  /**
   * Handles responses from Fuel, the http library used and retries for a certain amount of time on specific errors.
   *
   * @param tr A function to try and run which produces a [HttpFuelResp]
   */
  internal fun handleFuelResponse(tr: () -> Kind<F, HttpFuelResp>): Kind<F, HttpResponse> = M.bindingCatch {
    // below schedule equates to exponential backoff with the power of 2 and a base of 10ms till it reaches
    //  60 seconds, then it continues with one retry per minute up to five times
    Schedule.exponential(10.milliseconds).whileValue { it.lte(60.seconds) }
      .andThen(Schedule.spaced(60.seconds) * 5)
      // run the schedule with tr until it succeeds or the schedule ends
      .runS(M, tr).attempt().bind()
      .fold({ HttpException.UnhandledException(it).left() }, { (_, resp, res) ->
        res.fold({
          HttpResult(resp.statusCode, it).right()
        }, {
          HttpResult(resp.statusCode, it.response.responseMessage).right()
        })
      })
  }

  /**
   * Transforms a [Try<HttpFuelResp>] to a [Kind<F, HttpFuelResponse>] where [F] has an instance of [MonadError]
   *  (which [Async] is extends)
   *
   * Raises errors for all retry-able error cases (Unhandled errors, usually connection issues), and rate-limit or service
   *  unavailable error codes.
   * Everything else is accepted as either finished or non-retry-able.
   */
  internal fun Try<HttpFuelResp>.raiseRetryErrors() = fold({
    M.raiseError<HttpFuelResp>(it)
  }, { resp ->
    when (resp.second.statusCode) {
      403, 401 -> M.just(resp)
      429 -> M.raiseError(Exception("Rate limit reached"))
      503, 504 -> M.raiseError(Exception("Service unavailable"))
      else -> M.just(resp)
    }
  })

  override fun httpGet(path: String, headers: Map<String, String>): Kind<F, HttpResponse> =
    handleFuelResponse {
      M.binding {
        logAlgebra.debug("HttpGet to $path").bind()
        Try {
          path.httpGet()
            .header(headers)
            .responseString()
        }.raiseRetryErrors().bind()
      }
    }

  override fun httpPost(path: String, headers: Map<String, String>, body: String): Kind<F, HttpResponse> =
    handleFuelResponse {
      M.binding {
        logAlgebra.debug("HttpPost to $path").bind()
        Try {
          path.httpPost()
            .body(body)
            .header(headers + mapOf("Content-Type" to "application/json"))
            .responseString()
        }.raiseRetryErrors().bind()
      }
    }

  override fun httpPut(path: String, headers: Map<String, String>, body: String): Kind<F, HttpResponse> =
    handleFuelResponse {
      M.binding {
        logAlgebra.debug("HttpPut to $path").bind()
        Try {
          path.httpPut()
            .body(body)
            .header(headers + mapOf("Content-Type" to "application/json"))
            .responseString()
        }.raiseRetryErrors().bind()
      }
    }

  override fun httpDelete(path: String, headers: Map<String, String>): Kind<F, HttpResponse> =
    handleFuelResponse {
      M.binding {
        logAlgebra.debug("HttpDelete to $path").bind()
        Try {
          path.httpDelete()
            .header(headers)
            .responseString()
        }.raiseRetryErrors().bind()
      }
    }
}