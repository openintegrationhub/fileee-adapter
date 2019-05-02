package com.fileee.oihAdapter.algebra

import arrow.Kind
import arrow.typeclasses.Monad
import javax.json.JsonObject

// if I ever want a more descriptive return type type aliases make it easy to change that everywhere
typealias EmitErrorResult = Unit
typealias EmitMessageResult = Unit
typealias EmitSnapshotResult = Unit
typealias EmitKeysResult = Unit

/**
 * Algebra for dealing with elasticio's EventEmitter.
 */
interface EmitAlgebra<F> {
  val M: Monad<F>
  fun emitError(err: Throwable): Kind<F, EmitErrorResult>
  fun emitMessage(msg: JsonObject): Kind<F, EmitMessageResult>
  fun emitSnapshot(snapshot: JsonObject): Kind<F, EmitSnapshotResult>
  fun emitKeys(keys: JsonObject): Kind<F, EmitKeysResult>
}