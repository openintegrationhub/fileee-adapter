package com.fileee.oihAdapter.actions

import arrow.core.*
import com.fileee.oihAdapter.algebra.ContactAlgebra
import com.fileee.oihAdapter.algebra.ContactException
import com.fileee.oihAdapter.algebra.EmitAlgebra
import com.fileee.oihAdapter.credentialsConfig
import com.fileee.oihAdapter.logMock
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.json.Json

class LookupContactSpec : StringSpec({
  "lookupContact fails fast when no id is found" {
    val lookupContact = LookupContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()

    every { emitMock.emitError(any()) } returns Id.just(Unit)

    lookupContact.getContact(
            Json.createObjectBuilder().build(),
            Json.createObjectBuilder().build(),
            mockk(), emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.MissingOrInvalidId)
    }
  }
  "lookupContact fails fast when no authAlgebra config is found" {
    val lookupContact = LookupContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()

    every { emitMock.emitError(any()) } returns Id.just(Unit)

    lookupContact.getContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            Json.createObjectBuilder().build(),
            mockk(), emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
    }
  }
  "lookupContact should emit errors from getContact" {
    val lookupContact = LookupContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.getContact(any(), any()) } answers {
      Id.just(ContactException.AuthException.left())
    }
    every { emitMock.emitError(any()) } returns Id.just(Unit)

    lookupContact.getContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
    }
  }
  "lookupContact should emit correct value on successful get" {
    val lookupContact = LookupContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.getContact(any(), any()) } answers {
      Id.just(Json.createObjectBuilder().build().right())
    }
    every { emitMock.emitMessage(any()) } returns Id.just(Unit)

    lookupContact.getContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitMessage(Json.createObjectBuilder().build())
    }
  }
})