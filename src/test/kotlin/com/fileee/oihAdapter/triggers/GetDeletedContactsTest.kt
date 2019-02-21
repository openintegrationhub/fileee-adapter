package com.fileee.oihAdapter.triggers

import arrow.core.*
import com.fileee.oihAdapter.*
import com.fileee.oihAdapter.algebra.ContactAlgebra
import com.fileee.oihAdapter.algebra.ContactException
import com.fileee.oihAdapter.algebra.EmitAlgebra
import com.fileee.oihAdapter.algebra.GetContactListResult
import com.fileee.oihAdapter.generators.*
import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.lang.IllegalStateException
import javax.json.JsonObject
import javax.json.JsonValue

class GetDeletedContactsTest : StringSpec({
  "getDeletedContactsTest" {
    forAll(
      lastModifiedGen(DELETED_CONTACT_TYPE).option(),
      credJsonGen,
      Gen.oneOf<GetContactListResult>(
        jsonObjectGen.nel().option().map { it.right() },
        Gen.from(listOf(
          ContactException.BadApiResponse(Gen.int().rand(), Gen.string().rand()),
          ContactException.UnhandledException(Throwable())
        )).map { it.left() }
      )
    ) { snapshot, configuration, result ->
      val instance = GetDeletedContacts()
      val emitMock = mockk<EmitAlgebra<ForId>>()

      every { emitMock.emitError(any()) } returns Id.just(Unit)
      every { emitMock.emitMessage(any()) } returns Id.just(Unit)
      every { emitMock.emitSnapshot(any()) } returns Id.just(Unit)

      configuration.fold({
        // no auth details
        instance.getContactList(
          snapshot, it,
          mockk(), emitMock, logMock(),
          Id.monad()
        ).value()

        verify {
          emitMock.emitError(ContactException.AuthException)
        }
        true
      }, { json ->
        // auth details exist
        val contactMock = mockk<ContactAlgebra<ForId>>()

        every { contactMock.getDeletedContactList(any(), any()) } returns Id.just(result)

        instance.getContactList(
          snapshot, json,
          contactMock, emitMock, logMock(),
          Id.monad()
        ).value()

        verify {
          contactMock.getDeletedContactList(
            // test if timestamp is parsed correctly
            snapshot.flatMap { parseLastModified(DELETED_CONTACT_TYPE, it) }
              .getOrElse { 0 },
            // this should never throw as long as parseCredentials works
            parseCredentials(json).getOrElse { throw IllegalStateException() }
          )
        }

        result.fold({ exc ->
          // failure
          verify {
            emitMock.emitError(exc)
          }
          true
        }, { option ->
          // success, check if everything is emitted
          verify {
            emitMock.emitSnapshot(match {
              it[DELETED_CONTACT_TYPE]?.let { (it as JsonObject)[MODIFIED_AFTER_KEY]?.valueType == JsonValue.ValueType.NUMBER } ?: false
            })
          }
          option.fold({ true }, { list ->
            verify(exactly = list.size) { emitMock.emitMessage(any()) }
            list.foldLeft(true) { acc, v ->
              verify {
                emitMock.emitMessage(v)
              }
              acc
            }
          })
        })
      })
    }
  }
})