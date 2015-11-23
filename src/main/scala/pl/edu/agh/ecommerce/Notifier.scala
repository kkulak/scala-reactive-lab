package pl.edu.agh.ecommerce

import akka.actor._
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Notifier.Notification

class Notifier(publisherFactory: ActorRefFactory => ActorSelection) extends Actor {

  override def receive: Receive = LoggingReceive {
    case e: Notification => publisherFactory(context) ! e
  }

}

case object Notifier {

  def props(publisherFactory: ActorRefFactory => ActorSelection): Props = Props(classOf[Notifier], publisherFactory)

  sealed trait Notification
  case class OfferAccepted(title: String, buyer: ActorRef, price: BigDecimal) extends Notification
  case class AuctionFinishedWithWinner(title: String, winner: ActorRef, price: BigDecimal) extends Notification
  case class AuctionFinishedWithoutWinner(title: String) extends Notification

}
