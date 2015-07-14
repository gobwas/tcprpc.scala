package lite

import java.io.{OutputStream, InputStream}
import java.net.Socket
import java.util.concurrent.{TimeUnit, ConcurrentHashMap}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import akka.actor._

case class Request(id: String, method: String, params: JArray)

sealed trait Response { def id: String }
case class Success(id: String, result: String) extends Response
case class Error(id: String, error: ErrorDescription) extends Response
case class ErrorDescription(code: Int, message: String)

class SocketReader(stream: InputStream, responses: ConcurrentHashMap[String, Promise[Response]]) extends Runnable {
  def run(): Unit = {

    val readBuffer = new Array[Byte](512)
    var buffer = List[String]()

    implicit val formats = org.json4s.DefaultFormats

    def rec(parts: List[String]): List[Response] = {
      if (parts.length == 0) return List()

      parse(parts.head) match {
        case json: JObject => (json.extractOpt[Success], json.extractOpt[Error]) match {
            case (Some(success), None) => List(success) ::: rec(parts.tail)
            case (None, Some(error)) => List(error) ::: rec(parts.tail)
            case _ => rec(parts.tail)
        }

        case _ => rec(parts.tail)
      }
    }

    def make(hunk: String): List[Response] = {
      // hunk not have delimiter yet
      // so we need to wait for other hunks
      if (hunk.indexOf('\n') == -1) {
        buffer = buffer ::: List(hunk)
        return List()
      }

      val p = hunk.split('\n').toList
      val parts = List((buffer ::: p.take(1)).mkString) ::: p.drop(1)

      // if true - there are no uncompleted parts
      // and we not need to buffer something
      if ( hunk.lastIndexOf('\n') != (hunk.length - 1) ) {
        buffer = parts.takeRight(1)
        return rec(parts.dropRight(1))
      }

      rec(parts)
    }

    Stream.continually(stream.read(readBuffer)).takeWhile(_ != -1).foreach(count => {
      if (count == -1) {
        println("Stream hung up")
        Thread.currentThread().interrupt()
      }

      make(new String(readBuffer.slice(0, count).map(_.toChar))).foreach((r: Response) => {
        responses.remove(r.id).success(r)
      })
    })
  }
}

class WritingActor(stream: OutputStream) extends Actor {
  import org.json4s.JsonDSL._

  def receive = {
    case req: Request => {
      stream.write(compact(("id" -> req.id) ~ ("topic" -> req.method) ~ ("params" -> req.params)).getBytes)
      stream.write('\n'.toByte)
    }
  }
}

trait Client {
  def request(method: String, params: JArray): Response
}

class TCPClient(host: String, port: Int) extends Client {
  private val socket = new Socket(host, port)

  private val responses = new ConcurrentHashMap[String, Promise[Response]]()
  private val reader = new Thread(new SocketReader(socket.getInputStream, responses))

  val system = ActorSystem("write")
  val writer = system.actorOf(Props(new WritingActor(socket.getOutputStream)), name = "writer")

  reader.start()

  def request(method: String, params: JArray): Response = {
    val requestId = java.util.UUID.randomUUID().toString
    val p = Promise[Response]()

    responses.put(requestId, p)
    writer ! Request(requestId, method, params)

    Await.result(p.future, Duration.create(5000, TimeUnit.MILLISECONDS))
  }
}
