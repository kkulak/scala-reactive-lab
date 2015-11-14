package pl.edu.agh.ecommerce

import java.time.LocalDateTime
import java.util.UUID

import akka.actor.{Props, ActorRef}
import akka.persistence.fsm.PersistentFSM
import pl.edu.agh.ecommerce.AuctionCommands._
import pl.edu.agh.ecommerce.AuctionData._
import pl.edu.agh.ecommerce.AuctionEvents._
import pl.edu.agh.ecommerce.AuctionStates._
import pl.edu.agh.ecommerce.Buyer.Offer

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.reflect.ClassTag

class Auction(id: String) extends PersistentFSM[AuctionState, AuctionData, AuctionEvent] {
  import context._

  override def domainEventClassTag: ClassTag[AuctionEvent] = ClassTag(classOf[AuctionEvent])

  override def persistenceId: String = s"auction-$id"

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(StartAuction(timerConf: TimerConf, auctionConf: AuctionParams), Uninitialized) =>
      goto(Created) applying AuctionStartedEvent(AuctionConf(LocalDateTime.now(), timerConf, auctionConf), sender()) andThen {
        case _ => scheduleBidTimer(timerConf.bidTimerTimeout)
      }
  }

  when(Created) {
    case Event(Bid(offer), p: AuctionAwaiting) =>
      handleOffer(offer, sender(), None, p.conf.params) match {
        case Some(buyerOffer) => goto(Activated) applying MinimalOfferReachedEvent(buyerOffer)
        case None => stay()
      }
    case Event(BidTimerExpired, p: AuctionAwaiting) =>
      goto(Ignored) applying  AuctionIgnoredEvent andThen {
        case _ =>
          scheduleDeleteTimer(p.conf.timer.deleteTimerTimeout)
          p.seller ! AuctionWithoutOfferFinished
      }
  }

  when(Activated) {
    case Event(Bid(offer), p: AuctionInProgress) =>
      handleOffer(offer, sender(), Some(p.offers.head), p.conf.params) match {
        case Some(buyerOffer) => stay applying OfferAcceptedEvent(buyerOffer)
        case None => stay()
      }
    case Event(BidTimerExpired, p:AuctionInProgress) =>
      goto(Sold) applying AuctionSoldEvent(p.offers.head) andThen {
        case _ =>
          scheduleDeleteTimer(p.conf.timer.deleteTimerTimeout)
          p.offers.head.buyer ! AuctionWon(p.offers.head.offer)
          p.seller ! AuctionWonBy(p.offers.head.offer, p.offers.head.buyer)
      }
  }

  when(Ignored) {
    case Event(DeleteTimerExpired, p: AuctionIgnored) =>
      stop()
    case Event(Relist, p: AuctionIgnored) =>
      goto(Created) applying AuctionRelistEvent
  }

  when(Sold) {
    case Event(DeleteTimerExpired, p: AuctionSold) =>
      stop()
  }

  override def onRecoveryCompleted(): Unit = {
    super.onRecoveryCompleted()
    rescheduleTimersIfAuctionInitialized()
  }

  override def applyEvent(domainEvent: AuctionEvent, currentData: AuctionData) = domainEvent match {
    case AuctionStartedEvent(conf, seller) =>
      AuctionAwaiting(conf, seller)
    case MinimalOfferReachedEvent(offer) => AuctionInProgress(
        currentData.asInstanceOf[AuctionAwaiting].conf,
        currentData.asInstanceOf[AuctionAwaiting].seller,
        List(offer)
      )
    case AuctionIgnoredEvent => AuctionIgnored(
        currentData.asInstanceOf[AuctionAwaiting].conf,
        currentData.asInstanceOf[AuctionAwaiting].seller
      )
    case OfferAcceptedEvent(offers) => AuctionInProgress(
        currentData.asInstanceOf[AuctionInProgress].conf,
        currentData.asInstanceOf[AuctionInProgress].seller,
        offers :: currentData.asInstanceOf[AuctionInProgress].offers
      )
    case AuctionSoldEvent(bestOffer) => AuctionSold(
        currentData.asInstanceOf[AuctionInProgress].conf,
        currentData.asInstanceOf[AuctionInProgress].seller,
        bestOffer
      )
    case AuctionRelistEvent => AuctionAwaiting(
        currentData.asInstanceOf[AuctionIgnored].conf,
        currentData.asInstanceOf[AuctionIgnored].seller
      )
  }

  private def rescheduleTimersIfAuctionInitialized(): Unit = stateData match {
    case data: InitializedAuctionData => rescheduleTimers(data.conf().timer, data.conf().start)
    case _ =>
  }

  def rescheduleTimers(timers: TimerConf, startAuction: LocalDateTime): Unit = {
    if(startAuction.plusSeconds(timers.bidTimerTimeout.toSeconds) isAfter LocalDateTime.now())
      scheduleBidTimer(timers.bidTimerTimeout)
    else
      scheduleDeleteTimer(timers.deleteTimerTimeout)
  }

  private def scheduleBidTimer(timeout: FiniteDuration) = system.scheduler.scheduleOnce(timeout, self, BidTimerExpired)

  private def scheduleDeleteTimer(timeout: FiniteDuration) = system.scheduler.scheduleOnce(timeout, self, DeleteTimerExpired)

  private def handleOffer(offer: Offer, buyer: ActorRef, bestCurrentOffer: Option[BuyerOffer], conf: AuctionParams) = {
    if(exceedsCurrentMaxOffer(offer, bestCurrentOffer, conf)) {
      buyer ! BidAccepted(offer)
      notifyPreviousWinnerAboutGazump(bestCurrentOffer, offer, conf)
      Some(BuyerOffer(offer, buyer))
    } else {
      buyer ! BidTooLow(offer, nextMin(currentMax(bestCurrentOffer, conf), conf))
      None
    }
  }

  private def currentMax(bestCurrentOffer: Option[BuyerOffer], conf: AuctionParams) =
    bestCurrentOffer match {
      case Some(buyerOffer) => buyerOffer.offer.amount
      case None => conf.initialPrice
    }

  private def exceedsCurrentMaxOffer(offer: Offer, bestCurrentOffer: Option[BuyerOffer], conf: AuctionParams): Boolean =
    offer.amount >= currentMax(bestCurrentOffer, conf)

  private def nextMin(bestCurrentOffer: BigDecimal, conf: AuctionParams): BigDecimal =
    if(bestCurrentOffer == conf.initialPrice) conf.initialPrice
    else bestCurrentOffer + conf.bidStep

  private def notifyPreviousWinnerAboutGazump(previousBestOffer: Option[BuyerOffer], currentBestOffer: Offer, conf: AuctionParams) = previousBestOffer match {
    case Some(buyerOffer) => buyerOffer.buyer ! BidTopped(buyerOffer.offer, nextMin(currentBestOffer.amount, conf))
    case _ =>
  }

}

object Auction {
  def props(): Props = Props(classOf[Auction], UUID.randomUUID().toString)
}
