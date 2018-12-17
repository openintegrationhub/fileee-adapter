package com.fileee.oihAdapter

import arrow.Kind
import arrow.core.right
import arrow.effects.typeclasses.Async
import arrow.effects.typeclasses.Duration
import arrow.effects.typeclasses.milliseconds
import arrow.typeclasses.Order
import arrow.typeclasses.bindingCatch
import java.util.*
import kotlin.concurrent.schedule

// I should probably put this one in some library rather than having this just here
//  its way overkill for the task at hand, but it does have it's uses
/**
 * A (partial) port of the awesome Schedule class from scala zio
 * TODO: Remove and replace with better port in form of a library
 *
 * This below is not how scalaz zio's schedule works, just an attempt at replicating its behaviour.
 *   And while it does work, it's not as composable as the one from zio.
 */
sealed class Schedule(
  val condition: (Int) -> Boolean = { true }
) {
  abstract fun <F, V> runS(A: Async<F>, f: () -> Kind<F, V>): Kind<F, V>

  class Retry(
    condition: (Int) -> Boolean = { true }
  ) : Schedule(condition) {
    override fun <F, V> runS(A: Async<F>, f: () -> Kind<F, V>): Kind<F, V> = A.bindingCatch {
      f().attempt().bind().fold({
        _runS(A, f, 1, it).bind()
      }, { it })
    }

    private fun <F, V> _runS(
      A: Async<F>,
      f: () -> Kind<F, V>,
      tries: Int,
      lastThrown: Throwable
    ): Kind<F, V> = A.bindingCatch {
      if (condition(tries)) {
        f().attempt().bind().fold({
          _runS(A, f, tries + 1, it).bind()
        }, { it })
      } else {
        A.raiseError<V>(lastThrown).bind()
      }
    }

    fun delay(dur: Duration): Delayed =
      Delayed(
        condition,
        { true },
        delay = { dur }
      )
  }

  class Delayed(
    condition: (Int) -> Boolean = { true },
    val delayCond: Order<Duration>.(Duration) -> Boolean = { true },
    val randStart: Double = 1.0,
    val randEnd: Double = 1.0,
    val delay: (Int) -> Duration
  ) : Schedule(condition) {

    private val rand = Random()

    override fun <F, V> runS(
      A: Async<F>,
      f: () -> Kind<F, V>
    ): Kind<F, V> = A.bindingCatch {
      f().attempt().bind().fold({
        _runS(A, f, 1, it).bind()
      }, { it })
    }

    private fun <F, V> _runS(
      A: Async<F>,
      f: () -> Kind<F, V>,
      tries: Int,
      lastThrown: Throwable
    ): Kind<F, V> = A.bindingCatch {
      // apply some random noise to the delay (usually 0-1 times delay)
      val r = randStart + (randEnd - randStart) * rand.nextDouble()
      val d = (delay(tries).amount * r).toInt().milliseconds

      if (condition(tries) && DurationOrd.delayCond(d)) {
        invokeLater(
          d.amount, A, f
        ).attempt().bind().fold({
          _runS(A, f, tries + 1, it).bind()
        }, { it })
      } else {
        A.raiseError<V>(lastThrown).bind()
      }
    }

    private fun <F, V> invokeLater(millis: Long, A: Async<F>, f: () -> Kind<F, V>): Kind<F, V> = A.bindingCatch {
      A.async<Kind<F, V>> { outerCb ->
        Timer().schedule(millis) {
          outerCb(f().right())
        }
      }.flatMap { it }.bind()
    }

    fun whileValue(c: Order<Duration>.(Duration) -> Boolean): Delayed =
      Delayed(
        condition,
        { delayCond(it) && c(it) },
        delay = delay
      )

    fun randomized(start: Double = .0, end: Double = 1.0) = Delayed(
      condition,
      delayCond,
      start,
      end,
      delay
    )

    operator fun times(amount: Int): Delayed =
      Delayed(
        { condition(it) && it < amount },
        delayCond,
        delay = delay
      )

    fun delay(dur: Duration): Delayed =
      Delayed(
        condition,
        delayCond,
        randStart,
        randEnd,
        { delay(it) + dur }
      )
  }

  class ContinueOther(
    val first: Schedule,
    val second: Schedule
  ) : Schedule({ true }) {
    override fun <F, V> runS(A: Async<F>, f: () -> Kind<F, V>): Kind<F, V> = A.bindingCatch {
      first.runS(A, f).attempt().bind().fold({
        second.runS(A, f).bind()
      }, { it })
    }
  }

  infix fun andThen(schedule: Schedule): Schedule = ContinueOther(this, schedule)

  companion object {
    fun exponential(i: Duration, factor: Double = 2.0): Delayed =
      Delayed { (Math.pow(factor, it * 1.0) * i.amount).toLong().milliseconds }

    fun linear(i: Duration): Delayed =
      Delayed { i * it }

    fun spaced(i: Duration): Delayed =
      Delayed { i }

    fun recurrent(i: Int): Retry =
      Retry { it < i }
  }
}

object DurationOrd : Order<Duration> {
  override fun Duration.compare(b: Duration): Int =
    if (this.nanoseconds > b.nanoseconds) 1
    else if (this.nanoseconds < b.nanoseconds) -1
    else 0
}


