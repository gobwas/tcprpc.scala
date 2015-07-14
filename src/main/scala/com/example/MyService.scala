package com.example

import akka.actor.Actor
import lite.{Success, TCPClient}
import spray.routing._
import spray.http._
import MediaTypes._
import org.json4s.JsonAST._

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(route)
}


// this trait defines our service behavior independently from the service actor
trait MyService extends HttpService {

//  val client = new TCPClient("127.0.0.1", 3000)

  val route =
//    path("render" / Segment / Segment) { (template, user) =>
//      get {
//        val resp: String = client.request("render", JArray(List(JString(template), JObject(List(JField("name", JString(user))))))) match {
//          case resp: Success => resp.result;
//          case resp: lite.Error => resp.error.message;
//          case _ => "Unknown"
//        }
//
//        respondWithMediaType(`text/html`) {
//          complete(resp)
//        }
//      }
//    } ~
//    path("greeting" / Segment) { user =>
//      get {
//        respondWithMediaType(`text/html`) {
//          complete("Hello, " + user)
//        }
//      }
//    } ~
    path("") {
      get {
        respondWithMediaType(`text/html`) {
          complete("Hello World!")
        }
      }
    }
}