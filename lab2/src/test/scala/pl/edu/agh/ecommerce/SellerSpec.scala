package pl.edu.agh.ecommerce

import akka.actor._
import akka.testkit.{ImplicitSender, TestActors, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}
import pl.edu.agh.ecommerce.Auction.{AuctionConf, AuctionWithoutOfferFinished, AuctionWonBy, TimerConf}
import pl.edu.agh.ecommerce.AuctionSearch.{Deregister, Register}
import pl.edu.agh.ecommerce.Seller.RegisterAndStartAuction

import scala.concurrent.duration._

class SellerSpec extends TestKit(ActorSystem("SellerSpec"))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfterEach with ImplicitSender{

  var seller: ActorRef = _
  var title: String = _
  var timerConf: TimerConf = _
  var auctionConf: AuctionConf = _
  var auctionSearch: TestProbe = _
  var auction: TestProbe = _

  override protected def beforeEach(): Unit = {
    val customActorSystem = ActorSystem("test-actor-system")

    title = "Some title"
    timerConf = TimerConf(bidTimerTimeout = 10 seconds, deleteTimerTimeout = 5 seconds)
    auctionConf = AuctionConf(initialPrice = BigDecimal(100), bidStep = BigDecimal(10))
    auctionSearch = new TestProbe(customActorSystem)
    auction = new TestProbe(customActorSystem)

    val auctionFactory = (f: ActorRefFactory) => auction.ref
    seller = customActorSystem.actorOf(Props(classOf[Seller], auctionFactory))
    customActorSystem.actorOf(TestActors.forwardActorProps(auctionSearch.ref), "auction-search")
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Seller" must {

    "register actor in auction search registry" in {
      // when
      seller ! RegisterAndStartAuction(title, timerConf, auctionConf)

      // then
      auctionSearch.expectMsg(Register(auction.ref, title))
    }

    "deregister actor in auction search registry given aution with winner" in {
      // given
      val buyer = TestProbe()
      seller ! RegisterAndStartAuction(title, timerConf, auctionConf)

      // when
      seller.tell(AuctionWonBy(null, buyer.ref), auction.ref)

      // then
      auctionSearch.expectMsgAllOf(
        Register(auction.ref, title),
        Deregister(auction.ref)
      )
    }

    "deregister actor in auction search registry given auction without winner" in {
      // given
      seller ! RegisterAndStartAuction(title, timerConf, auctionConf)

      // when
      seller.tell(AuctionWithoutOfferFinished, auction.ref)

      // then
      auctionSearch.expectMsgAllOf(
        Register(auction.ref, title),
        Deregister(auction.ref)
      )
    }

  }

}
