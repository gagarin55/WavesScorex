package scorex.api.http

import javax.ws.rs.Path

import akka.actor.ActorRefFactory
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import play.api.libs.json.{JsError, JsSuccess, Json}
import scorex.account.Account
import scorex.app.RunnableApplication
import scorex.crypto.encode.Base58
import scorex.transaction.assets.{IssueTransaction, ReissueTransaction, TransferTransaction}
import scorex.transaction.state.database.blockchain.StoredState
import scorex.transaction.state.wallet.{IssueRequest, ReissueRequest, TransferRequest}
import scorex.transaction.{AssetAcc, SimpleTransactionModule, StateCheckFailed, ValidationResult}

import scala.util.{Failure, Success, Try}


@Path("/assets")
@Api(value = "/assets/")
case class AssetsApiRoute(application: RunnableApplication)(implicit val context: ActorRefFactory)
  extends ApiRoute with CommonTransactionApiFunctions {
  val MaxAddressesPerRequest = 1000

  val settings = application.settings

  private val wallet = application.wallet
  private val state = application.blockStorage.state.asInstanceOf[StoredState]
  private implicit val transactionModule = application.transactionModule.asInstanceOf[SimpleTransactionModule]

  override lazy val route =
    pathPrefix("assets") {
      balance ~ issue ~ reissue ~ transfer
    }

  @Path("/balance/{address}/{assetId}")
  @ApiOperation(value = "Balance", notes = "Account's balance", httpMethod = "GET")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "address", value = "Address", required = true, dataType = "string", paramType = "path"),
    new ApiImplicitParam(name = "assetId", value = "Asset ID", required = true, dataType = "string", paramType = "path")
  ))
  def balance: Route = {
    path("balance" / Segment / Segment) { case (address, assetId) =>
      getJsonRoute {
        balanceJson(address, assetId)
      }
    }
  }

  @Path("/transfer")
  @ApiOperation(value = "Transfer asset",
    notes = "Transfer asset to new address",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.transaction.state.wallet.TransferRequest",
      defaultValue = "\"sender\":\"3Mn6xomsZZepJj1GL1QaW6CaCJAq8B3oPef\",\"recipient\":\"3Mciuup51AxRrpSz7XhutnQYTkNT9691HAk\",\"assetId\":null,\"amount\":5813874260609385500,\"feeAsset\":\"3Z7T9SwMbcBuZgcn3mGu7MMp619CTgSWBT7wvEkPwYXGnoYzLeTyh3EqZu1ibUhbUHAsGK5tdv9vJL9pk4fzv9Gc\",\"fee\":1579331567487095949,\"timestamp\":4231642878298810008}"
    )
  ))
  def transfer: Route = path("transfer") {
    entity(as[String]) { body =>
      withAuth {
        postJsonRoute {
          walletNotExists(wallet).getOrElse {
            Try(Json.parse(body)).map { js =>
              js.validate[TransferRequest] match {
                case err: JsError =>
                  WrongTransactionJson(err).response
                case JsSuccess(request: TransferRequest, _) =>
                  val txOpt: Try[TransferTransaction] = transactionModule.transferAsset(request, wallet)
                  txOpt match {
                    case Success(tx) =>
                      JsonResponse(tx.json, StatusCodes.OK)
                    case Failure(e: StateCheckFailed) =>
                      StateCheckFailed.response
                    case _ =>
                      WrongJson.response
                  }
              }
            }.getOrElse(WrongJson.response)
          }
        }
      }
    }
  }

  @Path("/issue")
  @ApiOperation(value = "Issue asset",
    notes = "Issue new asset (or reissue the old one)",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.transaction.state.wallet.IssueRequest",
      defaultValue = "{\"sender\":\"string\",\"name\":\"str\",\"description\":\"string\",\"quantity\":100000,\"decimals\":7,\"reissuable\":false,\"fee\":100000000}"
    )
  ))
  def issue: Route = path("issue") {
    entity(as[String]) { body =>
      withAuth {
        postJsonRoute {
          walletNotExists(wallet).getOrElse {
            Try(Json.parse(body)).map { js =>
              js.validate[IssueRequest] match {
                case err: JsError =>
                  WrongTransactionJson(err).response
                case JsSuccess(issue: IssueRequest, _) =>
                  val txOpt: Try[IssueTransaction] = transactionModule.issueAsset(issue, wallet)
                  txOpt match {
                    case Success(tx) =>
                      tx.validate match {
                        case ValidationResult.ValidateOke =>
                          JsonResponse(tx.json, StatusCodes.OK)

                        case ValidationResult.InvalidAddress =>
                          InvalidAddress.response

                        case ValidationResult.NegativeAmount =>
                          InvalidAmount.response

                        case ValidationResult.InsufficientFee =>
                          InsufficientFee.response

                        case ValidationResult.InvalidName =>
                          InvalidName.response

                        case ValidationResult.InvalidSignature =>
                          InvalidSignature.response

                        case ValidationResult.TooBigArray =>
                          TooBigArrayAllocation.response
                      }
                    case Failure(e: StateCheckFailed) =>
                      StateCheckFailed.response
                    case _ =>
                      WrongJson.response
                  }
              }
            }.getOrElse(WrongJson.response)
          }
        }
      }
    }
  }

  @Path("/reissue")
  @ApiOperation(value = "Issue asset",
    notes = "Reissue asset (or reissue the old one)",
    httpMethod = "POST",
    produces = "application/json",
    consumes = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(
      name = "body",
      value = "Json with data",
      required = true,
      paramType = "body",
      dataType = "scorex.transaction.state.wallet.ReissueRequest",
      defaultValue = "{\"sender\":\"string\",\"assetId\":\"Base58\",\"quantity\":100000,\"reissuable\":false,\"fee\":1}"
    )
  ))
  def reissue: Route = path("reissue") {
    entity(as[String]) { body =>
      withAuth {
        postJsonRoute {
          walletNotExists(wallet).getOrElse {
            Try(Json.parse(body)).map { js =>
              js.validate[ReissueRequest] match {
                case err: JsError =>
                  WrongTransactionJson(err).response
                case JsSuccess(issue: ReissueRequest, _) =>
                  val txOpt: Try[ReissueTransaction] = transactionModule.reissueAsset(issue, wallet)
                  txOpt match {
                    case Success(tx) =>
                      tx.validate match {
                        case ValidationResult.ValidateOke =>
                          JsonResponse(tx.json, StatusCodes.OK)

                        case ValidationResult.InvalidAddress =>
                          InvalidAddress.response

                        case ValidationResult.NegativeAmount =>
                          InvalidAmount.response

                        case ValidationResult.InsufficientFee =>
                          InsufficientFee.response

                        case ValidationResult.InvalidSignature =>
                          InvalidSignature.response
                      }
                    case Failure(e: StateCheckFailed) =>
                      StateCheckFailed.response
                    case _ =>
                      WrongJson.response
                  }
              }
            }.getOrElse(WrongJson.response)
          }
        }
      }
    }
  }


  private def balanceJson(address: String, assetIdStr: String): JsonResponse = {
    val account = new Account(address)
    Base58.decode(assetIdStr) match {
      case Success(assetId) if Account.isValid(account) =>
        val json = Json.obj(
          "address" -> account.address,
          "assetId" -> assetIdStr,
          "balance" -> state.assetBalance(AssetAcc(account, Some(assetId)))
        )
        JsonResponse(json, StatusCodes.OK)
      case _ => InvalidAddress.response
    }
  }

}
