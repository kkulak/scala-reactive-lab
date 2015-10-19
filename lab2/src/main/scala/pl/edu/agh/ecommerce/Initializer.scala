package pl.edu.agh.ecommerce

import akka.actor.{Props, ActorRef, Actor}
import pl.edu.agh.ecommerce.Auction.{StartBidTimer, Conf, StartBidding}
import pl.edu.agh.ecommerce.Initializer.Initialize
import scala.concurrent.duration._

class Initializer extends Actor {

  override def receive: Receive = {
    case Initialize => initialize()
  }

  private def initialize(): Unit = {
    val firstAuction: ActorRef = context.actorOf(Props(new Auction(BigDecimal(10), BigDecimal(5), Conf(5 seconds, 10 seconds))), "auction1")
    val secondAuction: ActorRef = context.actorOf(Props(new Auction(BigDecimal(100), BigDecimal(1), Conf(10 seconds, 15 seconds))), "auction2")

    val firstBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(500)))), "buyer1")
/*    val secondBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(600)))), "buyer2")
    val thirdBuyer: ActorRef = context.actorOf(Props(new Buyer(new Wallet(BigDecimal(200)))), "buyer3")*/

    firstAuction ! StartBidTimer
    secondAuction ! StartBidTimer

    firstBuyer ! StartBidding(BigDecimal(0), firstAuction)
    firstBuyer ! StartBidding(BigDecimal(1), secondAuction)

/*    secondBuyer ! StartBidding(BigDecimal(1), firstAuction)
    secondBuyer ! StartBidding(BigDecimal(0), secondAuction)

    thirdBuyer ! StartBidding(BigDecimal(5), firstAuction)
    thirdBuyer ! StartBidding(BigDecimal(1), secondAuction)*/
  }

}

object Initializer {
  case object Initialize
}
