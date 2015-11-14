package scorex.perma.actors

import akka.actor.Actor
import scorex.perma.merkle.CryptographicHash

case class Segment(id:Int, data:Array[Byte], merklePath: Seq[CryptographicHash.Digest])

class TrustedDealer extends Actor {

  override def receive = {
    case _ =>
  }

}

object TrustedDealerSpec {

  case object PublishDataset

  case class SendOutSegments(segments:Array[Int])
}
