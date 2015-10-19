package pl.edu.agh.ecommerce

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Auction._
import pl.edu.agh.ecommerce.Buyer.Offer

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

class Auction(initialPrice: BigDecimal, bidStep: BigDecimal, conf: Conf) extends Actor with ActorLogging {
  import context._

  var bids: List[BuyerOffer] = List()

  override def receive: Receive = LoggingReceive {
    case StartBidTimer => startAuction()
  }

  private def startAuction() = {
    scheduleBidTimer()
    become(created)
  }

  private def scheduleBidTimer() = system.scheduler.scheduleOnce(conf.bidTimerTimeout, self, BidTimerExpired)

  def created: Receive = LoggingReceive {
    case Bid(offer) => handleOffer(offer, sender()) foreach (_ => become(activated))
    case BidTimerExpired => scheduleDeleteTimerAndBecomeIgnored()
  }

  private def scheduleDeleteTimerAndBecomeIgnored() = {
    scheduleDeleteTimer()
    become(ignored)
  }

  private def scheduleDeleteTimer() = system.scheduler.scheduleOnce(conf.deleteTimerTimeout, self, DeleteTimerExpired)

  private def handleOffer(offer: Offer, buyer: ActorRef) = {
    if(exceedsCurrentMaxOffer(offer)) {
      bids = BuyerOffer(offer, buyer) :: bids
      buyer ! BidAccepted(offer)
      notifyPreviousWinnerAboutGazump()
      Some(BuyerOffer(offer, buyer))
    } else {
      buyer ! BidTooLow(offer, nextMin())
      None
    }
  }

  def ignored: Receive = LoggingReceive {
    case DeleteTimerExpired => stop(self)
    case Relist => startAuction()
  }

  def activated: Receive = LoggingReceive {
    case Bid(offer) => handleOffer(offer, sender())
    case BidTimerExpired => scheduleDeleteTimerAndBecomeSold()
  }

  private def scheduleDeleteTimerAndBecomeSold() = {
    scheduleDeleteTimer()
    lastWinner() foreach { winner => winner.buyer ! AuctionWon(winner.offer) }
    become(sold)
  }

  def sold: Receive = LoggingReceive {
    case DeleteTimerExpired => stop(self)
  }

  private def nextMin(): BigDecimal = currentMaxOffer() + bidStep

  private def exceedsCurrentMaxOffer(offer: Offer): Boolean = offer.amount >= nextMin()

  private def notifyPreviousWinnerAboutGazump() = bids match {
    case x :: (xs :: xy) => xs.buyer ! BidTopped(xs.offer, nextMin())
    case _ =>
  }

  private def lastWinner() = bids match {
    case Nil => None
    case x :: xs => Some(x)
  }

  private def currentMaxOffer(): BigDecimal =
    lastWinner()
    .map(w => w.offer.amount)
    .getOrElse(initialPrice)

}

object Auction {
  case class Bid(offer: Offer)
  case class StartBidding(amount: BigDecimal, auction: ActorRef)
  case class BidTooLow(offer: Offer, minBidAmount: BigDecimal)
  case class BidAccepted(offer: Offer)
  case class BidTopped(offer: Offer, minBidAmount: BigDecimal)
  case class AuctionWon(offer: Offer)
  case object StartBidTimer
  case object BidTimerExpired
  case object DeleteTimerExpired
  case object Relist

  case class BuyerOffer(offer: Offer, buyer: ActorRef)
  case class Conf(bidTimerTimeout: FiniteDuration, deleteTimerTimeout: FiniteDuration)
}