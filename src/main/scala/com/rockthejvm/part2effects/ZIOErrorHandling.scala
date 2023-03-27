package com.rockthejvm.part2effects

import zio.*

import java.io.IOException
import java.net.NoRouteToHostException
import scala.util.{Failure, Success, Try}
object ZIOErrorHandling extends ZIOAppDefault {

  val aFailedZIO = ZIO.fail("Something went wrong")
  val failedWithThrowable = ZIO.fail(new RuntimeException("Boom!"))
  val failedWithDescription = failedWithThrowable.mapError(_.getMessage)

  // attempt: run an effect that might throw an exception
  val badZIO = ZIO.succeed {
    println("Trying something")
    val string: String = null
    string.length
  } // this is bad

  val anAttempt: ZIO[Any,Throwable,Int] = ZIO.attempt {
    println("Trying something")
    val string: String = null
    string.length
  }

  // effectfully catch errors
  val catchError = anAttempt.catchAll(e => ZIO.succeed(s"Returning a different value because $e"))
  val catchSelectiveErrors = anAttempt.catchSome {
    case e: RuntimeException => ZIO.succeed(s"Ignoring runtime exception: $e")
    case _ => ZIO.succeed("Ignoring everything else")
  }

  // chain effects
  val aBetterAttempt = anAttempt.orElse(ZIO.succeed(56))

  // fold: handle both success and failure
  val handleBoth = anAttempt.fold(ex => s"Something bad happened: $ex", value => s"Length of the string was $value")

  // effectful fold: foldZIO
  val handleBoth_v2 = anAttempt.foldZIO(
    ex => ZIO.succeed(s"Something bad happened: $ex"),
    value => ZIO.succeed(s"Length of the string was $value")
  )

  /*
    Conversions between Option/Try/Either to ZIO
   */

  val aTryToZIO = ZIO.fromTry(Try(42 / 0))

  // either -> ZIO
  val anEither: Either[Int, String] = Right("Success!")
  val anEitherToZIO = ZIO.fromEither(anEither)

  // ZIO -> ZIO with Either as the value channel
  val eitherZIO = anAttempt.either
  // reverse
  val anAttempt_v2 = eitherZIO.absolve

  // option -> ZIO
  val anOption = ZIO.fromOption(Option(42))

  /**
   * Exercise: Implement a version of fromTry, fromOption, fromEither, either, absolve
   * using fold and foldZIO
   */

  def fromTry[A](value: => Try[A]): Task[A] = value match
    case Failure(ex) => ZIO.fail(ex)
    case Success(v) => ZIO.succeed(v)

  def fromEither[E,A](value: => Either[E,A]): IO[E,A] = value match
    case Left(ex) => ZIO.fail(ex)
    case Right(v) => ZIO.succeed(v)

  def fromOption[A](value: => Option[A]): IO[Option[Nothing], A] = value match
    case Some(v) => ZIO.succeed(v)
    case None => ZIO.fail(None)

  extension [R,E,A](zio: ZIO[R,E,A])
    def either_v2: URIO[R, Either[E,A]] = zio.fold(
      ex => Left(ex),
      v => Right(v)
    )

    def absolve_v2[E1 >: E, B](implicit ev: A IsSubtypeOfOutput Either[E1, B], trace: Trace): ZIO[R, E1, B] =
      ZIO.absolve(zio.map(ev))

  /*
    Errors =failures in the ZIO type signature ("checked" exception)
    Defects = failures that unrecoverable, onforeseen, NOT present in the ZIO type signature

    ZIO[R,E,A] can finish with Exit[E,A]
      - Success[A] containing A
      - Cause[E]
        - Fail[E] contianing the error
        - Die(t: Throwable) which was unforeseen
   */

  val divisionByZero: UIO[Int] = ZIO.succeed(1 / 0)

  val failedInt: ZIO[Any, String, Int] = ZIO.fail("I Failed!")
  val failureCauseExposed: ZIO[Any, Cause[String], Int] = failedInt.sandbox
  val failureCauseHidden: ZIO[Any, String, Int] = failureCauseExposed.unsandbox
  // fold with cause
  val foldedWithCause = failedInt.foldCause(
    cause => s"This failed with ${cause.defects}",
    value => s"this succeeded with $value"
  )
  val foldedWithCause_v2 = failedInt.foldCauseZIO(
    cause => ZIO.succeed(s"This failed with ${cause.defects}"),
    value => ZIO.succeed(s"this succeeded with $value")
  )

  def callHTTPEndpoint(url: String): ZIO[Any, IOException, String] =
    ZIO.fail(new IOException("no internet, dummy!"))

  val endpointCallWithDefects: ZIO[Any, Nothing, String] =
    callHTTPEndpoint("rockthejvm.com").orDie // all errors are now defects

  // refining the error channel
  def callHTTPEndpointWideError(url: String): ZIO[Any, Exception, String] =
    ZIO.fail(new IOException("No internet!!!"))

  def callHTTPENdpoint_v2(url: String): ZIO[Any, IOException, String] =
    callHTTPEndpointWideError(url).refineOrDie[IOException] {
      case e: IOException => e
      case _: NoRouteToHostException => new IOException(s"No route to host to $url, can't fetch page")
    }

  // reverse: turn defects into the error channel
  val endpointCallWithError = endpointCallWithDefects.unrefine {
    case e => e.getMessage
  }

  /*
    Combine effects with different errors
   */
  case class IndexError(message: String)
  case class DbError(message: String)
  val callApi: ZIO[Any, IndexError, String] = ZIO.succeed("page: <html></html>")
  val queryDb: ZIO[Any, DbError, Int] = ZIO.succeed(1)
  val combined = for {
    page <- callApi
    rowsAffected <- queryDb
  } yield (page, rowsAffected) // lost type safety
  /*
    Solutions:
      - design an error model
      - use Scala 3 union types
      - .mapError to some common error type
   */

  /**
   *  Exercises
   */
  // 1 - make this effect fail with a TYPED error
  val aBadFailure = ZIO.succeed[Int](throw new RuntimeException("this is bad!"))
  val aBetterFailure = aBadFailure.unrefine {
    e => new Exception(e)
  }

  // 2 - transform a ZIO into another ZIO with a narrower exception type
  def ioException[R,A](zio: ZIO[R, Throwable, A]): ZIO[R, IOException, A] = zio.refineOrDie {
    case e: IOException => e
  }

  // 3
  def left[R,E,A,B](zio: ZIO[R, E, Either[A,B]]): ZIO[R,Either[E,A], B] = zio.foldZIO(
    e => ZIO.fail(Left(e)),
    either => either match
      case Left(a) => ZIO.fail(Right(a))
      case Right(b) => ZIO.succeed(b)
  )

  // 4
  val database = Map(
    "daniel" -> 123,
    "alice" -> 789
  )
  case class QueryError(reason: String)
  case class UserProfile(name: String, phone: Int)

  def lookupProfile(userId: String): ZIO[Any, QueryError, Option[UserProfile]] =
    if userId != userId.toLowerCase
    then ZIO.fail(QueryError("user Id format is invalid"))
    else ZIO.succeed(database.get(userId).map(phone => UserProfile(userId, phone)))

  // surface out all the failed cases of this api
  def betterLookupProfile(userId: String): ZIO[Any, QueryError | Option[Nothing], UserProfile] =
    lookupProfile(userId).flatMap(mUser => ZIO.fromOption(mUser))

  override def run = betterLookupProfile("Daniel").debug
}
