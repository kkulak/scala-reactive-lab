package pl.edu.agh.ecommerce

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Auction.{AuctionConf, StartAuction, TimerConf}
import pl.edu.agh.ecommerce.AuctionSearch.{Deregister, Register}
import pl.edu.agh.ecommerce.Seller.{AuctionCompleted, RegisterAndStartAuction}

class Seller extends Actor with ActorLogging {
  var auctions: List[ActorRef] = List()

  override def receive: Receive = LoggingReceive {
    case RegisterAndStartAuction(title, timerConf, auctionConf) =>
      registerAndStartAuction(title, timerConf, auctionConf)
    case AuctionCompleted(auction) => deregisterAuction(auction)
  }

  private def registerAndStartAuction(title: String, timerConf: TimerConf, auctionConf: AuctionConf): Unit = {
    val auction = context.actorOf(Props[Auction])
    auctions = auction :: auctions
    auctionSearch ! Register(auction, title)
    auction ! StartAuction(timerConf, auctionConf)
  }

  private def deregisterAuction(auction: ActorRef): Unit = {
    auctions = auctions filterNot(ref => ref == auction)
    auctionSearch ! Deregister(auction)
  }

  private def auctionSearch = context.actorSelection("/user/auction-search")

}

object Seller {
  case class RegisterAndStartAuction(title: String, timerConf: TimerConf, auctionConf: AuctionConf)
  case class AuctionCompleted(auction: ActorRef)
}
