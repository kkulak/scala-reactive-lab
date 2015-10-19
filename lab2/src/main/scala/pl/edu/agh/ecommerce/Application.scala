package pl.edu.agh.ecommerce

import akka.actor.{Props, ActorSystem}
import pl.edu.agh.ecommerce.Initializer.Initialize

object Application extends App {
  implicit val system = ActorSystem("ecommerce")
  val initializer = system.actorOf(Props[Initializer])
  initializer ! Initialize
}
