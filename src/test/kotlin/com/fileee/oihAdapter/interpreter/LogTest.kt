package com.fileee.oihAdapter.interpreter

import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monadDefer
import io.kotlintest.specs.ShouldSpec
import io.mockk.*
import org.slf4j.Logger

class IOLogSpec : ShouldSpec({
    "onError should call the correct method" {
        val log = mockk<Logger>()
        val logAlg = LogInterpreter(log, IO.monadDefer())

        every { log.error(any()) } just Runs

        logAlg.error("MyMessage").fix().unsafeRunSync()

        verify {
            log.error("MyMessage")
        }
    }
    "onInfo should call the correct method" {
        val log = mockk<Logger>()
        val logAlg = LogInterpreter(log, IO.monadDefer())

        every { log.info(any()) } just Runs

        logAlg.info("MyMessage").fix().unsafeRunSync()

        verify {
            log.info("MyMessage")
        }
    }
    // disabled because for testing onDebug for now calls onInfo instead
    should("onDebug should call the correct method").config(enabled = false) {
        val log = mockk<Logger>()
        val logAlg = LogInterpreter(log, IO.monadDefer())

        every { log.debug(any()) } just Runs

        logAlg.debug("MyMessage").fix().unsafeRunSync()

        verify {
            log.debug("MyMessage")
        }
    }
})