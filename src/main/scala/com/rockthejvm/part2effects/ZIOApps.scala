package com.rockthejvm.part2effects

import zio._

object ZIOApps {

  val meaningOfLife: UIO[Int] = ZIO.succeed(42)
  def main(args: Array[String]): Unit = {
    val runtime = Runtime.default
    given trace: Trace = Trace.empty
    Unsafe.unsafeCompat { unsafe =>
      given u: Unsafe = unsafe

      println(runtime.unsafe.run(meaningOfLife))
    }
  }
}

object BetterApp extends ZIOAppDefault {

  override def run = ZIOApps.meaningOfLife.debug
}

object ManualApp extends ZIOApp {
  override implicit def environmentTag: zio.EnvironmentTag[ManualApp.type] = ???

  override type Environment = this.type

  override def bootstrap: ZLayer[Any, Any, ManualApp.type] = ???

  override def run: ZIO[Any, Any, Any] = ???
}
