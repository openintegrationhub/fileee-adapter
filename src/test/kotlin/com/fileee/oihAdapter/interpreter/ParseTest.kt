package com.fileee.oihAdapter.interpreter

import arrow.core.Either
import arrow.core.Try
import arrow.core.left
import arrow.core.right
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monadDefer
import com.fileee.oihAdapter.generators.jsonArrayGen
import com.fileee.oihAdapter.generators.jsonObjectGen
import io.kotlintest.properties.Gen
import io.kotlintest.properties.forAll
import io.kotlintest.specs.StringSpec
import javax.json.Json

class ParseSpec : StringSpec({
  "parseJsonObject should work as expected" {
    forAll(Gen.oneOf<Either<String, String>>(
      jsonObjectGen.map { it.toString() }.map { it.right() },
      Gen.string()
        .filter { Try { Json.createReader(it.reader()).readObject() }.isFailure() }
        .map { it.left() }
    )) { obj ->
      val parseAlg = ParseInterpreter(IO.monadDefer())

      obj.fold({ str ->
        parseAlg.parseJsonObject(str).fix().unsafeRunSync().fold({ true }, { false })
      }, { str ->
        parseAlg.parseJsonObject(str).fix().unsafeRunSync().fold({ false }, { it == Json.createReader(str.reader()).readObject() })
      })
    }
  }

  "parseJsonArray should work as expected" {
    forAll(Gen.oneOf<Either<String, String>>(
      jsonArrayGen.map { it.toString() }.map { it.right() },
      Gen.string()
        .filter { Try { Json.createReader(it.reader()).readArray() }.isFailure() }
        .map { it.left() }
    )) { obj ->
      val parseAlg = ParseInterpreter(IO.monadDefer())

      obj.fold({ str ->
        parseAlg.parseJsonArray(str).fix().unsafeRunSync().fold({ true }, { false })
      }, { str ->
        parseAlg.parseJsonArray(str).fix().unsafeRunSync().fold({ false }, { it == Json.createReader(str.reader()).readArray() })
      })
    }
  }
})