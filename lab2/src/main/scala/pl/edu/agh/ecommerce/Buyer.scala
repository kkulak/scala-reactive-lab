package pl.edu.agh.ecommerce

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Auction._
import pl.edu.agh.ecommerce.Buyer._

class Buyer(wallet: Wallet) extends Actor with ActorLogging {
  var wonAuctions: Set[AuctionInfo] = Set()

  override def receive: Receive = LoggingReceive {
    case StartBidding(amount, auction) => sendBidIfCanOffer(amount, auction)
    case BidTooLow(previousOffer, minBidAmount) => withdrawLastAndSendNewOffer(previousOffer, minBidAmount, sender())
    case BidAccepted(offer) => notifyActuallyWon(offer)
    case BidTopped(offer, minBidAmount) => withdrawLastAndSendNewOffer(offer, minBidAmount, sender())
    case AuctionWon(offer) => handleWonAuction(offer, sender())
  }

  private def sendBidIfCanOffer(amount: BigDecimal, auction: ActorRef): Unit = {
    if(wallet canAfford amount) {
      val offerId = UUID.randomUUID().toString
      wallet.markPotentialExpense(Offer(offerId, amount))
      auction ! Bid(Offer(offerId, amount))
    }
  }

  private def withdrawLastAndSendNewOffer(offer: Offer, minBidAmount: BigDecimal, auction: ActorRef): Unit = {
    wallet withdrawPotentialExpense offer
    sendBidIfCanOffer(minBidAmount, auction)
  }

  private def notifyActuallyWon(offer: Offer): Unit = log.info(s"Your offer ${offer} is actually the highest one. Gz!")

  private def handleWonAuction(offer: Offer, auction: ActorRef) = {
    wallet confirmExpense offer
    val auctionInfo = AuctionInfo(offer, auction)
    wonAuctions += auctionInfo
  }

}

class Wallet(var moneyAmount: BigDecimal) {
  var potentialExpenses: Set[Offer] = Set()

  def canAfford(amount: BigDecimal): Boolean = moneyAmount - sumOfPotentialExpenses() - amount >= BigDecimal(0)

  def markPotentialExpense(offer: Offer): Unit = {
    potentialExpenses = potentialExpenses + offer
  }

  def withdrawPotentialExpense(offer: Offer): Unit = {
    potentialExpenses = potentialExpenses - offer
  }

  def confirmExpense(offer: Offer): Unit = {
    potentialExpenses -= offer
    moneyAmount -= offer.amount
  }

  private def sumOfPotentialExpenses(): BigDecimal = potentialExpenses.map(_.amount).sum

}

object Buyer {
  case class AuctionInfo(offer: Offer, ref: ActorRef)
  case class Offer(id: String, amount: BigDecimal)
}
