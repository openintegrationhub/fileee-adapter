package com.fileee.oihAdapter.interpreter

import arrow.core.Try
import arrow.effects.*
import com.fileee.oihAdapter.API_BASE_URL
import com.fileee.oihAdapter.algebra.HttpResult
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import io.kotlintest.assertions.arrow.either.beLeft
import io.kotlintest.assertions.arrow.either.beRight
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URL

class HttpResponseHandlingSpec : StringSpec({
    "raiseTryErrors should not raise error for correct input" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val resp = HttpFuelResp(
                Request(Method.GET, API_BASE_URL, URL(API_BASE_URL), timeoutInMillisecond = 1, timeoutReadInMillisecond = 1),
                Response(URL(API_BASE_URL), statusCode = 200),
                Result.of { "Content" }
        )
        val io = httpAlg.run {
            Try.just(resp).raiseRetryErrors().fix()
        }

        val result = io.attempt().unsafeRunSync()

        result shouldBe beRight(resp)
    }
    "raiseTryErrors should not raise error for authAlgebra errors" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val resp = HttpFuelResp(
                Request(Method.GET, API_BASE_URL, URL(API_BASE_URL), timeoutInMillisecond = 1, timeoutReadInMillisecond = 1),
                Response(URL(API_BASE_URL), statusCode = 403),
                Result.of { "Content" }
        )
        val io = httpAlg.run {
            Try.just(resp).raiseRetryErrors().fix()
        }

        val result = io.attempt().unsafeRunSync()

        result shouldBe beRight(resp)
    }
    "raiseTryErrors should raise error for thrown exceptions" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val exc = Exception("MyException")
        val io = httpAlg.run {
            Try.raise<HttpFuelResp>(exc).raiseRetryErrors().fix()
        }

        val result = io.attempt().unsafeRunSync()

        result shouldBe beLeft(exc)
    }
    "raiseTryErrors should raise error for rate limit errors" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val resp = HttpFuelResp(
                Request(Method.GET, API_BASE_URL, URL(API_BASE_URL), timeoutInMillisecond = 1, timeoutReadInMillisecond = 1),
                Response(URL(API_BASE_URL), statusCode = 429),
                Result.of { "Content" }
        )
        val io = httpAlg.run {
            Try.just(resp).raiseRetryErrors().fix()
        }

        val result = io.attempt().unsafeRunSync()

        result.isLeft() shouldBe true
        // Exception shouldBe Exception works only on referential eq
        result.mapLeft { it.message shouldBe "Rate limit reached" }
    }
    "raiseTryErrors should raise error for service unavailable errors" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val resp = HttpFuelResp(
                Request(Method.GET, API_BASE_URL, URL(API_BASE_URL), timeoutInMillisecond = 1, timeoutReadInMillisecond = 1),
                Response(URL(API_BASE_URL), statusCode = 503),
                Result.of { "Content" }
        )
        val io = httpAlg.run {
            Try.just(resp).raiseRetryErrors().fix()
        }

        val result = io.attempt().unsafeRunSync()

        result.isLeft() shouldBe true
        // Exception shouldBe Exception works only on referential eq
        result.mapLeft { it.message shouldBe "Service unavailable" }
    }
})

class HttpHandleFuelResponseSpec : StringSpec({
    "handleFuelResponse should return a correct response" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val io = httpAlg.handleFuelResponse {
            HttpFuelResp(
                    Request(Method.GET, API_BASE_URL, URL(API_BASE_URL), timeoutReadInMillisecond = 1, timeoutInMillisecond = 1),
                    Response(URL(API_BASE_URL), statusCode = 200, responseMessage = "MyResp"),
                    Result.of { "MyResp" }
            ).liftIO()
        }.fix()

        val result = io.unsafeRunSync()
        val expected = HttpResult(200, "MyResp")

        result shouldBe beRight(expected)
    }
    "handleFuelResponse should retry an error untill it works (or backoff runs out)" {
        val httpAlg = HttpInterpreter(mockk(), IO.async())

        val mockMethod = mockk<() -> IO<HttpFuelResp>>()

        var tries = 0
        every { mockMethod() } answers {
            if (++tries < 5) IO.raiseError<HttpFuelResp>(Exception("MyExc"))
            else HttpFuelResp(
                    Request(Method.GET, API_BASE_URL, URL(API_BASE_URL), timeoutReadInMillisecond = 1, timeoutInMillisecond = 1),
                    Response(URL(API_BASE_URL), statusCode = 200, responseMessage = "MyResp"),
                    Result.of { "MyResp" }
            ).liftIO()
        }

        val io = httpAlg.handleFuelResponse(mockMethod).fix()

        val result = io.unsafeRunSync()
        val expected = HttpResult(200, "MyResp")

        result shouldBe beRight(expected)

        verify(exactly = 5) {
            mockMethod()
        }
    }
})

// TODO test all methods if they create correct requests
//  request handling is tested above
