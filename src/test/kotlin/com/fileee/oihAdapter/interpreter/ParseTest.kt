package com.fileee.oihAdapter.interpreter

import arrow.core.left
import arrow.core.right
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monadDefer
import com.fileee.oihAdapter.algebra.ParseException
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.kotlintest.tables.forAll
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table
import javax.json.Json

class ParseSpec: StringSpec({
    "parseJsonObject should work as expected" {
        table(
                headers("json", "expected"),
                row("{}", Json.createObjectBuilder().build().right()),
                row("[]", ParseException.FailedToParse("[]").left()),
                row("{ \"prop\": 1 }", Json.createObjectBuilder().add("prop", 1).build().right()),
                row("...", ParseException.FailedToParse("...").left())
        ).forAll { input, expected ->
            val parseAlg = ParseInterpreter(IO.monadDefer())

            val result = parseAlg.parseJsonObject(input).fix().unsafeRunSync()
            result shouldBe expected
        }
    }
    "parseJsonArray should work as expected" {
        table(
                headers("json", "expected"),
                row("{}", ParseException.FailedToParse("{}").left()),
                row("[]", Json.createArrayBuilder().build().right()),
                row("[1, 2]", Json.createArrayBuilder().add(1).add(2).build().right()),
                row("...", ParseException.FailedToParse("...").left())
        ).forAll { input, expected ->
            val parseAlg = ParseInterpreter(IO.monadDefer())

            val result = parseAlg.parseJsonArray(input).fix().unsafeRunSync()
            result shouldBe expected
        }
    }
})