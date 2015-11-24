package pl.edu.agh.ecommerce

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import pl.edu.agh.ecommerce.Initializer.Initialize

object Application extends App {
  implicit val config = ConfigFactory.load()
  val auctionSystem = ActorSystem("auction-system", config.getConfig("auction-system").withFallback(config))
  val auctionPublisher = ActorSystem("auction-publisher", config.getConfig("auction-publisher").withFallback(config))

  auctionSystem.actorOf(AuctionSearch.props(), "auction-search")
  auctionPublisher.actorOf(Publisher.props(), "publisher")

  val initializer = auctionSystem.actorOf(Initializer.props())
  initializer ! Initialize
}
