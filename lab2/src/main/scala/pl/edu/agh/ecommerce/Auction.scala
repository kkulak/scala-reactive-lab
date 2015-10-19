package pl.edu.agh.ecommerce

import akka.actor.{Actor, ActorLogging, ActorRef}
import pl.edu.agh.ecommerce.Auction._
import pl.edu.agh.ecommerce.Buyer.Offer

class Auction(initialPrice: BigDecimal, bidStep: BigDecimal) extends Actor with ActorLogging {
  var bids: List[BuyerOffer] = List()

  override def receive: Receive = {
    case Bid(offer) => handleOffer(offer, sender())
  }

  private def handleOffer(offer: Offer, buyer: ActorRef) = {
    if(exceedsCurrentMaxOffer(offer)) {
      bids = BuyerOffer(offer, buyer) :: bids
      buyer ! BidAccepted(offer)
      notifyLastWinnerAboutGazump()
      log.info(s"Success offer! Current price: ${currentMaxOffer()}")
    } else {
      buyer ! BidTooLow(offer, nextMin())
      log.info("Sorry, offer too low.")
    }
  }

  private def nextMin(): BigDecimal = currentMaxOffer() + bidStep

  private def exceedsCurrentMaxOffer(offer: Offer): Boolean = offer.amount >= nextMin()

  // TODO: ugly
  def notifyLastWinnerAboutGazump() = {
    if(bids.length > 1) {
      val lastBid = bids(1)
      lastBid.buyer ! BidTopped(lastBid.offer, nextMin())
    }
  }

  private def currentMaxOffer(): BigDecimal = bids match {
    case Nil => initialPrice
    case _ => bids.head.offer.amount
  }

}

object Auction {
  case class Bid(offer: Offer)
  case class StartBidding(amount: BigDecimal, auction: ActorRef)
  case class BidTooLow(offer: Offer, minBidAmount: BigDecimal)
  case class BidAccepted(offer: Offer)
  case class BidTopped(offer: Offer, minBidAmount: BigDecimal)
  case class AuctionWon(offer: Offer)

  case class BuyerOffer(offer: Offer, buyer: ActorRef)
}