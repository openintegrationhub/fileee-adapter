package com.fileee.oihAdapter

import arrow.core.ForId
import arrow.core.Id
import com.fileee.oihAdapter.algebra.LogAlgebra
import io.mockk.every
import io.mockk.mockk
import javax.json.Json

fun logMock(): LogAlgebra<ForId> {
  val logMock = mockk<LogAlgebra<ForId>>()
  every { logMock.error(any()) } returns Id.just(Unit)
  every { logMock.info(any()) } returns Id.just(Unit)
  every { logMock.debug(any()) } returns Id.just(Unit)
  return logMock
}

val credentialsConfig = Json.createObjectBuilder()
        .add(OAUTH_KEY, Json.createObjectBuilder()
                .add(REFRESH_TOKEN, "RefToken")
                .add(ACCESS_TOKEN, "Token")
        ).build()