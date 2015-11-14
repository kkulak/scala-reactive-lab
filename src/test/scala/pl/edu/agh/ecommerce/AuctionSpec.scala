package pl.edu.agh.ecommerce

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}
import pl.edu.agh.ecommerce.AuctionCommands._
import pl.edu.agh.ecommerce.Buyer.Offer

import scala.concurrent.duration._
import scala.language.postfixOps

class AuctionSpec extends TestKit(ActorSystem("AuctionSpec"))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfterEach with ImplicitSender {

  var auction: ActorRef = _
  var timerConf: TimerConf = _
  var auctionConf: AuctionParams = _
  var buyer: TestProbe = _

  override protected def beforeEach(): Unit = {
    auction = system.actorOf(Auction.props())
    timerConf = TimerConf(bidTimerTimeout = 10 seconds, deleteTimerTimeout = 20 seconds)
    auctionConf = AuctionParams(initialPrice = BigDecimal(100), bidStep = BigDecimal(20))
    buyer = TestProbe()
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Auction" must {

    "notify buyer bid was too low" in {
      // given
      auction ! StartAuction(timerConf, auctionConf)

      // when
      val offer = Offer("#-offer-0", BigDecimal(50))
      auction.tell(Bid(offer), buyer.ref)

      // then
      buyer.expectMsg(BidTooLow(offer, BigDecimal(100)))
    }

    "notify buyer about accepted offer" in {
      // given
      auction ! StartAuction(timerConf, auctionConf)

      // when
      val offer = Offer("#-offer-0", BigDecimal(100))
      auction.tell(Bid(offer), buyer.ref)

      // then
      buyer.expectMsg(BidAccepted(offer))
    }

    "notify buyer about successful bid offer" in {
      // given
      auction ! StartAuction(timerConf, auctionConf)
      val anotherBuyer = TestProbe()
      auction.tell(Bid(Offer("#-offer-0", BigDecimal(150))), anotherBuyer.ref)

      // when
      val offer = Offer("#-offer-0", BigDecimal(170))
      auction.tell(Bid(offer), buyer.ref)

      // then
      buyer.expectMsg(BidAccepted(offer))
    }

    "notify buyer about failure bid offer" in {
      // given
      auction ! StartAuction(timerConf, auctionConf)
      val anotherBuyer = TestProbe()
      auction.tell(Bid(Offer("#-offer-0", BigDecimal(150))), anotherBuyer.ref)

      // when
      val offer = Offer("#-offer-1", BigDecimal(140))
      auction.tell(Bid(offer), buyer.ref)

      // then
      buyer.expectMsg(BidTooLow(offer, BigDecimal(170)))
    }

    "notify buyer when its offer has been topped" in {
      // given
      auction ! StartAuction(timerConf, auctionConf)
      val offer = Offer("#-offer-1", BigDecimal(140))
      auction.tell(Bid(offer), buyer.ref)

      // when
      val anotherBuyer = TestProbe()
      auction.tell(Bid(Offer("#-offer-won", BigDecimal(180))), anotherBuyer.ref)

      // then
      buyer.expectMsg(BidAccepted(offer))
      buyer.expectMsg(BidTopped(offer, BigDecimal(200)))
    }

    "notify winner when auction ends" in {
      // given
      auction ! StartAuction(timerConf, auctionConf)
      val offer = Offer("#-offer-1", BigDecimal(140))
      auction.tell(Bid(offer), buyer.ref)

      // when
      auction ! BidTimerExpired

      // then
      buyer.expectMsgAllOf(
        BidAccepted(offer), AuctionWon(offer)
      )
    }

    "notify seller about winner" in {
      // given
      val seller = TestProbe()
      auction.tell(StartAuction(timerConf, auctionConf), seller.ref)
      val offer = Offer("#-offer-1", BigDecimal(140))
      auction.tell(Bid(offer), buyer.ref)

      // when
      auction ! BidTimerExpired

      // then
      seller.expectMsg(AuctionWonBy(offer, buyer.ref))
    }

    "notify seller about auction end when no offer was provided" in {
      // given
      val seller = TestProbe()
      auction.tell(StartAuction(timerConf, auctionConf), seller.ref)

      // when
      auction ! BidTimerExpired

      // then
      seller.expectMsg(AuctionWithoutOfferFinished)
    }

  }

}
