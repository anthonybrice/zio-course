package com.rockthejvm.part2effects

import zio._
import com.rockthejvm.part2effects.ZIODependencies.{EmailService, User, UserDatabase}

class UserSubscription(emailService: EmailService, userDatabase: UserDatabase) {
  def subscribeUser(user: User): Task[Unit] = for {
    _ <- emailService.email(user)
    _ <- userDatabase.insert(user)
  } yield ()
}

object UserSubscription {
  def create(emailService: EmailService, userDatabase: UserDatabase) =
    new UserSubscription(emailService, userDatabase)
    
  val live: ZLayer[EmailService & UserDatabase, Nothing, UserSubscription] =
    ZLayer.fromFunction(create)
}
