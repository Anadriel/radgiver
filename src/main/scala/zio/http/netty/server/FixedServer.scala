package zio.http.netty.server

import zio.*
import zio.http.Server.Config
import zio.http.{Driver, HttpApp, Server}

import java.util.concurrent.atomic.LongAdder

// TODO: Remove this when https://github.com/zio/zio-http/issues/2381 is fixed
object FixedServer {

  def withApp[R](app: HttpApp[R]): ZLayer[R & Config, Throwable, Server] = {
    implicit val trace: Trace = Trace.empty

    NettyDriver.live >+> ZLayer.scoped {
      for {
        driver <- ZIO.service[Driver]
        config <- ZIO.service[Config]
        inFlightRequests <- Promise.make[Throwable, LongAdder]
        _ <- Scope.addFinalizer(
          inFlightRequests.await.flatMap { counter =>
            ZIO
              .succeed(counter.longValue())
              .repeat(
                Schedule
                  .identity[Long]
                  .zip(Schedule.elapsed)
                  .untilOutput { case (inFlight, elapsed) =>
                    inFlight == 0L || elapsed > config.gracefulShutdownTimeout
                  } &&
                  Schedule.fixed(10.millis)
              )
          }.ignoreLogged
        )
        _ <- ZIO.environment[R].flatMap(driver.addApp(app, _))
        result <- driver.start.catchAllCause(cause =>
          inFlightRequests.failCause(cause) *> ZIO.refailCause(cause)
        )
        _ <- inFlightRequests.succeed(result.inFlightRequests)
      } yield FixedServerLive(driver, result.port)
    }
  }

  final private case class FixedServerLive(
    driver: Driver,
    bindPort: Int,
  ) extends Server {
    def install[R](httpApp: HttpApp[R])(implicit trace: Trace): URIO[R, Unit] = ZIO.unit
    def port: Int = bindPort
  }

}
