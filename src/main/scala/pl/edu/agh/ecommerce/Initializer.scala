package pl.edu.agh.ecommerce

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import pl.edu.agh.ecommerce.AuctionCommands.{AuctionParams, TimerConf}
import pl.edu.agh.ecommerce.Buyer.StartBidding
import pl.edu.agh.ecommerce.Initializer.Initialize
import pl.edu.agh.ecommerce.Seller.RegisterAndStartAuction

import scala.concurrent.duration._

class Initializer extends Actor {

  override def receive: Receive = {
    case Initialize => initialize()
  }

  private def initialize(): Unit = {

    val publisherFactory = (f: ActorRefFactory) =>
      f.actorSelection("akka.tcp://auction-publisher@127.0.0.1:2553/user/publisher")
    val notifier = context.actorOf(Notifier.props(publisherFactory))

    val auctionFactory = (f: ActorRefFactory) => f.actorOf(Auction.props(notifier))
    val seller = context.actorOf(Props(classOf[Seller], auctionFactory), "global-seller")

    seller ! RegisterAndStartAuction(TimerConf(20 seconds, 10 seconds), AuctionParams("Audi A6", BigDecimal(100), BigDecimal(5)))
    //seller ! RegisterAndStartAuction(TimerConf(30 seconds, 5 seconds), AuctionParams("Audi A4", BigDecimal(300), BigDecimal(30)))

    val firstBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(500)))), "buyer1")
    val secondBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(600)))), "buyer2")

    firstBuyer ! StartBidding("A4")

    //secondBuyer ! StartBidding("A8")
  }

}

object Initializer {

  def props(): Props = Props[Initializer]

  case object Initialize
}
