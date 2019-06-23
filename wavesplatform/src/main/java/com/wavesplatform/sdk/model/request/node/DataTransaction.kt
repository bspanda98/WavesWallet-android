package com.wavesplatform.sdk.model.request.node

import android.util.Log
import com.google.common.primitives.Bytes
import com.google.common.primitives.Longs
import com.google.gson.annotations.SerializedName
import com.wavesplatform.sdk.crypto.Base58

class DataTransaction(
        @SerializedName("data") var data: ByteArray)
    : BaseTransaction(CREATE_LEASING) {

    override fun toBytes(): ByteArray {
        if (data.size < 1024 * 140) {
            return try {
                Bytes.concat(byteArrayOf(type.toByte()),
                        byteArrayOf(version.toByte()),
                        Base58.decode(senderPublicKey),
                        data,
                        Longs.toByteArray(timestamp),
                        Longs.toByteArray(fee))
            } catch (e: Exception) {
                Log.e("Sign", "Can't create bytes for sign in Data Transaction", e)
                ByteArray(0)
            }
        } else {
            Log.e("Sign", "Can't create bytes for sign in Data Transaction, data > 140kb")
            return ByteArray(0)
        }
    }
}