package com.rockthejvm.part2effects

import zio.*

import scala.io.StdIn

object ZIOEffects {

  // success
  val meaningOfLife: ZIO[Any, Nothing, Int] = ZIO.succeed(42)
  // failure
  val aFailure: ZIO[Any,String,Nothing] = ZIO.fail("Something went wrong")
  // suspension/delay
  val aSuspendedZIO: ZIO[Any,Throwable,Int] = ZIO.suspend(meaningOfLife)

  // map + flatMap
  val improvedMOL = meaningOfLife.map(_ * 2)
  val printingMOL = meaningOfLife.flatMap(mol => ZIO.succeed(println(mol)))
  // for comprehensions
  val smallProgram = for {
    _ <- ZIO.succeed(println("what's your name"))
    name <- ZIO.succeed(StdIn.readLine)
    _ <- ZIO.succeed(println(s"Welcome to ZIO, ${name}"))
  } yield ()

  // A LOT of combinators
  // zip, zipWith
  val anotherMOL = ZIO.succeed(100)
  val tupledZIO = meaningOfLife.zip(anotherMOL)
  val combinedZIO = meaningOfLife.zipWith(anotherMOL)(_ * _)

  /**
   * Type aliases of ZIOs
   */
  // UIO = ZIO[Any,Nothing,A] - no requirements, cannot fail, produces A
  val aUIO: UIO[Int] = ZIO.succeed(99)
  // URIO[R,A] = ZIO[R,Nothing,A] - cannot fail
  val aURIO: URIO[Int,Int] = ZIO.succeed(67)
  // val RIO[R,A] = ZIO[R,Throwable,A] - can fail with throwable
  val anRIO: RIO[Int, Int] = ZIO.succeed(98)
  val aFailedRIO: RIO[Int,Int] = ZIO.fail(new RuntimeException("RIO Failed"))

  // Task[A] = ZIO[Any,Throwable,A] - no requirements, can fail with a Throwable, produces A
  val aSuccessfulTask: Task[Int] = ZIO.succeed(89)
  val aFailedTask: Task[Int] = ZIO.fail(new RuntimeException("Something bad"))

  // IO[E,A] = ZIO[Any,E,A] - no requirements
  val aSuccessfulIO: IO[String, Int] = ZIO.succeed(34)
  val aFailedIO: IO[String, Int] = ZIO.fail("Something bad happened")

  /**
   * Exercises
   */

  // 1 - sequence two ZIOs and take the value of the last one
  def sequenceTakeLast[R,E,A,B](zioa: ZIO[R,E,A], ziob: ZIO[R,E,B]): ZIO[R,E,B] = zioa *> ziob

  // 2 - sequence two ZIOs and take the value of the first one
  def sequenceTakeFirst[R,E,A,B](zioa: ZIO[R,E,A], ziob: ZIO[R,E,B]): ZIO[R,E,A] = zioa <* ziob

  // 3 - run a ZIO forever
  def runForever[R,E,A](zio: ZIO[R,E,A]): ZIO[R,E,A] = zio *> runForever(zio)

  val endlessLoop = runForever {
    ZIO.succeed {
      println("running...")
      Thread.sleep(1000)
    }
  }

  // 4 - convert the value of a ZIO to something else
  def convert[R,E,A,B](zio: ZIO[R,E,A], value: B): ZIO[R,E,B] = zio.as(value)

  // 5 - discard the value of a ZIO to Unit
  def asUnit[R,E,A](zio: ZIO[R,E,A]): ZIO[R,E,Unit] = zio.unit

  // 6 - recursion
  def sum(n: Int): Int =
    if n == 0
    then 0
    else n + sum(n - 1) // will crash at sum(20_000)

  def sumZIO(n: Int): UIO[Int] =
    if n == 0
    then ZIO.succeed(0)
    else for {
      m <- ZIO.succeed(n)
      l <- sumZIO(n - 1)
    } yield m + l

  // 7 - fibonacci
  // hint: use ZIO.suspend or ZIO.suspendSucceed
  def fiboZIO(n: Int): UIO[BigInt] = n match
    case n if n < 0 => ZIO.succeed(-1)
    case 0 => ZIO.succeed(0)
    case 1 => ZIO.succeed(1)
    case 2 => ZIO.succeed(1)
    case _ => for {
      m <- ZIO.suspendSucceed(fiboZIO(n - 1))
      l <- fiboZIO(n - 2)
    } yield m + l

  def main(args: Array[String]): Unit = {
    val runtime = Runtime.default
    given trace: Trace = Trace.empty
    val foo = Unsafe.unsafe { (u: Unsafe) =>
      given up: Unsafe = u
      val r = runtime.unsafe.run(fiboZIO(40))
      println(r)
    }
    foo(null)
  }
}
