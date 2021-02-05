package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.discovery.ServiceDiscovery.ResolvedTarget
import akka.discovery.{ Lookup, ServiceDiscovery }
import akka.grpc.GrpcClientSettings
import example.myapp.helloworld.grpc.helloworld.{ GreeterService, GreeterServiceClient, HelloRequest }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class ChannelLeakSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with ScalaFutures {
  implicit val system = ActorSystem("ChannelLeakSpec")
  implicit val mat = akka.stream.ActorMaterializer.create(system)
  implicit val ec = system.dispatcher

  "A client that connects to a non-existing service" should {
    "not leak managed connections" in {
      val discovery = new ServiceDiscovery {
        override def lookup(lookup: Lookup, resolveTimeout: FiniteDuration): Future[ServiceDiscovery.Resolved] =
          Future.successful(
            ServiceDiscovery.Resolved(lookup.serviceName, List(ResolvedTarget("localhost", Some(4242), None))))
      }
      val clientSettings = GrpcClientSettings.usingServiceDiscovery(GreeterService.name, discovery)

      for {
        i <- 0 to 1000000
      } {
        system.log.warning(s"Attempt $i")
        val client = GreeterServiceClient(clientSettings)
        system.log.info(client.sayHello(HelloRequest(s"Hello $i!")).failed.futureValue.toString)
        client.close().futureValue
        //Thread.sleep(1000)
      }
    }
  }
}
