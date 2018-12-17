package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.core.*
import arrow.data.Nel
import com.fileee.oihAdapter.PERSONS_API_URL
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.createHeaderFromCredentials
import io.kotlintest.assertions.arrow.either.beLeft
import io.kotlintest.assertions.arrow.either.beRight
import io.kotlintest.assertions.arrow.option.beNone
import io.kotlintest.assertions.arrow.option.beSome
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.json.Json

val noCanDoFunc: (Any) -> Nothing = { throw Exception("No can do") }

typealias HandlerResult = Kind<ForId, Either<Nothing, String>>

class HandleHttpResponseSpec : StringSpec({
  "handleHttpResponse should call onSuccess for a successfull call" {
    val contactAlg = ContactInterpreter(
            mockk(), mockk(), mockk(), Id.monad()
    )

    val func = mockk<(HttpResult) -> HandlerResult>()

    every { func(any()) } returns Id.just("MyValue".right())

    val result = contactAlg.handleHttpResponse(
            { Id.just(HttpResult(200, "{}").right()) },
            noCanDoFunc, noCanDoFunc, noCanDoFunc, func,
            Credentials("", "")
    ).value()

    result shouldBe beRight("MyValue")

    verify {
      func(HttpResult(200, "{}"))
    }
  }

  "handleHttpResponse should call onError for an error call" {
    val contactAlg = ContactInterpreter(
            mockk(), mockk(), mockk(), Id.monad()
    )

    val func = mockk<(Throwable) -> HandlerResult>()

    every { func(any()) } returns Id.just("MyValue".right())

    val result = contactAlg.handleHttpResponse(
            { Id.just(HttpException.NoConnection.left()) },
            func, noCanDoFunc, noCanDoFunc, noCanDoFunc,
            Credentials("", "")
    ).value()

    result shouldBe beRight("MyValue")

    verify {
      func(HttpException.NoConnection)
    }
  }

  "handleHttpResponse should call onAuthError for a 403 response with one failing attempt at refreshing credentials" {
    val authMock = mockk<AuthAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            mockk(), mockk(), authMock, Id.monad()
    )

    val genResponse = mockk<(Credentials) -> Kind<ForId, HttpResponse>>()
    val func = mockk<(HttpResult) -> HandlerResult>()

    every { genResponse(any()) } returns Id.just(HttpResult(403, "Forbidden").right())
    every { func(any()) } returns Id.just("MyValue".right())
    every { authMock.refreshCredentials(any()) } returns Id.just(Credentials(
            "NewToken", "NewRefreshToken"
    ).right())

    val result = contactAlg.handleHttpResponse(
            genResponse,
            noCanDoFunc, func, noCanDoFunc, noCanDoFunc,
            Credentials("", "")
    ).value()

    result shouldBe beRight("MyValue")

    verify(exactly = 1) {
      func(HttpResult(403, "Forbidden"))
    }
    verify {
      genResponse(Credentials("", ""))
      genResponse(Credentials("NewToken", "NewRefreshToken"))
    }
  }

  "handleHttpResponse should call onSuccess after one 403 response with successfull refreshing credentials" {
    val authMock = mockk<AuthAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            mockk(), mockk(), authMock, Id.monad()
    )

    val genResponse = mockk<(Credentials) -> Kind<ForId, HttpResponse>>()
    val func = mockk<(HttpResult) -> HandlerResult>()

    every { genResponse(any()) } answers {
      Id.just(HttpResult(403, "Forbidden").right())
    } andThen {
      Id.just(HttpResult(200, "{}").right())
    }
    every { func(any()) } returns Id.just("MyValue".right())
    every { authMock.refreshCredentials(any()) } returns Id.just(Credentials(
            "NewToken", "NewRefreshToken"
    ).right())

    val result = contactAlg.handleHttpResponse(
            genResponse,
            noCanDoFunc, noCanDoFunc, noCanDoFunc, func,
            Credentials("", "")
    ).value()

    result shouldBe beRight("MyValue")

    verify(exactly = 1) {
      func(HttpResult(200, "{}"))
    }
    verify {
      genResponse(Credentials("", ""))
      genResponse(Credentials("NewToken", "NewRefreshToken"))
    }
  }

  "handleHttpResponse should call onApiException for a bad api response" {
    val contactAlg = ContactInterpreter(
            mockk(), mockk(), mockk(), Id.monad()
    )

    val func = mockk<(HttpResult) -> Kind<ForId, Either<Nothing, String>>>()

    every { func(any()) } returns Id.just("MyValue".right())

    val result = contactAlg.handleHttpResponse(
            { Id.just(HttpResult(404, "Not found").right()) },
            noCanDoFunc, noCanDoFunc, func, noCanDoFunc,
            Credentials("", "")
    ).value()

    result shouldBe beRight("MyValue")

    verify(exactly = 1) {
      func(HttpResult(404, "Not found"))
    }
  }
})

fun <T>testUnhandledException(f: ContactAlgebra<ForId>.() -> IdOf<T>): T {
  val httpMock = mockk<HttpAlgebra<ForId>>()
  val contactAlg = ContactInterpreter(
          httpMock, mockk(), mockk(), Id.monad()
  )

  every { httpMock.httpGet(any(), any()) } returns Id.just(HttpException.NoConnection.left())
  every { httpMock.httpPost(any(), any(), any()) } returns Id.just(HttpException.NoConnection.left())
  every { httpMock.httpPut(any(), any(), any()) } returns Id.just(HttpException.NoConnection.left())
  every { httpMock.httpDelete(any(), any()) } returns Id.just(HttpException.NoConnection.left())

  return contactAlg.f().value()
}

fun <T>testNotFound(f: ContactAlgebra<ForId>.() -> IdOf<T>): T {
  val httpMock = mockk<HttpAlgebra<ForId>>()
  val contactAlg = ContactInterpreter(
          httpMock, mockk(), mockk(), Id.monad()
  )

  every { httpMock.httpGet(any(), any()) } answers {
    Id.just(HttpResult(404, "Not found").right())
  }
  every { httpMock.httpPost(any(), any(), any()) } answers {
    Id.just(HttpResult(404, "Not found").right())
  }
  every { httpMock.httpPut(any(), any(), any()) } answers {
    Id.just(HttpResult(404, "Not found").right())
  }
  every { httpMock.httpDelete(any(), any()) } answers {
    Id.just(HttpResult(404, "Not found").right())
  }

  return contactAlg.f().value()
}

fun <T>testAuth(f: ContactAlgebra<ForId>.() -> IdOf<T>): T {
  val authAlg = mockk<AuthAlgebra<ForId>>()
  val httpMock = mockk<HttpAlgebra<ForId>>()
  val contactAlg = ContactInterpreter(
          httpMock, mockk(), authAlg, Id.monad()
  )

  every { authAlg.refreshCredentials(any()) } answers {
    Id.just(RefreshCredentialsException.InvalidRefreshToken.left())
  }
  every { httpMock.httpGet(any(), any()) } answers {
    Id.just(HttpResult(403, "Unauthorized").right())
  }
  every { httpMock.httpPost(any(), any(), any()) } answers {
    Id.just(HttpResult(403, "Unauthorized").right())
  }
  every { httpMock.httpPut(any(), any(), any()) } answers {
    Id.just(HttpResult(403, "Unauthorized").right())
  }
  every { httpMock.httpDelete(any(), any()) } answers {
    Id.just(HttpResult(403, "Unauthorized").right())
  }

  return contactAlg.f().value()
}

fun <T>testBadApi(f: ContactAlgebra<ForId>.() -> IdOf<T>): T {
  val httpMock = mockk<HttpAlgebra<ForId>>()
  val contactAlg = ContactInterpreter(
          httpMock, mockk(), mockk(), Id.monad()
  )

  every { httpMock.httpGet(any(), any()) } answers {
    Id.just(HttpResult(500, "Internal server error").right())
  }
  every { httpMock.httpPost(any(), any(), any()) } answers {
    Id.just(HttpResult(500, "Internal server error").right())
  }
  every { httpMock.httpPut(any(), any(), any()) } answers {
    Id.just(HttpResult(500, "Internal server error").right())
  }
  every { httpMock.httpDelete(any(), any()) } answers {
    Id.just(HttpResult(500, "Internal server error").right())
  }

  return contactAlg.f().value()
}

class GetContactSpec : StringSpec({
  "getContact should create a correct httpAlgebra and parseAlgebra call" {
    val httpMock = mockk<HttpAlgebra<ForId>>()
    val parseMock = mockk<ParseAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpMock, parseMock, mockk(), Id.monad()
    )

    every { parseMock.parseJsonObject(any()) } returns Id.just(Json.createObjectBuilder().build().right())
    every { httpMock.httpGet(any(), any()) } returns Id.just(HttpResult(200, "{}").right())

    val result = contactAlg.getContact("MyId", Credentials("MyToken", "")).value()

    result shouldBe beRight(Json.createObjectBuilder().build())

    verify(exactly = 1) {
      httpMock.httpGet("$PERSONS_API_URL/MyId", createHeaderFromCredentials(Credentials("MyToken", "")))
      parseMock.parseJsonObject("{}")
    }
  }
  "getContact should return UnhandledException on unexpected exceptions" {
    val result = testUnhandledException { getContact("", Credentials("", "")).fix() }

    result shouldBe beLeft(ContactException.UnhandledException(HttpException.NoConnection))
  }
  "getContact should return NotFoundException on 404 responses" {
    val result = testNotFound { getContact("MyId", Credentials("", "")) }

    result shouldBe beLeft(ContactException.ContactNotFound("MyId"))
  }
  "getContact should return authAlgebra exception on 403 responses" {
    val result = testAuth { getContact("MyId", Credentials("", "")) }

    result shouldBe beLeft(ContactException.AuthException)
  }
  "getContact should return bad api exception on a bad api response" {
    val result = testBadApi { getContact("MyId", Credentials("", "")) }

    result shouldBe beLeft(ContactException.BadApiResponse(500, "Internal server error"))
  }
})

class GetContactListSpec : StringSpec({
  "getContactList should return correctly for successful request for an empty array" {
    val httpMock = mockk<HttpAlgebra<ForId>>()
    val parseMock = mockk<ParseAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpMock, parseMock, mockk(), Id.monad()
    )

    every { parseMock.parseJsonArray(any()) } answers {
      Id.just(Json.createArrayBuilder().build().right())
    }
    every { httpMock.httpGet(any(), any()) } answers {
      Id.just(HttpResult(200, "[]").right())
    }

    val result = contactAlg.getContactList(0L, Credentials("", "")).value()

    result shouldBe beRight(none<Nel<Contact>>())
  }
  "getContactList should return correctly for successful request for an non empty array" {
    val httpMock = mockk<HttpAlgebra<ForId>>()
    val parseMock = mockk<ParseAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpMock, parseMock, mockk(), Id.monad()
    )

    every { parseMock.parseJsonArray(any()) } answers {
      Id.just(Json.createArrayBuilder().add(Json.createObjectBuilder()).build().right())
    }
    every { httpMock.httpGet(any(), any()) } answers {
      Id.just(HttpResult(200, "[{}]").right())
    }

    val result = contactAlg.getContactList(0L, Credentials("", "")).value()

    result shouldBe beRight(Nel(Json.createObjectBuilder().build()).some())
  }
  "getContactList should setup a correct httpAlgebra and parseAlgebra call for successful execution" {
    val httpMock = mockk<HttpAlgebra<ForId>>()
    val parseMock = mockk<ParseAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpMock, parseMock, mockk(), Id.monad()
    )

    every { parseMock.parseJsonArray(any()) } answers {
      Id.just(Json.createArrayBuilder().build().right())
    }
    every { httpMock.httpGet(any(), any()) } answers {
      Id.just(HttpResult(200, "[]").right())
    }

    contactAlg.getContactList(0L, Credentials("", ""))

    verify {
      parseMock.parseJsonArray("[]")
      httpMock.httpGet("$PERSONS_API_URL?modifiedAfter=0", createHeaderFromCredentials(Credentials("", "")))
    }
  }
  "getContactList should handle UnhandledExceptions correctly" {
    val result = testUnhandledException { getContactList(0L, Credentials("", "")) }

    result shouldBe beLeft(ContactException.UnhandledException(HttpException.NoConnection))
  }
  "getContactList should handle AuthExceptions correctly" {
    val result = testAuth { getContactList(0L, Credentials("", "")) }

    result shouldBe beLeft(ContactException.AuthException)
  }
  "getContactList should handle BadApiExceptions correctly" {
    val result = testBadApi { getContactList(0L, Credentials("", "")) }

    result shouldBe beLeft(ContactException.BadApiResponse(500, "Internal server error"))
  }
})

class CreateContactSpec : StringSpec({
  "createContact should setup correct httpAlgebra and parseAlgebra calls for successful execution" {
    val httpAlg = mockk<HttpAlgebra<ForId>>()
    val parseAlg = mockk<ParseAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpAlg, parseAlg, mockk(), Id.monad()
    )

    every { parseAlg.parseJsonObject(any()) } answers {
      Id.just(Json.createObjectBuilder().build().right())
    }
    every { httpAlg.httpPost(any(), any(), any()) } answers {
      Id.just(HttpResult(200, "{}").right())
    }

    val result = contactAlg.createContact(Json.createObjectBuilder().build(), Credentials("", "")).value()

    result shouldBe beRight(Json.createObjectBuilder().build())

    verify {
      parseAlg.parseJsonObject("{}")
      httpAlg.httpPost(PERSONS_API_URL, createHeaderFromCredentials(Credentials("", "")), "{}")
    }
  }
  "createContact should handle UnhandledExceptions correctly" {
    val result = testUnhandledException { createContact(Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.UnhandledException(HttpException.NoConnection))
  }
  "createContact should handle AuthExceptions correctly" {
    val result = testAuth { createContact(Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.AuthException)
  }
  "createContact should handle BadApiExceptions correctly" {
    val result = testBadApi { createContact(Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.BadApiResponse(500, "Internal server error"))
  }
})

class UpdateContactSpec : StringSpec({
  "updateContact should setup correct httpAlgebra and parseAlgebra calls on successful execution" {
    val parseAlg = mockk<ParseAlgebra<ForId>>()
    val httpAlg = mockk<HttpAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpAlg, parseAlg, mockk(), Id.monad()
    )

    every { parseAlg.parseJsonObject(any()) } answers {
      Id.just(Json.createObjectBuilder().build().right())
    }
    every { httpAlg.httpPut(any(), any(), any()) } answers {
      Id.just(HttpResult(200, "{}").right())
    }

    val result = contactAlg.updateContact("MyId", Json.createObjectBuilder().build(), Credentials("", "")).value()

    result shouldBe beRight(Json.createObjectBuilder().build())

    verify {
      parseAlg.parseJsonObject("{}")
      httpAlg.httpPut(
              "$PERSONS_API_URL/MyId",
              createHeaderFromCredentials(Credentials("", "")),
              "{}"
      )
    }
  }
  "updateContact should handle UnhandledExceptions correctly" {
    val result = testUnhandledException { updateContact("MyId", Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.UnhandledException(HttpException.NoConnection))
  }
  "updateContact should handle AuthExceptions correctly" {
    val result = testAuth { updateContact("MyId", Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.AuthException)
  }
  "updateContact should handle 404 Exceptions correctly" {
    val result = testNotFound { updateContact("MyId", Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.ContactNotFound("MyId"))
  }
  "updateContact should handle bad api exceptions correctly" {
    val result = testBadApi { updateContact("MyId", Json.createObjectBuilder().build(), Credentials("", "")) }

    result shouldBe beLeft(ContactException.BadApiResponse(500, "Internal server error"))
  }
})

class DeleteContactSpec : StringSpec({
  "deleteContact should setup httpAlgebra call correctly for successful execution" {
    val httpMock = mockk<HttpAlgebra<ForId>>()
    val contactAlg = ContactInterpreter(
            httpMock, mockk(), mockk(), Id.monad()
    )

    every { httpMock.httpDelete(any(), any()) } answers {
      Id.just(HttpResult(200, "Done").right())
    }

    val result = contactAlg.deleteContact("MyId", Credentials("", "")).value()

    result shouldBe beNone()

    verify {
      httpMock.httpDelete("$PERSONS_API_URL/MyId", createHeaderFromCredentials(Credentials("", "")))
    }
  }
  "deleteContact should handle UnhandledExceptions correctly" {
    val result = testUnhandledException { deleteContact("MyId", Credentials("", "")) }

    result shouldBe beSome(ContactException.UnhandledException(HttpException.NoConnection))
  }
  "deleteContact should handle AuthExceptions correctly" {
    val result = testAuth { deleteContact("MyId", Credentials("", "")) }

    result shouldBe beSome(ContactException.AuthException)
  }
  "deleteContact should handle 404 responses correctly" {
    val result = testNotFound { deleteContact("MyId", Credentials("", "")) }

    result shouldBe beSome(ContactException.ContactNotFound("MyId"))
  }
  "deleteContact should handle bad api responses correctly" {
    val result = testBadApi { deleteContact("MyId", Credentials("", "")) }

    result shouldBe beSome(ContactException.BadApiResponse(500, "Internal server error"))
  }
})