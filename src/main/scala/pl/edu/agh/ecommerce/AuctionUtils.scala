package pl.edu.agh.ecommerce

import java.time.LocalDateTime

import akka.actor.ActorRef
import akka.persistence.fsm.PersistentFSM.FSMState
import pl.edu.agh.ecommerce.AuctionCommands.{BuyerOffer, AuctionParams, TimerConf}
import pl.edu.agh.ecommerce.AuctionData.AuctionConf
import pl.edu.agh.ecommerce.Buyer.Offer

import scala.concurrent.duration.FiniteDuration

object AuctionCommands {
  case class Bid(offer: Offer)
  case class BidTooLow(offer: Offer, minBidAmount: BigDecimal)
  case class BidAccepted(offer: Offer)
  case class BidTopped(offer: Offer, minBidAmount: BigDecimal)
  case class AuctionWon(offer: Offer)
  case class AuctionWonBy(offer: Offer, buyer: ActorRef)
  case object AuctionWithoutOfferFinished
  case class StartAuction(timerConf: TimerConf, auctionConf: AuctionParams)
  case object BidTimerExpired
  case object DeleteTimerExpired
  case object Relist

  case class BuyerOffer(offer: Offer, buyer: ActorRef)
  case class TimerConf(bidTimerTimeout: FiniteDuration, deleteTimerTimeout: FiniteDuration)
  case class AuctionParams(initialPrice: BigDecimal, bidStep: BigDecimal)
}

object AuctionStates {
  sealed trait AuctionState extends FSMState
  case object Idle extends AuctionState {
    override def identifier: String = "Idle"
  }
  case object Created extends AuctionState {
    override def identifier: String = "Created"
  }
  case object Activated extends AuctionState {
    override def identifier: String = "Activated"
  }
  case object Ignored extends AuctionState {
    override def identifier: String = "Ignored"
  }
  case object Sold extends AuctionState {
    override def identifier: String = "Sold"
  }
}

object AuctionData {
  case class AuctionConf(start: LocalDateTime, timer: TimerConf, params: AuctionParams)
  sealed trait AuctionData
  sealed trait InitializedAuctionData extends AuctionData {
    def conf(): AuctionConf
  }

  case object Uninitialized extends AuctionData
  case class AuctionAwaiting(conf: AuctionConf, seller: ActorRef) extends InitializedAuctionData
  case class AuctionInProgress(conf: AuctionConf, seller: ActorRef, offers: List[BuyerOffer]) extends InitializedAuctionData
  case class AuctionSold(conf: AuctionConf, seller: ActorRef, bestOffer: BuyerOffer) extends InitializedAuctionData
  case class AuctionIgnored(conf: AuctionConf, seller: ActorRef) extends InitializedAuctionData
}

object AuctionEvents {
  sealed trait AuctionEvent
  case class AuctionStartedEvent(conf: AuctionConf, seller: ActorRef) extends AuctionEvent
  case class MinimalOfferReachedEvent(offer: BuyerOffer) extends AuctionEvent
  case object AuctionIgnoredEvent extends AuctionEvent
  case class OfferAcceptedEvent(offer: BuyerOffer) extends AuctionEvent
  case class AuctionSoldEvent(bestOffer: BuyerOffer) extends AuctionEvent
  case object AuctionRelistEvent extends AuctionEvent
}