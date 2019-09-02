package com.wavesplatform.wallet.v2.ui.keeper

import com.arellomobile.mvp.InjectViewState
import com.vicpin.krealmextensions.queryAll
import com.wavesplatform.sdk.WavesSdk
import com.wavesplatform.sdk.keeper.interfaces.KeeperTransaction
import com.wavesplatform.sdk.model.request.node.BaseTransaction
import com.wavesplatform.sdk.model.request.node.DataTransaction
import com.wavesplatform.sdk.model.request.node.InvokeScriptTransaction
import com.wavesplatform.sdk.model.request.node.TransferTransaction
import com.wavesplatform.sdk.model.response.node.AssetsDetailsResponse
import com.wavesplatform.sdk.model.response.node.ScriptInfoResponse
import com.wavesplatform.wallet.App
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.data.model.db.SpamAssetDb
import com.wavesplatform.wallet.v2.data.model.service.cofigs.GlobalTransactionCommissionResponse
import com.wavesplatform.wallet.v2.ui.base.presenter.BasePresenter
import com.wavesplatform.wallet.v2.util.TransactionCommissionUtil
import com.wavesplatform.wallet.v2.util.executeInBackground
import io.reactivex.Observable
import javax.inject.Inject

@InjectViewState
class KeeperTransactionPresenter @Inject constructor() : BasePresenter<KeeperTransactionView>() {

    var fee = 0L
    lateinit var transaction: KeeperTransaction

    fun receiveTransactionData(transaction: KeeperTransaction, address: String) {
        fee = 0L
        this.transaction = transaction

        var bytesCount = 0
        var dAppAddress = ""
        var type = 0.toByte()

        var observable: Observable<AssetsDetailsResponse>? = null

        when (transaction) {
            is TransferTransaction -> {
                type = transaction.type
                observable = nodeServiceManager.assetDetails(transaction.assetId)
            }
            is DataTransaction -> {
                bytesCount = transaction.getDataSize()
                type = transaction.type
                observable = Observable.empty()
            }
            is InvokeScriptTransaction -> {
                type = transaction.type
                transaction.payment.forEach { paymentItem ->
                    observable = if (observable == null) {
                        nodeServiceManager.assetDetails(paymentItem.assetId)
                    } else {
                        observable!!.mergeWith(nodeServiceManager.assetDetails(paymentItem.assetId))
                    }
                }
                dAppAddress = transaction.dApp
            }
        }

        val assetDetails = hashMapOf<String, AssetsDetailsResponse>()
        var commission: GlobalTransactionCommissionResponse? = null
        var scriptInfo: ScriptInfoResponse? = null

        addSubscription(Observable.zip(
                githubServiceManager.getGlobalCommission(),
                nodeServiceManager.scriptAddressInfo(address),
                io.reactivex.functions.BiFunction { commission: GlobalTransactionCommissionResponse,
                                                    scriptInfoResponse: ScriptInfoResponse ->
                    return@BiFunction Pair(commission, scriptInfoResponse)
                })
                .flatMap {
                    commission = it.first
                    scriptInfo = it.second
                    observable!!
                }
                .executeInBackground()
                .subscribe({
                    assetDetails[it.assetId] = it
                }, {
                    it.printStackTrace()
                    viewState.onError(it)
                }, {
                    val params = GlobalTransactionCommissionResponse.ParamsResponse()
                    params.transactionType = type
                    params.smartAccount = scriptInfo!!.extraFee != 0L
                    when (type) {
                        BaseTransaction.TRANSFER -> {
                            val assetDetail = assetDetails.values.toList()
                            if (assetDetail.isNotEmpty()) {
                                params.smartAsset = assetDetail[0].scripted
                            }
                        }
                        BaseTransaction.DATA -> {
                            params.bytesCount = bytesCount
                        }
                    }
                    fee = TransactionCommissionUtil.countCommission(commission!!, params)
                    viewState.onReceiveTransactionData(
                            type, transaction, fee, dAppAddress, assetDetails)
                }))
    }

    fun signTransaction(transaction: KeeperTransaction) {
        when (transaction) {
            is TransferTransaction -> {
                transaction.sign(App.getAccessManager().getWallet()?.seedStr ?: "")
                viewState.onSuccessSign(transaction)
            }
            is DataTransaction -> {
                transaction.sign(App.getAccessManager().getWallet()?.seedStr ?: "")
                viewState.onSuccessSign(transaction)
            }
            is InvokeScriptTransaction -> {
                transaction.sign(App.getAccessManager().getWallet()?.seedStr ?: "")
                viewState.onSuccessSign(transaction)
            }
            else -> {
                viewState.onError(
                        Throwable(App.getAppContext().getString(R.string.common_server_error)))
            }
        }
    }
}
