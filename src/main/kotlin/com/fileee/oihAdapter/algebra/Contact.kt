package com.fileee.oihAdapter.algebra

import arrow.Kind
import arrow.core.Either
import arrow.core.Option
import arrow.data.Nel
import arrow.typeclasses.Monad
import javax.json.JsonObject

typealias UUID = String
typealias Contact = JsonObject
typealias GetContactResult = Either<ContactException, Contact>

typealias TimeStamp = Long
typealias GetContactListResult = Either<ContactException, Option<Nel<Contact>>>

typealias CreateContactResult = Either<ContactException, Contact>

typealias UpdateContactResult = Either<ContactException, Contact>

typealias DeleteContactResult = Option<ContactException>

sealed class ContactException : Throwable() {
  object MissingOrInvalidId : ContactException()
  class ContactNotFound(val id: UUID) : ContactException()
  object AuthException : ContactException()
  class BadApiResponse(val status: Int, val msg: String): ContactException()
  class UnhandledException(val exception: Throwable): ContactException()

  override fun toString(): String = when (this) {
    is MissingOrInvalidId -> "Missing or invalid value for id"
    is ContactNotFound -> "No contact for id $id found"
    is AuthException -> "Invalid credentials"
    is BadApiResponse -> "Bad response from the api. Status: $status. Message: $msg"
    is UnhandledException -> exception.toString()
  }

  override fun equals(other: Any?): Boolean = when (this) {
    is MissingOrInvalidId -> other is MissingOrInvalidId
    is ContactNotFound -> if (other is ContactNotFound) id == other.id else false
    is AuthException -> other is AuthException
    is BadApiResponse -> if (other is BadApiResponse) status == other.status && msg == other.msg else false
    is UnhandledException -> if (other is UnhandledException) exception == other.exception else false
  }
}

/**
 * Algebra for dealing with contacts in a crud kind of way
 */
interface ContactAlgebra<F> {
  val M: Monad<F>
  fun getContact(id: UUID, credentials: Credentials): Kind<F, GetContactResult>
  fun getContactList(modifiedAfter: TimeStamp, credentials: Credentials): Kind<F, GetContactListResult>
  fun createContact(contact: Contact, credentials: Credentials): Kind<F, CreateContactResult>
  fun updateContact(id: UUID, contact: Contact, credentials: Credentials): Kind<F, UpdateContactResult>
  fun deleteContact(id: UUID, credentials: Credentials): Kind<F, DeleteContactResult>
}