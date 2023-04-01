package com.rockthejvm.part3concurrency

import zio.*
import com.rockthejvm.utils.*

import java.util.concurrent.{ExecutorService, Executors}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object AsynchronousEffects extends ZIOAppDefault:

  // CALLBACK-based
  // asynchronous
  object LoginService:
    case class AuthError(message: String)
    case class UserProfile(email: String, name: String)

    // thread pool
    val executor = Executors.newFixedThreadPool(8)

    // "database"
    val passwd = Map(
      "daniel@rockthejvm.com" -> "RocktheJVM1!"
    )

    // the profile data
    val database = Map(
      "daniel@rockthejvm.com" -> "Daniel"
    )

    def login(email: String, password: String)(onSuccess: UserProfile => Unit, onFailure: AuthError => Unit) =
      executor.execute { () =>
        println(s"[${Thread.currentThread().getName}] Attempting login for $email")
        passwd.get(email) match
          case Some(`password`) =>
            onSuccess(UserProfile(email, database(email)))
          case Some(_) =>
            onFailure(AuthError("Incorrect password."))
          case None =>
            onFailure(AuthError("User with this email does not exist. Please sign up."))
      }

  def loginAsZIO(id: String, pw: String): ZIO[Any, LoginService.AuthError, LoginService.UserProfile] =
    ZIO.async[Any, LoginService.AuthError, LoginService.UserProfile] { cb => // callback object created by ZIO
      LoginService.login(id, pw)(
        profile => cb(ZIO.succeed(profile)), // notify the ZIO fiber to complete the ZIO with a success
        error => cb(ZIO.fail(error)) // same, with a failure
      )
    }

  val loginProgram = for
    email <- Console.readLine("Email: ")
    pass <- Console.readLine("Password: ")
    profile <- loginAsZIO(email, pass).debugThread
    _ <- Console.printLine(s"Welcome to Rock the JVM ${profile.name}")
  yield ()

  /**
   * Exercises
   */
  // 1 - surface a computation running on some (external) thread to a ZIO
  // hint: invoke the cb when the computation is complete
  // hint 2: don't wrap the computation into a ZIO
  def external2ZIO[A](computation: () => A)(executor: ExecutorService): Task[A] = ZIO.async { cb =>
    executor.execute { () =>
      Try(computation()).fold(t => cb(ZIO.fail(t)), a => cb(ZIO.succeed(a)))
    }
  }

  val demoExternal2ZIO =
    val executor = Executors.newFixedThreadPool(8)
    val zio: Task[Int] = external2ZIO { () =>
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    } (executor)

    zio.debugThread.unit

  // 2 - lift a Future to a ZIO
  // hint: invoke cb when the future completes
  def future2ZIO[A](future: => Future[A])(using ExecutionContext): Task[A] = ZIO.async { cb =>
    future.onComplete(_.fold(t => cb(ZIO.fail(t)), a => cb(ZIO.succeed(a))))
  }

  val demoFuture2ZIO =
    val executor = Executors.newFixedThreadPool(8)
    given ExecutionContext = ExecutionContext.fromExecutorService(executor)
    val mol: Task[Int] = future2ZIO(Future {
      println(s"[${Thread.currentThread().getName}] computing the meaning of life on some thread")
      Thread.sleep(1000)
      42
    })

    mol.debugThread.unit

  // 3 - implement a never-ending ZIO
  // hint: ZIO.async fiber is semantically blocked until cb is called
  def neverEndingZIO[A]: UIO[A] = ZIO.async(_ => ())

  def run = demoFuture2ZIO
