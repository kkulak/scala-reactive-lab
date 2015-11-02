package pl.edu.agh.ecommerce

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, WordSpecLike}

class SellerSpec extends TestKit(ActorSystem("SellerSpec"))
  with WordSpecLike with BeforeAndAfterAll with BeforeAndAfterEach with ImplicitSender{

  var seller: ActorRef = _

  override protected def beforeEach(): Unit = {
    seller = system.actorOf(Props[Seller])
  }

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Seller" must {



  }

}
