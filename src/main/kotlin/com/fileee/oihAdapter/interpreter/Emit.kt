package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.core.Option
import arrow.effects.typeclasses.MonadDefer
import com.fileee.oihAdapter.algebra.*
import io.elastic.api.EventEmitter
import io.elastic.api.Message
import javax.json.JsonObject

/**
 * Interpreter for [EmitAlgebra]. This has the constraint [MonadDefer] instead of just [Monad] because it
 *  performs side-effects which [MonadDefer] both defers and declares (with its type)
 */
class EmitInterpreter<F>(
  val eventEmitter: Option<EventEmitter>,
  override val M: MonadDefer<F>
) : EmitAlgebra<F> {

  override fun emitError(err: Throwable): Kind<F, EmitErrorResult> =
    M {
      eventEmitter.map { it.emitException(Exception(err)) }
      Unit
    }

  override fun emitMessage(msg: JsonObject): Kind<F, EmitMessageResult> =
    M {
      eventEmitter.map {
        it.emitData(
          Message.Builder()
            .body(msg)
            .build()
        )
      }
      Unit
    }

  override fun emitSnapshot(snapshot: JsonObject): Kind<F, EmitSnapshotResult> =
    M {
      eventEmitter.map { it.emitSnapshot(snapshot) }
      Unit
    }

  override fun emitKeys(keys: JsonObject): Kind<F, EmitKeysResult> =
    M {
      eventEmitter.map { it.emitUpdateKeys(keys) }
      Unit
    }
}