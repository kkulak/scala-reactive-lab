package pl.edu.agh.ecommerce

import akka.actor._
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.AuctionCommands._
import pl.edu.agh.ecommerce.AuctionSearch.{Deregister, Register}
import pl.edu.agh.ecommerce.Seller.RegisterAndStartAuction

class Seller(auctionFactory: ActorRefFactory => ActorRef) extends Actor with ActorLogging {
  var auctions: List[ActorRef] = List()

  override def receive: Receive = LoggingReceive {
    case RegisterAndStartAuction(timerConf, auctionConf) =>
      registerAndStartAuction(timerConf, auctionConf)
    case AuctionWonBy(offer, buyer) => deregisterAuction(sender())
    case AuctionWithoutOfferFinished => deregisterAuction(sender())
  }

  private def registerAndStartAuction(timerConf: TimerConf, auctionConf: AuctionParams): Unit = {
    val auction = auctionFactory(context)
    auctions = auction :: auctions
    auctionSearch ! Register(auction, auctionConf.title)
    auction ! StartAuction(timerConf, auctionConf)
  }

  private def deregisterAuction(auction: ActorRef): Unit = {
    auctions = auctions filterNot(ref => ref == auction)
    auctionSearch ! Deregister(auction)
  }

  private def auctionSearch = context.actorSelection("/user/auction-search")

}

object Seller {
  case class RegisterAndStartAuction(timerConf: TimerConf, auctionConf: AuctionParams)
}
