package com.fileee.oihAdapter.actions

import arrow.core.*
import com.fileee.oihAdapter.*
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.generators.credGen
import com.fileee.oihAdapter.generators.rand
import com.fileee.oihAdapter.generators.toJson
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import javax.json.Json

class DeleteContactSpec : StringSpec({

  "deleteContact should report a missing id correctly" {
    val delContact = DeleteContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()

    every { emitMock.emitError(any()) } returns Id.just(Unit)

    delContact.deleteContact(
            Json.createObjectBuilder().build(),
            Json.createObjectBuilder().build(),
            mockk(), emitMock, logMock(), Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.MissingOrInvalidId)
    }
  }

  "deleteContact should report missing credentials correctly" {
    val delContact = DeleteContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()

    every { emitMock.emitError(any()) } returns Id.just(Unit)

    delContact.deleteContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            Json.createObjectBuilder().build(),
            mockk(), emitMock, logMock(), Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
    }
  }


  "deleteContact should emit exceptions from Alg.deleteContact correctly" {
    val delContact = DeleteContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    val idSlot = slot<String>()
    every { contactMock.deleteContact(capture(idSlot), any()) } answers {
      Id.just(ContactException.ContactNotFound(idSlot.captured).some())
    }
    every { emitMock.emitError(any()) } returns Id.just(Unit)

    val credentials = credGen.rand()
    val credentialsConfig = toJson(credentials)

    delContact.deleteContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            credentialsConfig,
            contactMock, emitMock, logMock(), Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.ContactNotFound("MyId"))
      contactMock.deleteContact("MyId", credentials)
    }
  }

  "deleteContact should emit correct values from Alg.deleteContact" {
    val delContact = DeleteContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.deleteContact(any(), any()) } answers {
      Id.just(none())
    }
    every { emitMock.emitMessage(any()) } returns Id.just(Unit)

    val credentials = credGen.rand()
    val credentialsConfig = toJson(credentials)

    delContact.deleteContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            credentialsConfig,
            contactMock, emitMock, logMock(), Id.monad()
    ).value()

    verify {
      emitMock.emitMessage(Json.createObjectBuilder().build())
      contactMock.deleteContact("MyId", credentials)
    }
  }
})