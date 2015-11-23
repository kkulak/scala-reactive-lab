package pl.edu.agh.ecommerce

import akka.actor._
import akka.event.LoggingReceive
import pl.edu.agh.ecommerce.Notifier.Notification

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class NotifierRequest(publisherFactory: ActorRefFactory => ActorSelection,
                      payload: Notification) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    log.debug(s"Trying to send payload: $payload")
    sendNotificationOrThrowException()
  }

  override def receive: Receive = LoggingReceive {
    case e: Exception => throw e
  }

  private def sendNotificationOrThrowException(): Unit = {
    val eventualActorRef = publisherFactory(context).resolveOne(3 seconds)
    eventualActorRef.onSuccess(sendNotificationAndPoisonPill)
    eventualActorRef.onFailure(rethrowException)
  }

  def sendNotificationAndPoisonPill: PartialFunction[ActorRef, Unit] = {
    case ref: ActorRef =>
      ref ! payload
      context.stop(self)
  }

  def rethrowException: PartialFunction[Throwable, Unit] = {
    case ex: Throwable => self ! ex
  }

}

case object NotifierRequest {

  def props(publisherFactory: ActorRefFactory => ActorSelection, payload: Notification): Props =
    Props(classOf[NotifierRequest], publisherFactory, payload)

}
