package com.fileee.oihAdapter

import arrow.core.Either
import arrow.core.none
import com.fileee.oihAdapter.generators.*
import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import javax.json.JsonNumber
import javax.json.JsonObject
import javax.json.JsonString

class ParseCredentialsSpec : StringSpec({
  "parseCredentials should work for correct json" {
    forAll(credJsonGen) { s : Either<JsonObject, JsonObject> ->
      s.fold({
        parseCredentials(it).isEmpty()
      }, {
        parseCredentials(it).isDefined()
      })
    }
  }
})

class CreateHeaderFromCredentialsSpec : StringSpec({
  "createHeaderFromCredentials should create correct header" {
    forAll(credGen) { c ->
      val headers = createHeaderFromCredentials(c)
      headers["Authorization"] == "Bearer ${c.accessToken}"
    }
  }
})

class GetIdSpec : StringSpec({
  "getId" {
    forAll(jsonIdObjGen) { obj ->
      obj.fold({ json ->
        getId(json).fold({ true }, { false })
      }, { json ->
        getId(json).fold({ false }, { it == (json["id"] as JsonString).string })
      })
    }
  }
})

class ParseLastModifiedSpec : StringSpec({
  "parseLastModified should work" {
    forAll(jsonLastModifiedGen(CONTACT_TYPE)) { obj ->
      obj.fold({
        parseLastModified(CONTACT_TYPE, it).fold({ true }, { false })
      }, { json ->
        parseLastModified(CONTACT_TYPE, json).fold({ false }, { time ->
          json[CONTACT_TYPE]?.let {
            time == ((it as JsonObject)[MODIFIED_AFTER_KEY] as JsonNumber).longValueExact()
          } ?: false
        })
      })
    }
  }
})

class CreateNewSnapFromOldSpec : StringSpec({
  "createNewSnapFromOld should create a new object containing correct values" {
    forAll(Gen.oneOf(
      lastModifiedGen(CONTACT_TYPE),
      jsonObjectGen
    ).option()) { snap ->
      val result = createNewSnapFromOld(CONTACT_TYPE, snap)
      (result[CONTACT_TYPE] is JsonObject) &&
        ((result[CONTACT_TYPE] as JsonObject)[MODIFIED_AFTER_KEY] is JsonNumber) &&
        snap.fold({ true }, { json ->
          json.entries.filter { (k,_) -> k != CONTACT_TYPE }.fold(true) { b, (k, v) -> b && result[k] == v }
        })
    }
  }
})