package pl.edu.agh.ecommerce

import akka.actor.{Props, ActorLogging, Actor}
import pl.edu.agh.ecommerce.Notifier.{AuctionFinishedWithoutWinner, AuctionFinishedWithWinner, OfferAccepted}

class Publisher extends Actor with ActorLogging {

  override def receive: Receive = {
    case OfferAccepted(title, buyer, price) =>
      log.info(s"Received offer accepted message: $title $buyer $price")
    case AuctionFinishedWithWinner(title, winner, price) =>
      log.info(s"Received auction finished with winner: $title $winner $price")
    case AuctionFinishedWithoutWinner(title) =>
      log.info(s"Received auction finished without winner: $title")
  }

}

case object Publisher {

  def props(): Props = Props[Publisher]

}
