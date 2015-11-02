package pl.edu.agh.ecommerce

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import pl.edu.agh.ecommerce.Auction.{AuctionConf, TimerConf}
import pl.edu.agh.ecommerce.Buyer.StartBidding
import pl.edu.agh.ecommerce.Initializer.Initialize
import pl.edu.agh.ecommerce.Seller.RegisterAndStartAuction

import scala.concurrent.duration._

class Initializer extends Actor {

  override def receive: Receive = {
    case Initialize => initialize()
  }

  private def initialize(): Unit = {
    context.actorOf(Props[AuctionSearch], "auction-search")

    val auctionFactory = (f: ActorRefFactory) => f.actorOf(Props[Auction])
    val seller = context.actorOf(Props(classOf[Seller], auctionFactory), "global-seller")

    seller ! RegisterAndStartAuction("Audi A6", TimerConf(20 seconds, 10 seconds), AuctionConf(BigDecimal(100), BigDecimal(5)))
    seller ! RegisterAndStartAuction("Audi A4", TimerConf(30 seconds, 5 seconds), AuctionConf(BigDecimal(300), BigDecimal(30)))

    val firstBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(500)))), "buyer1")
    val secondBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(600)))), "buyer2")

    firstBuyer ! StartBidding("Audi")

    secondBuyer ! StartBidding("A8")
  }

}

object Initializer {
  case object Initialize
}
