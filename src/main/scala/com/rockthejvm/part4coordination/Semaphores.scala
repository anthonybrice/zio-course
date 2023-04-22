package com.rockthejvm.part4coordination

import zio._
import com.rockthejvm.utils._

object Semaphores extends ZIOAppDefault:

  // n permits
  // acquire, acquireN - can potentially (semantically)
  // release, releaseN
  val aSemaphore = Semaphore.make(10)

  // example: limiting the number of concurrent sessions on a server
  def doWorkWhileLoggedIn(): UIO[Int] =
    ZIO.sleep(1.second) *> Random.nextIntBounded(100)

  def login(id: Int, sem: Semaphore): UIO[Int] =
    ZIO.succeed(s"[task $id] waiting to log in").debugThread *>
      sem.withPermit { // acquire + zio + release
        for {
          // critical section start
          _ <- ZIO.succeed(s"[task $id] logged in, working...").debugThread
          res <- doWorkWhileLoggedIn()
          _ <- ZIO.succeed(s"[task $id] done: $res").debugThread
        } yield res
      }

  def demoSemaphore() = for {
    sem <- Semaphore.make(2) // Semaphore.make(1) == a Mutex
    f1 <- login(1, sem).fork
    f2 <- login(2, sem).fork
    f3 <- login(3, sem).fork
    _ <- f1.join
    _ <- f2.join
    _ <- f3.join
  } yield ()

  def loginWeighted(n: Int, sem: Semaphore): UIO[Int] =
    ZIO.succeed(s"[task $n] waiting to log in with $n permits").debugThread *>
      sem.withPermits(n) { // acquire + zio + release
        for {
          // critical section starts when you acquired ALL n permits
          _ <- ZIO.succeed(s"[task $n] logged in, working...").debugThread
          res <- doWorkWhileLoggedIn()
          _ <- ZIO.succeed(s"[task $n] done: $res").debugThread
        } yield res
      }

  def demoSemaphoreWeighed() = for {
    sem <- Semaphore.make(2)
    f1 <- loginWeighted(1, sem).fork // requires 1 permit
    f2 <- loginWeighted(2, sem).fork // requires 2 permits
    f3 <- loginWeighted(3, sem).fork // requires 3 permits - will block indefinitely
    _ <- f1.join
    _ <- f2.join
    _ <- f3.join
  } yield ()

  /**
   * Exercise
   * 1. what is the code SUPPOSED to do?
   *      - should only allow one logged in user at a time
   * 2. find if there's anything wrong
   *      - runs all tasks in parallel, effectively ignoring the mutex
   * 3. fix the problem
   */
  val mySemaphore = Semaphore.make(1) // a mutex
  def tasks = ZIO.collectAllPar((1 to 10).map { id =>
    for
      sem <- mySemaphore
      _ <- ZIO.succeed(s"[task $id] waiting to log in").debugThread
      res <-  sem.withPermit { // acquire + zio + release
        for
          // critical section start
          _ <- ZIO.succeed(s"[task $id] logged in, working...").debugThread
          res <- doWorkWhileLoggedIn()
          _ <- ZIO.succeed(s"[task $id] done: $res").debugThread
        yield res
      }
    yield res
  })

  def tasks_v2 = mySemaphore.flatMap { sem => // only one instance
    ZIO.collectAllPar((1 to 10).map { id =>
      for
        _ <- ZIO.succeed(s"[task $id] waiting to log in").debugThread
        res <- sem.withPermit { // acquire + zio + release
          for
          // critical section start
            _ <- ZIO.succeed(s"[task $id] logged in, working...").debugThread
            res <- doWorkWhileLoggedIn()
            _ <- ZIO.succeed(s"[task $id] done: $res").debugThread
          yield res
        }
      yield res
    })
  }

  def run = tasks_v2.debugThread
