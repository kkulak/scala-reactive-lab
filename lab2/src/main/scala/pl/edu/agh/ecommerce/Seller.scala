package pl.edu.agh.ecommerce

import akka.actor._
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Auction._
import pl.edu.agh.ecommerce.AuctionSearch.{Deregister, Register}
import pl.edu.agh.ecommerce.Seller.RegisterAndStartAuction

class Seller(auctionFactory: (ActorRefFactory, TimerConf, AuctionConf) => ActorRef) extends Actor with ActorLogging {
  var auctions: List[ActorRef] = List()

  override def receive: Receive = LoggingReceive {
    case RegisterAndStartAuction(title, timerConf, auctionConf) =>
      registerAndStartAuction(title, timerConf, auctionConf)
    case AuctionWonBy(offer, buyer) => deregisterAuction(sender())
    case AuctionWithoutOfferFinished => deregisterAuction(sender())
  }

  private def registerAndStartAuction(title: String, timerConf: TimerConf, auctionConf: AuctionConf): Unit = {
    val auction = auctionFactory(context, timerConf, auctionConf)
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
}
