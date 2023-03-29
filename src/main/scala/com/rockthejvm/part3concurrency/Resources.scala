package com.rockthejvm.part3concurrency

import zio.{ZIO, *}
import com.rockthejvm.utils.*

import java.io.File
import java.util.Scanner

object Resources extends ZIOAppDefault:

  def unsafeMethod(): Int = throw new RuntimeException("Not an int here for you!")
  val anAttempt = ZIO.attempt(unsafeMethod())

  // finalizers
  val attemptWithFinalizers = anAttempt.ensuring(ZIO.succeed("finalizer").debugThread)
  // multiple finalizers
  val attemptWith2Finalizers = attemptWithFinalizers.ensuring(ZIO.succeed("another finalizer!").debugThread)
  // .onInterrupt, .onError, .onDone, .onExit

  // resource lifecycle
  class Connection(url: String):
    def open = ZIO.succeed(s"opening connection to $url").debugThread
    def close = ZIO.succeed(s"closing connection to $url").debugThread

  object Connection:
    def create(url: String) = ZIO.succeed(new Connection(url))

  val fetchUrl =
    for
      conn <- Connection.create("rockthejvm.com")
      fib <- (conn.open *> ZIO.sleep(300.seconds)).fork
      _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
      _ <- fib.join
    yield () // resource leak

  val correctFetchUrl =
    for
      conn <- Connection.create("rockthejvm.com")
      fib <- (conn.open *> ZIO.sleep(300.seconds)).ensuring(conn.close).fork
      _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
      _ <- fib.join
    yield () // preventing leaks

  // tedious

  // acquireRelease
  val cleanConnection = ZIO.acquireRelease(Connection.create("rockthejvm.com"))(_.close)
  val fetchWithResource = for
    conn <- cleanConnection
    fib <- (conn.open *> ZIO.sleep(300.seconds)).fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  yield ()

  val fetchWithScopedResource = ZIO.scoped(fetchWithResource)

  // acquireReleaseWith
  val cleanConnection_v2 = ZIO.acquireReleaseWith(
    Connection.create("rockthejvm.com") // acquire
  )(
    _.close // release
  )(
    conn => conn.open *> ZIO.sleep(300.seconds) // use
  )

  val fetchWithResource_v2 = for
    fib <- cleanConnection_v2.fork
    _ <- ZIO.sleep(1.second) *> ZIO.succeed("interrupting").debugThread *> fib.interrupt
    _ <- fib.join
  yield ()

  /**
   * Exercises
   * 1. Use the acquireRelease to open a file, print all lines, (one every 100 millis), then close the file
   * hint - Scanner::hasNext and Scanner::nextLine
   */
  def openFileScanner(path: String): UIO[Scanner] =
    ZIO.succeed(new Scanner(new File(path)))

  def acquireOpenFile(path: String): UIO[Unit] = ZIO.scoped(
    for
      scanner <- ZIO.acquireRelease(openFileScanner(path))(sc => ZIO.succeed(sc.close()))
      print = ZIO.succeed(println(scanner.nextLine)) *> ZIO.sleep(100.millis)
      _ <- print.repeatWhile(_ => scanner.hasNextLine)
    yield ())

  val testInterruptFileDisplay = for
    fib <- acquireOpenFile("src/main/scala/com/rockthejvm/part3concurrency/Resources.scala").fork
    _ <- ZIO.sleep(2.seconds) *> fib.interrupt
  yield ()

  // acquireRelease vs acquireReleaseWith
  def connFromConfig(path: String): UIO[Unit] =
    ZIO.acquireReleaseWith(openFileScanner(path))(sc => ZIO.succeed("closing file").debugThread *> ZIO.succeed(sc.close())) { scanner =>
      ZIO.acquireReleaseWith(Connection.create(scanner.nextLine()))(_.close) { conn =>
        conn.open *> ZIO.never
      }
    }

  // nested resource
  def connFromConfig_v2(path: String): UIO[Unit] = ZIO.scoped(
    for
      scanner <- ZIO.acquireRelease(openFileScanner(path))(sc => ZIO.succeed("closing file").debugThread *> ZIO.succeed(sc.close()))
      conn <- ZIO.acquireRelease(Connection.create(scanner.nextLine()))(_.close)
      _ <- conn.open *> ZIO.never
    yield ())

  def run = connFromConfig_v2("src/main/resources/connection.conf")
