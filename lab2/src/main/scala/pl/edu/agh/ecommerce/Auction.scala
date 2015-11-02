package pl.edu.agh.ecommerce

import akka.actor.{ActorRef, LoggingFSM}
import pl.edu.agh.ecommerce.Auction._
import pl.edu.agh.ecommerce.Buyer.Offer
import pl.edu.agh.ecommerce.Seller.AuctionCompleted

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

class Auction extends LoggingFSM[State, Data] {
  import context._

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(StartAuction(timerConf: TimerConf, auctionConf: AuctionConf), Uninitialized) =>
      scheduleBidTimer(timerConf.bidTimerTimeout)
      goto(Created) using AuctionAwaiting(timerConf, auctionConf, sender())
  }

  when(Created) {
    case Event(Bid(offer), p: AuctionAwaiting) =>
      handleOffer(offer, sender(), None, p.auctionConf) match {
        case Some(buyerOffer) => goto(Activated) using AuctionInProgress(p.timerConf, p.auctionConf, p.seller, List(buyerOffer))
        case None => stay()
      }
    case Event(BidTimerExpired, p: AuctionAwaiting) =>
      scheduleDeleteTimer(p.timerConf.deleteTimerTimeout)
      goto(Ignored) using AuctionIgnored(p.timerConf, p.auctionConf, p.seller)
  }

  when(Activated) {
    case Event(Bid(offer), p: AuctionInProgress) =>
      handleOffer(offer, sender(), Some(p.offers.head), p.auctionConf) match {
        case Some(buyerOffer) => stay using AuctionInProgress(p.timerConf, p.auctionConf, p.seller, buyerOffer :: p.offers)
        case None => stay()
      }
    case Event(BidTimerExpired, p:AuctionInProgress) =>
      scheduleDeleteTimer(p.timerConf.deleteTimerTimeout)
      p.offers.head.buyer ! AuctionWon(p.offers.head.offer)
      p.seller ! AuctionCompleted(self)
      goto(Sold) using AuctionSold(p.timerConf, p.seller, p.offers.head)
  }

  when(Ignored) {
    case Event(DeleteTimerExpired, p: AuctionIgnored) =>
      stop()
    case Event(Relist, p: AuctionIgnored) =>
      goto(Created) using AuctionAwaiting(p.timerConf, p.auctionConf, p.seller)
  }

  when(Sold) {
    case Event(DeleteTimerExpired, p: AuctionSold) =>
      stop()
  }

  private def scheduleBidTimer(timeout: FiniteDuration) = system.scheduler.scheduleOnce(timeout, self, BidTimerExpired)

  private def scheduleDeleteTimer(timeout: FiniteDuration) = system.scheduler.scheduleOnce(timeout, self, DeleteTimerExpired)

  private def handleOffer(offer: Offer, buyer: ActorRef, bestCurrentOffer: Option[BuyerOffer], conf: AuctionConf) = {
    if(exceedsCurrentMaxOffer(offer, bestCurrentOffer, conf)) {
      buyer ! BidAccepted(offer)
      notifyPreviousWinnerAboutGazump(bestCurrentOffer, offer, conf)
      Some(BuyerOffer(offer, buyer))
    } else {
      buyer ! BidTooLow(offer, nextMin(currentMax(bestCurrentOffer, conf), conf))
      None
    }
  }

  private def currentMax(bestCurrentOffer: Option[BuyerOffer], conf: AuctionConf) =
    bestCurrentOffer match {
      case Some(buyerOffer) => buyerOffer.offer.amount
      case None => conf.initialPrice
    }

  private def exceedsCurrentMaxOffer(offer: Offer, bestCurrentOffer: Option[BuyerOffer], conf: AuctionConf): Boolean =
    offer.amount >= currentMax(bestCurrentOffer, conf)

  private def nextMin(bestCurrentOffer: BigDecimal, conf: AuctionConf): BigDecimal =
    bestCurrentOffer + conf.bidStep

  private def notifyPreviousWinnerAboutGazump(previousBestOffer: Option[BuyerOffer], currentBestOffer: Offer, conf: AuctionConf) = previousBestOffer match {
    case Some(buyerOffer) => buyerOffer.buyer ! BidTopped(buyerOffer.offer, nextMin(currentBestOffer.amount, conf))
    case _ =>
  }

}

object Auction {
  case class Bid(offer: Offer)
  case class BidTooLow(offer: Offer, minBidAmount: BigDecimal)
  case class BidAccepted(offer: Offer)
  case class BidTopped(offer: Offer, minBidAmount: BigDecimal)
  case class AuctionWon(offer: Offer)
  case class StartAuction(timerConf: TimerConf, auctionConf: AuctionConf)
  case object BidTimerExpired
  case object DeleteTimerExpired
  case object Relist

  case class BuyerOffer(offer: Offer, buyer: ActorRef)
  case class TimerConf(bidTimerTimeout: FiniteDuration, deleteTimerTimeout: FiniteDuration)
  case class AuctionConf(initialPrice: BigDecimal, bidStep: BigDecimal)
}

sealed trait State
case object Idle extends State
case object Created extends State
case object Activated extends State
case object Ignored extends State
case object Sold extends State

sealed trait Data
case object Uninitialized extends Data
case class AuctionAwaiting(timerConf: TimerConf, auctionConf: AuctionConf, seller: ActorRef) extends Data
case class AuctionInProgress(timerConf: TimerConf, auctionConf: AuctionConf, seller: ActorRef, offers: List[BuyerOffer]) extends Data
case class AuctionSold(timerConf: TimerConf, seller: ActorRef, bestOffer: BuyerOffer) extends Data
case class AuctionIgnored(timerConf: TimerConf, auctionConf: AuctionConf, seller: ActorRef) extends Data