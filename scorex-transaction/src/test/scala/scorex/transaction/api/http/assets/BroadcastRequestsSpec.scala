package scorex.transaction.api.http.assets

import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.Json
import scorex.api.http.assets.BroadcastRequests.AssetIssueRequest


class BroadcastRequestsSpec extends FunSuite with Matchers {

  test("AssetIssueRequest json parsing works") {
    val json =
      """
        {
          "name": "string",
          "quantity": 1000000,
          "timestamp": 1234,
          "description": "string",
          "signature": "string",
          "senderPublicKey": "string",
          "decimals": 2,
          "reissuable": false,
          "fee": 0
        }
      """
    val req = Json.parse(json).validate[AssetIssueRequest].get
    req.name shouldBe "string"
    req.quantity shouldBe 1000000L
    req.fee shouldBe 0L
    req.decimals shouldBe 2
    req.timestamp shouldBe 1234L
    req.reissuable shouldBe false
  }
}
