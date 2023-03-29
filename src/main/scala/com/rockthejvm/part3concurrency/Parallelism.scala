package com.rockthejvm.part3concurrency

import zio.{ZIO, *}
import com.rockthejvm.utils.*

import scala.collection.immutable.Seq

object Parallelism extends ZIOAppDefault {

  val meaningOfLife = ZIO.succeed(42)
  val favLang = ZIO.succeed("Scala")
  val combined = meaningOfLife.zip(favLang) // combines/zips in a sequential way

  // combine in parallel
  val combinedPar = meaningOfLife.zipPar(favLang)// combination is parallel

  /*
    - start each on fibers
    - what if one fails? the other one should be interrupted
    - what if one is interrupted? the entire thing should be interrupted
    - what if the whole thing is interrupted? need to interrupt both effects
   */

  // try a zipPar combinator
  // hint: fork/join/await, interrupt
  def myZipPar[R,E,A,B](zioa: ZIO[R,E,A], ziob: ZIO[R,E,B]): ZIO[R,E,(A,B)] =
    val exits = for
      fiba <- zioa.fork
      fibb <- ziob.fork
      exita <- fiba.await
      exitb <- exita match
        case Exit.Success(_) => fibb.await
        case Exit.Failure(_) => fibb.interrupt
    yield (exita, exitb)

    exits.flatMap {
      case (Exit.Success(a), Exit.Success(b)) => ZIO.succeed((a,b)) // happy path
      case (Exit.Success(_), Exit.Failure(c)) => ZIO.failCause(c) // one of them failed
      case (Exit.Failure(c), Exit.Success(_)) => ZIO.failCause(c) // one of them failed
      case (Exit.Failure(c1), Exit.Failure(c2)) => ZIO.failCause(c1 && c2)
    }

  // parallel combinators
  // zipPar, zipWithPar

  // collectAllPar
  val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to 10).map(i => ZIO.succeed(i).debugThread)
  val collectedValues: ZIO[Any, Nothing, Seq[Int]] = ZIO.collectAllPar(effects) // "traverse"

  // foreachPar
  val printlnParallel = ZIO.foreachPar((1 to 10).toList)(i => ZIO.succeed(println(i)))

  // reduceAllPar, mergeAllPar
  val sumPar = ZIO.reduceAllPar(ZIO.succeed(0), effects)(_ + _)
  val sumPar_v2 = ZIO.mergeAllPar(effects)(0)(_ + _)

  /*
    - if all the effects succeed, all good
    - one effect fails => everyone else is interrupted, error is surfaced
    - one effect is interrupted => everyone else is interrupted, error = interruption (for the big expression)
    - if the entire thing is interrupted => all effects are interrupted
   */

  /**
   * Do the word counting exercise we did earlier with parallel combinators
   * hint - you can use mergeAllPar, collectAllPar, or reduceAllPar
   */
  def countWords(path: String): UIO[Int] =
    ZIO.succeed {
      val source = scala.io.Source.fromFile(path)
      val nWords = source.getLines().mkString(" ").split(" ").count(_.nonEmpty)
      println(s"Counted $nWords in $path")
      source.close
      nWords
    }

  def wordCountPar(n: Int): UIO[Int] =
    val effects: Seq[ZIO[Any, Nothing, Int]] = (1 to n)
      .map(i => s"src/main/resources/testfile_$i.txt")
      .map(path => countWords(path))

    ZIO.mergeAllPar(effects)(0)(_ + _)

  val printCount =
    for
      n <- wordCountPar(10)
      _ = println(n)
    yield ()

  def run = wordCountPar(10).debugThread

}
