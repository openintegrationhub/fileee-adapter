package com.fileee.oihAdapter

import arrow.core.none
import arrow.core.some
import com.fileee.oihAdapter.algebra.Credentials
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table
import javax.json.Json

class ParseCredentialsSpec : StringSpec({
  // FIXME This is dumb, refractor into several tests
  "parseCredentials" {
    table(
            headers("JsonObject", "expected"),
            row(
                    Json.createObjectBuilder()
                            .add(OAUTH_KEY, Json.createObjectBuilder()
                                    .add(ACCESS_TOKEN, "MyToken")
                                    .add(REFRESH_TOKEN, "RefToken")
                                    .build())
                            .build(),
                    Credentials(
                            accessToken = "MyToken",
                            refreshToken = "RefToken"
                    ).some()
            ),
            row(
                    Json.createObjectBuilder().build(),
                    none()
            ),
            row(
                    Json.createObjectBuilder()
                            .add(OAUTH_KEY, Json.createObjectBuilder()
                                    .add(ACCESS_TOKEN, "Token")
                                    .build())
                            .build(),
                    none()
            ),
            row(
                    Json.createObjectBuilder()
                            .add(OAUTH_KEY, Json.createObjectBuilder()
                                    .add(REFRESH_TOKEN, "Token")
                                    .build())
                            .build(),
                    none()
            ),
            row(
                    Json.createObjectBuilder()
                            .add(OAUTH_KEY, Json.createObjectBuilder()
                                    .add(ACCESS_TOKEN, 1)
                                    .build())
                            .build(),
                    none()
            ),
            row(
                    Json.createObjectBuilder()
                            .add(OAUTH_KEY, Json.createObjectBuilder()
                                    .add(REFRESH_TOKEN, false)
                                    .build())
                            .build(),
                    none()
            )
    ).forAll { jsonObject, optionalCred ->
      parseCredentials(jsonObject) shouldBe optionalCred
    }
  }
})

class CreateHeaderFromCredentialsSpec : StringSpec({
  "createHeaderFromCredentials should create correct header" {
    val cred = Credentials(
            accessToken = "MyToken",
            refreshToken = "RefToken"
    )

    val headers = createHeaderFromCredentials(cred)

    headers["Authorization"] shouldBe "Bearer MyToken"
  }
})

class GetIdSpec : StringSpec({
  "getId" {
    table(
            headers("JsonObject", "Expected"),
            row(Json.createObjectBuilder().build(), none()),
            row(
                    Json.createObjectBuilder().add("id", "MyId").build(),
                    "MyId".some()
            ),
            row(
                    Json.createObjectBuilder().add("id", false).build(),
                    none()
            )
    ).forAll { obj, expected ->
      getId(obj) shouldBe expected
    }
  }
})