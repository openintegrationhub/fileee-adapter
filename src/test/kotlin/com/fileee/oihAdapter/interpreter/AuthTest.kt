package com.fileee.oihAdapter.interpreter

import arrow.core.*
import com.fileee.oihAdapter.USER_INFO
import com.fileee.oihAdapter.algebra.*
import io.kotlintest.assertions.arrow.option.beNone
import io.kotlintest.assertions.arrow.option.beSome
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class VerifyCredentialsSpec : StringSpec({
    "verifyCredentials should return correctly for correct credentials" {
        val httpMock = mockk<HttpAlgebra<ForId>>()
        val parseMock = mockk<ParseAlgebra<ForId>>()
        val emitMock = mockk<EmitAlgebra<ForId>>()

        val authAlg = authInterpreter(
                httpMock,
                parseMock,
                emitMock,
                Id.monad()
        )

        every { httpMock.httpGet(any(), any()) } answers {
            Id.just(HttpResult(
                    responseMessage = "{}",
                    responseStatus = 200
            ).right())
        }

        val result = authAlg.verifyCredentials(
                Credentials(
                        "MyCorrectToken",
                        "someToken"
                )
        ).value()

        result shouldBe beNone()

        verify {
            httpMock.httpGet(USER_INFO, match { it["Authorization"] == "Bearer MyCorrectToken" })
        }
    }
    "verifyCredentials should return correctly for incorrect credentials" {
        val httpMock = mockk<HttpAlgebra<ForId>>()
        val parseMock = mockk<ParseAlgebra<ForId>>()
        val emitMock = mockk<EmitAlgebra<ForId>>()

        val authAlg = authInterpreter(
                httpMock,
                parseMock,
                emitMock,
                Id.monad()
        )

        every { httpMock.httpGet(any(), any()) } answers {
            Id.just(HttpResult(
                    responseMessage = "{}",
                    responseStatus = 401
            ).right())
        }

        val result = authAlg.verifyCredentials(
                Credentials(
                        "MyInCorrectToken",
                        "someToken"
                )
        ).value()

        result shouldBe beSome(VerifyCredentialsException.InvalidCredentials)

        verify {
            httpMock.httpGet(USER_INFO, match { it["Authorization"] == "Bearer MyInCorrectToken" })
        }
    }
})

// TODO refresh cred test