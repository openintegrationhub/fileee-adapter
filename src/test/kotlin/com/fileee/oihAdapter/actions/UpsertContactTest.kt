package com.fileee.oihAdapter.actions

import arrow.core.*
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.credentialsConfig
import com.fileee.oihAdapter.logMock
import com.fileee.oihAdapter.parseCredentials
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.json.Json

class UpsertContactSpec : StringSpec({
  "upsertContact should fail fast if no authAlgebra config" {
    val upsertContact = UpsertContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()

    every { emitMock.emitError(any()) } returns Id.just(Unit)

    upsertContact.upsertContact(
            Json.createObjectBuilder().build(),
            Json.createObjectBuilder().build(),
            mockk(), emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
    }
  }
  "upsertContact should call and emit the result of createContact if no id is present" {
    val upsertContact = UpsertContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.createContact(any(), any()) } answers {
      Id.just(Json.createObjectBuilder().build().right())
    }
    every { emitMock.emitMessage(any()) } returns Id.just(Unit)

    upsertContact.upsertContact(
            Json.createObjectBuilder().build(),
            credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitMessage(Json.createObjectBuilder().build())
      contactMock.createContact(Json.createObjectBuilder().build(), parseCredentials(credentialsConfig).get())
    }
  }
  "upsertContact should call and emit the result of updateContact if an id was found" {
    val upsertContact = UpsertContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.updateContact(any(), any(), any()) } answers {
      Id.just(Json.createObjectBuilder().build().right())
    }
    every { emitMock.emitMessage(any()) } returns Id.just(Unit)

    upsertContact.upsertContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitMessage(Json.createObjectBuilder().build())
      contactMock.updateContact("MyId", Json.createObjectBuilder().add("id", "MyId").build(), parseCredentials(credentialsConfig).get())
    }
  }
  "upsertContact should emit errors from createContact" {
    val upsertContact = UpsertContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.createContact(any(), any()) } answers {
      Id.just(ContactException.AuthException.left())
    }
    every { emitMock.emitError(any()) } returns Id.just(Unit)

    upsertContact.upsertContact(
            Json.createObjectBuilder().build(),
            credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
      contactMock.createContact(Json.createObjectBuilder().build(), parseCredentials(credentialsConfig).get())
    }
  }
  "upsertContact should emit errors from updateContact" {
    val upsertContact = UpsertContact()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.updateContact(any(), any(), any()) } answers {
      Id.just(ContactException.ContactNotFound("MyId").left())
    }
    every { emitMock.emitError(any()) } returns Id.just(Unit)

    upsertContact.upsertContact(
            Json.createObjectBuilder().add("id", "MyId").build(),
            credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.ContactNotFound("MyId"))
      contactMock.updateContact("MyId", Json.createObjectBuilder().add("id", "MyId").build(), parseCredentials(credentialsConfig).get())
    }
  }
})