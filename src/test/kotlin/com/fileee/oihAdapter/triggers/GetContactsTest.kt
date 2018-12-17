package com.fileee.oihAdapter.triggers

import arrow.core.*
import arrow.data.Nel
import com.fileee.oihAdapter.*
import com.fileee.oihAdapter.algebra.*
import io.kotlintest.assertions.arrow.option.beNone
import io.kotlintest.assertions.arrow.option.beSome
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.json.Json
import javax.json.JsonNumber
import javax.json.JsonObject

class GetContactsSpec : StringSpec({
  "getContacts should fail fast if no credentials" {
    val getContacts = GetContacts()
    val emitMock = mockk<EmitAlgebra<ForId>>()

    every { emitMock.emitError(any()) } returns Id.just(Unit)

    getContacts.getContactList(
            none(), Json.createObjectBuilder().build(),
            mockk(), emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
    }
  }
  "getContacts should emit errors from getContactList" {
    val getContacts = GetContacts()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactMock = mockk<ContactAlgebra<ForId>>()

    every { contactMock.getContactList(any(), any()) } answers {
      Id.just(ContactException.AuthException.left())
    }
    every { emitMock.emitError(any()) } returns Id.just(Unit)

    getContacts.getContactList(
            none(), credentialsConfig,
            contactMock, emitMock, logMock(),
            Id.monad()
    ).value()

    verify {
      emitMock.emitError(ContactException.AuthException)
      contactMock.getContactList(0L, parseCredentials(credentialsConfig).get())
    }
  }
  "getContacts should emit a empty contact list and a new snapshot on success" {
    val getContacts = GetContacts()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactAlg = mockk<ContactAlgebra<ForId>>()

    every { contactAlg.getContactList(any(), any()) } answers {
      Id.just(none<Nel<Contact>>().right())
    }
    every { emitMock.emitSnapshot(any()) } returns Id.just(Unit)

    getContacts.getContactList(
            none(), credentialsConfig,
            contactAlg, emitMock, logMock(),
            Id.monad()
    ).value()

    verify(exactly = 1) {
      emitMock.emitSnapshot(any())
      contactAlg.getContactList(0L, parseCredentials(credentialsConfig).get())
    }
  }
  "getContacts should emit a non-empty contact list and a new snapshot on success" {
    val getContacts = GetContacts()
    val emitMock = mockk<EmitAlgebra<ForId>>()
    val contactAlg = mockk<ContactAlgebra<ForId>>()

    every { contactAlg.getContactList(any(), any()) } answers {
      Id.just(Nel(Json.createObjectBuilder().build()).some().right())
    }
    every { emitMock.emitMessage(any()) } returns Id.just(Unit)
    every { emitMock.emitSnapshot(any()) } returns Id.just(Unit)

    getContacts.getContactList(
            none(), credentialsConfig,
            contactAlg, emitMock, logMock(),
            Id.monad()
    ).value()

    verify(exactly = 1) {
      emitMock.emitMessage(
        Json.createObjectBuilder().build()
      )
      emitMock.emitSnapshot(any())
      contactAlg.getContactList(0L, parseCredentials(credentialsConfig).get())
    }
  }
})

class ParseLastModifiedSpec : StringSpec({
  "parseLastModified should fail for empty json object" {
    val result = GetContacts().parseLastModified(Json.createObjectBuilder().build())

    result shouldBe beNone()
  }
  "parseLastModified should fail for a json object with wrong values" {
    val result = GetContacts().parseLastModified(
            Json.createObjectBuilder()
                    .add(CONTACT_TYPE, Json.createObjectBuilder()
                            .add(MODIFIED_AFTER_KEY, "StringValue")
                            .build())
                    .build()
    )

    result shouldBe beNone()
  }
  "parseLastModified should work for correct json" {
    val result = GetContacts().parseLastModified(
            Json.createObjectBuilder()
                    .add(CONTACT_TYPE, Json.createObjectBuilder()
                            .add(MODIFIED_AFTER_KEY, 10)
                            .build())
                    .build()
    )

    result shouldBe beSome(10L)
  }
})

class CreateNewSnapFromOldSpec : StringSpec({
  "createNewSnapFromOld should create a new object containing correct values" {
    val result = GetContacts().createNewSnapFromOld(none())

    (result[CONTACT_TYPE] is JsonObject) shouldBe true
    ((result[CONTACT_TYPE] as JsonObject)[MODIFIED_AFTER_KEY] is JsonNumber) shouldBe true
  }
})