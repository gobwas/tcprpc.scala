package lite

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import org.json4s.JsonAST._

object Boot extends App {
  // we need an ActorSystem to host our application in
  implicit val system = ActorSystem("on-spray-can")

  // create and start our service actor
  val service = system.actorOf(Props[LiteActor], "demo-service")

  implicit val timeout = Timeout(5.seconds)
  // start a new HTTP server on port 8080 with our service actor as the handler
  IO(Http) ? Http.Bind(service, interface = "localhost", port = 8080)
}

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class LiteActor extends Actor with LiteService {
  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(route)
}

// this trait defines our service behavior independently from the service actor
trait LiteService extends HttpService {
  // defer client initialization
  lazy val client = { new TCPClient("127.0.0.1", 3000) }

  val route =
    path("render" / Segment / Segment) { (template, user) =>
      get {
        val resp: String = client.request("render", JArray(List(JString(template), JObject(List(JField("name", JString(user))))))) match {
          case resp: Success => resp.result;
          case resp: lite.Error => resp.error.message;
          case _ => "Unknown"
        }

        respondWithMediaType(`text/html`) {
          complete(resp)
        }
      }
    } ~
    path("greeting" / Segment) { user =>
      get {
        respondWithMediaType(`text/html`) {
          complete("Hello, " + user)
        }
      }
    } ~
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          complete("Hello World!")
        }
      }
    }
}