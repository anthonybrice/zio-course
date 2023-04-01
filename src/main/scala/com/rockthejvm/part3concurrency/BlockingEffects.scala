package com.rockthejvm.part3concurrency

import zio.*
import com.rockthejvm.utils.*

import java.util.concurrent.atomic.AtomicBoolean

object BlockingEffects extends ZIOAppDefault {

  def blockingTask(n: Int): UIO[Unit] =
    ZIO.succeed(s"running blocking task $n").debugThread
      *> ZIO.succeed(Thread.sleep(10000))
      *> blockingTask(n)

  val program = ZIO.foreachPar((1 to 100).toList)(blockingTask)
  // thread startvation

  // blocking thread pool
  val aBlockingZIO = ZIO.attemptBlocking {
    println(s"[${Thread.currentThread().getName}] running a long computation...")
    Thread.sleep(10000)
    42
  }

  // blocking code cannot (usually) be interrupted
  val tryInterrupting =
    for
      blockingFib <- aBlockingZIO.fork
      _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting...").debugThread *> blockingFib.interrupt
      mol <- blockingFib.join
    yield mol

  // can use blockingInterrupt
  // Thread.interrupt -> InterruptedException
  val aBlockingInterruptibleZIO = ZIO.attemptBlockingInterrupt {
    println(s"[${Thread.currentThread().getName}] running a long computation...")
    Thread.sleep(10000)
    42
  }

  // set a flag/switch
  def interruptibleBlockingEffect(cancelledFlag: AtomicBoolean): Task[Unit] =
    ZIO.attemptBlockingCancelable {
      (1 to 100_000).foreach { element =>
        if !cancelledFlag.get then {
          println(element)
          Thread.sleep(100)
        }
      }
    } (ZIO.succeed(cancelledFlag.set(true))) // cancelling/interrupting effect

  val interruptibleBlockingDemo =
    for
      fib <- interruptibleBlockingEffect(new AtomicBoolean(false)).fork
      _ <- ZIO.sleep(2.seconds) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
      _ <- fib.join
    yield ()

  // SEMANTIC blocking - no blocking of threads, descheduling the effect/fiber
  val sleepingThread = ZIO.succeed(Thread.sleep(1000)) // blocking, uninterruptible
  val sleeping = ZIO.sleep(1.second) // SEMANTICALLY blocking, interruptible
  // yield
  val chainedZIO = (1 to 10000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> _.debugThread)
  val yieldingDemo = (1 to 10000).map(i => ZIO.succeed(i)).reduce(_.debugThread *> ZIO.yieldNow *> _.debugThread)

  def run = yieldingDemo
}
