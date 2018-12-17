package com.fileee.oihAdapter.interpreter

import arrow.Kind
import arrow.effects.typeclasses.MonadDefer
import com.fileee.oihAdapter.algebra.LogAlgebra
import org.slf4j.Logger

class LogInterpreter<F>(
  val logger: Logger,
  override val M: MonadDefer<F>
) : LogAlgebra<F> {

  override fun info(msg: String): Kind<F, Unit> =
    M {
      logger.info(msg)
    }

  override fun error(msg: String): Kind<F, Unit> =
    M {
      logger.error(msg)
    }

  override fun debug(msg: String): Kind<F, Unit> =
    M {
      logger.info(msg)
    }
}