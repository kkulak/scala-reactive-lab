package pl.edu.agh.ecommerce

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.AuctionSearch._

import scala.language.postfixOps

class AuctionSearch extends Actor with ActorLogging {
  var auctions: Set[AuctionRef] = Set()

  override def receive: Receive = LoggingReceive {
    case Register(auction, title) => register(auction, title)
    case Deregister(auction) => deregister(auction)
    case Search(key) => findMatchingAndNotifySender(key, sender())
  }

  private def register(auction: ActorRef, title: String): Unit = {
    auctions = auctions + AuctionRef(auction, title)
  }

  private def deregister(auction: ActorRef): Unit = {
    auctions = auctions - findAuctionRef(auction)
  }

  private def findAuctionRef(auction: ActorRef): AuctionRef = {
    auctions find {
      ref => ref.auction == auction
    } orNull
  }

  private def findMatchingAndNotifySender(key: String, sender: ActorRef): Unit = {
    val matchingAuctions = auctions
      .filter(p => p.title.split("\\s+").contains(key))
      .map(a => a.auction)

    sender ! QueryResult(matchingAuctions)
  }

}

object AuctionSearch {
  case class AuctionRef(auction: ActorRef, title: String)
  case class Register(auction: ActorRef, title: String)
  case class Deregister(auction: ActorRef)
  case class Search(key: String)

  case class QueryResult(auctions: Set[ActorRef])
}
