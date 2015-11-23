package pl.edu.agh.ecommerce

import akka.actor.SupervisorStrategy.Restart
import akka.actor._
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Notifier.Notification

import scala.concurrent.duration._

class Notifier(publisherFactory: ActorRefFactory => ActorSelection) extends Actor with ActorLogging {

  override def receive: Receive = LoggingReceive {
    case payload: Notification =>
      context.actorOf(NotifierRequest.props(publisherFactory, payload))
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 100, withinTimeRange = 20 seconds) {
      case _: Exception => Restart
    }

}

case object Notifier {

  def props(publisherFactory: ActorRefFactory => ActorSelection): Props = Props(classOf[Notifier], publisherFactory)

  sealed trait Notification
  case class OfferAccepted(title: String, buyer: ActorRef, price: BigDecimal) extends Notification
  case class AuctionFinishedWithWinner(title: String, winner: ActorRef, price: BigDecimal) extends Notification
  case class AuctionFinishedWithoutWinner(title: String) extends Notification

}
