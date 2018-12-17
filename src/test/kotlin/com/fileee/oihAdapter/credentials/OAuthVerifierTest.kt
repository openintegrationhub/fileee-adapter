package com.fileee.oihAdapter.credentials

import arrow.core.*
import com.fileee.oihAdapter.algebra.AuthAlgebra
import com.fileee.oihAdapter.algebra.VerifyCredentialsException
import com.fileee.oihAdapter.credentialsConfig
import com.fileee.oihAdapter.logMock
import com.fileee.oihAdapter.parseCredentials
import io.elastic.api.InvalidCredentialsException
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.json.Json

class OAuthVerifierSpec : StringSpec({
  "verifyCredentials should fail fast if no credentials could be parsed" {
    val verifier = OAuthVerifier()

    val result = Try {
      verifier.verifyCredentials(
              Json.createObjectBuilder().build(),
              mockk(), logMock(),
              Id.monad()
      ).value()
    }

    result.isFailure() shouldBe true
    result.recover { (it is InvalidCredentialsException) shouldBe true }
  }
  "verifyCredentials should throw if credentials were invalid" {
    val verifier = OAuthVerifier()
    val authMock = mockk<AuthAlgebra<ForId>>()

    every { authMock.verifyCredentials(any()) } answers {
      Id.just(VerifyCredentialsException.InvalidCredentials.some())
    }

    val result = Try {
      verifier.verifyCredentials(
              credentialsConfig,
              authMock, logMock(),
              Id.monad()
      ).value()
    }

    result.isFailure() shouldBe true
    result.recover { (it is InvalidCredentialsException) shouldBe true }

    verify {
      authMock.verifyCredentials(parseCredentials(credentialsConfig).get())
    }
  }
  "verifyCredentials should not throw on valid credentials" {
    val verifier = OAuthVerifier()
    val authMock = mockk<AuthAlgebra<ForId>>()

    every { authMock.verifyCredentials(any()) } answers {
      Id.just(none())
    }

    val result = Try {
      verifier.verifyCredentials(
              credentialsConfig,
              authMock, logMock(),
              Id.monad()
      ).value()
    }

    result.isSuccess() shouldBe true
    verify {
      authMock.verifyCredentials(parseCredentials(credentialsConfig).get())
    }
  }
})