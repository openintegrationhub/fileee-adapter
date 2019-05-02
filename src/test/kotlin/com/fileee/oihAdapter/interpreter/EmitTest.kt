package com.fileee.oihAdapter.interpreter

import arrow.core.some
import arrow.effects.IO
import arrow.effects.fix
import arrow.effects.monadDefer
import io.elastic.api.EventEmitter
import io.kotlintest.specs.StringSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import javax.json.Json

class EmitSpec : StringSpec({
    "emitMessage should call the correct method" {
        val em = mockk<EventEmitter>()
        val emAlg = EmitInterpreter(em.some(), IO.monadDefer())

        every { em.emitData(any()) } returns em

        emAlg.emitMessage(Json.createObjectBuilder().build()).fix().unsafeRunSync()

        verify {
            em.emitData(any())
        }
    }
    "emitException should call the correct method" {
        val em = mockk<EventEmitter>()
        val emAlg = EmitInterpreter(em.some(), IO.monadDefer())

        every { em.emitException(any()) } returns em

        emAlg.emitError(Exception("MyExcep")).fix().unsafeRunSync()

        verify {
            em.emitException(any())
        }
    }
    "emitSnapshot should call the correct method" {
        val em = mockk<EventEmitter>()
        val emAlg = EmitInterpreter(em.some(), IO.monadDefer())

        every { em.emitSnapshot(any()) } returns em

        emAlg.emitSnapshot(Json.createObjectBuilder().build()).fix().unsafeRunSync()

        verify {
            em.emitSnapshot(Json.createObjectBuilder().build())
        }
    }
    // test for emitKeys later
})