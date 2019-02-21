package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.data.Nel
import arrow.typeclasses.Monad
import arrow.typeclasses.binding
import com.fileee.oihAdapter.DELETED_KEY
import com.fileee.oihAdapter.PERSONS_API_URL
import com.fileee.oihAdapter.algebra.*
import com.fileee.oihAdapter.createHeaderFromCredentials
import javax.json.JsonObject
import javax.json.JsonValue

class ContactInterpreter<F>(
  val httpAlgebra: HttpAlgebra<F>,
  val parseAlgebra: ParseAlgebra<F>,
  val authAlgebra: AuthAlgebra<F>,
  override val M: Monad<F>
) : ContactAlgebra<F> {

  /**
   * Handle a http response returned from [HttpAlgebra].
   *
   * Does nothing but simple pattern matching to call the correct functions
   *
   * @param request A function given [Credentials] that returns a [HttpResponse] wrapped in [F]
   * @param onException A function that handles the left case from [HttpResponse], defaults to [ContactException.UnhandledException]
   * @param onAuthException A function that handles 403 responses, defaults to [ContactException.AuthException]
   * @param onApiException A function that handles all response codes from 400 to 599, defaults to [ContactException.BadApiResponse]
   * @param onSuccess A function that is executed if none of the above applies
   * @param cred Credentials to pass to [request]
   * @param retryAuth Used internally as a "state" to determine if [request] should be retried on auth errors
   */
  internal fun <A> handleHttpResponse(
    request: (Credentials) -> Kind<F, HttpResponse>,
    onException: (Throwable) -> Kind<F, Either<ContactException, A>> = { M.just(ContactException.UnhandledException(it).left()) },
    onAuthException: (HttpResult) -> Kind<F, Either<ContactException, A>> = { M.just(ContactException.AuthException.left()) },
    onApiException: (HttpResult) -> Kind<F, Either<ContactException, A>> = { M.just(ContactException.BadApiResponse(it.responseStatus, it.responseMessage).left()) },
    onSuccess: (HttpResult) -> Kind<F, Either<ContactException, A>>,
    cred: Credentials,
    retryAuth: Boolean = true
  ): Kind<F, Either<ContactException, A>> =
    M.binding {
      request(cred).bind().fold({
        onException(it).bind()
      }, { resp ->
        when (resp.responseStatus) {
          403 -> if (retryAuth) {
            authAlgebra.refreshCredentials(cred).bind().fold({
              when (it) {
                is RefreshCredentialsException.InvalidRefreshToken -> onAuthException(resp)
                else -> onException(it)
              }
            }, {
              // recursive call with the same parameters but new credentials and no more auth retries
              handleHttpResponse(request, onException, onAuthException, onApiException, onSuccess, it, false)
            }).bind()
          } else {
            // first auth retry failed, credentials must be wrong
            onAuthException(resp).bind()
          }
          in 400..599 -> onApiException(resp).bind()
          else -> onSuccess(resp).bind()
        }
      })
    }

  override fun getContact(id: UUID, credentials: Credentials): Kind<F, GetContactResult> =
    handleHttpResponse(
      request = {
        httpAlgebra.httpGet(
          "$PERSONS_API_URL/$id",
          createHeaderFromCredentials(it)
        )
      },
      onApiException = {
        when (it.responseStatus) {
          404 -> M.just(ContactException.ContactNotFound(id).left())
          else -> M.just(ContactException.BadApiResponse(it.responseStatus, it.responseMessage).left())
        }
      },
      onSuccess = {
        M.binding {
          parseAlgebra.parseJsonObject(it.responseMessage).bind()
            .mapLeft { ContactException.UnhandledException(it) }
        }
      },
      cred = credentials
    )

  internal fun getContactListAll(modifiedAfter: TimeStamp, credentials: Credentials): Kind<F, GetContactListResult> =
    handleHttpResponse(
      request = {
        httpAlgebra.httpGet(
          "$PERSONS_API_URL?modifiedAfter=$modifiedAfter",
          createHeaderFromCredentials(it)
        )
      },
      onSuccess = {
        M.binding {
          parseAlgebra.parseJsonArray(it.responseMessage).bind()
            .mapLeft { ContactException.UnhandledException(it) }
            .map {
              val objList = it.toList().filter { it is JsonObject } as List<JsonObject>
              Nel.fromList(objList)
            }
        }
      },
      cred = credentials
    )

  override fun getContactList(modifiedAfter: TimeStamp, credentials: Credentials): Kind<F, GetContactListResult> =
    M.run {
      getContactListAll(modifiedAfter, credentials).map {
        it.map {
          it.flatMap { contacts ->
            Nel.fromList(contacts.all.filter {
              val deletedJson = it[DELETED_KEY]
              deletedJson == JsonValue.FALSE
            })
          }
        }
      }
    }

  override fun getDeletedContactList(modifiedAfter: TimeStamp, credentials: Credentials): Kind<F, GetContactListResult> =
    M.run {
      getContactListAll(modifiedAfter, credentials).map {
        it.map {
          it.flatMap { contacts ->
            Nel.fromList(contacts.all.filter {
              val deletedJson = it[DELETED_KEY]
              deletedJson == JsonValue.TRUE
            })
          }
        }
      }
    }

  override fun createContact(contact: Contact, credentials: Credentials): Kind<F, CreateContactResult> =
    handleHttpResponse(
      request = {
        httpAlgebra.httpPost(
          PERSONS_API_URL,
          createHeaderFromCredentials(it),
          contact.toString()
        )
      },
      onSuccess = {
        M.binding {
          parseAlgebra.parseJsonObject(it.responseMessage).bind()
            .mapLeft { ContactException.UnhandledException(it) }
        }
      },
      cred = credentials
    )

  override fun updateContact(id: UUID, contact: Contact, credentials: Credentials): Kind<F, UpdateContactResult> =
    handleHttpResponse(
      request = {
        httpAlgebra.httpPut(
          "$PERSONS_API_URL/$id",
          createHeaderFromCredentials(it),
          contact.toString()
        )
      },
      onApiException = {
        when (it.responseStatus) {
          404 -> M.just(ContactException.ContactNotFound(id).left())
          else -> M.just(ContactException.BadApiResponse(it.responseStatus, it.responseMessage).left())
        }
      },
      onSuccess = {
        M.binding {
          parseAlgebra.parseJsonObject(it.responseMessage).bind()
            .mapLeft { ContactException.UnhandledException(it) }
        }
      },
      cred = credentials
    )

  override fun deleteContact(id: UUID, credentials: Credentials): Kind<F, DeleteContactResult> =
    M.binding {
      handleHttpResponse(
        request = {
          httpAlgebra.httpDelete(
            "$PERSONS_API_URL/$id",
            createHeaderFromCredentials(it)
          )
        },
        onApiException = {
          when (it.responseStatus) {
            404 -> M.just(ContactException.ContactNotFound(id).left())
            else -> M.just(ContactException.BadApiResponse(it.responseStatus, it.responseMessage).left())
          }
        },
        onSuccess = { M.just(Unit.right()) },
        cred = credentials
      ).bind().swap().toOption()
    }
}