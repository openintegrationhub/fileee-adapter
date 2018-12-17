package com.fileee.oihAdapter.algebra

import arrow.Kind
import arrow.typeclasses.Monad

interface LogAlgebra<F> {
  val M: Monad<F>
  fun debug(msg: String): Kind<F, Unit>
  fun info(msg: String): Kind<F, Unit>
  fun error(msg: String): Kind<F, Unit>
}