package pl.edu.agh.ecommerce

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}
import pl.edu.agh.ecommerce.AuctionSearch.{Deregister, QueryResult, Register, Search}

class AuctionSearchSpec extends TestKit(ActorSystem("AuctionSearchSpec"))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfterEach with ImplicitSender {

  var auctionSearch: ActorRef = _

  override protected def beforeEach(): Unit = {
    auctionSearch = system.actorOf(Props[AuctionSearch])
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "AuctionSearch" must {

    "respond with empty list of auctions if there's not match" in {
      // when
      auctionSearch ! Search("keyword")

      // then
      expectMsg(QueryResult(Set()))
    }

    "respond with valid list of auctions on query" in {
      // given
      val firstActor = system.actorOf(Auction.props())
      val secondActor = system.actorOf(Auction.props())

      auctionSearch ! Register(firstActor, "Audi A6")
      auctionSearch ! Register(secondActor, "Audi A8")
      auctionSearch ! Register(ActorRef.noSender, "BMW 520d")

      // when
      auctionSearch ! Search("Audi")

      // then
      expectMsg(QueryResult(Set(firstActor, secondActor)))
    }

    "respond with valid list of auctions after unregistering some of them" in {
      // given
      val firstActor = system.actorOf(Auction.props())
      val secondActor = system.actorOf(Auction.props())

      auctionSearch ! Register(firstActor, "Audi A6")
      auctionSearch ! Register(secondActor, "Audi A8")
      auctionSearch ! Register(ActorRef.noSender, "BMW 520d")
      auctionSearch ! Deregister(secondActor)

      // when
      auctionSearch ! Search("Audi")

      // then
      expectMsg(QueryResult(Set(firstActor)))
    }

  }

}
