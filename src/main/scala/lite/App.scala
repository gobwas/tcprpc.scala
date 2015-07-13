package lite

import org.json4s.JsonAST._

object App {
  def main (args: Array[String]) {
    val client = new TCPClient("127.0.0.1", 3000)
    client.request("render", JArray(List(JString("template"), JObject(List(JField("name", JString("Sergey")))))))
    println("Server running!")
  }
}
