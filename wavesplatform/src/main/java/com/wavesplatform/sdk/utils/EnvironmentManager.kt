/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.sdk.utils

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.preference.PreferenceManager
import android.text.TextUtils
import android.util.Log
import com.google.gson.Gson
import com.wavesplatform.sdk.net.model.response.AssetBalanceResponse
import com.wavesplatform.sdk.net.model.response.GlobalConfigurationResponse
import com.wavesplatform.sdk.net.model.response.IssueTransactionResponse
import com.wavesplatform.sdk.net.service.ApiService
import com.wavesplatform.sdk.net.HostSelectionInterceptor
import com.wavesplatform.sdk.net.service.NodeService
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import pers.victor.ext.currentTimeMillis
import java.io.IOException
import java.nio.charset.Charset

class EnvironmentManager {

    var current: Environment? = null
    private var application: Application? = null
    private var configurationDisposable: Disposable? = null
    private var timeDisposable: Disposable? = null
    private var versionDisposable: Disposable? = null
    private var interceptor: HostSelectionInterceptor? = null

    class Environment internal constructor(val name: String, val url: String, jsonFileName: String) {
        var configuration: GlobalConfigurationResponse? = null

        init {
            this.configuration = Gson().fromJson(
                    loadJsonFromAsset(instance!!.application!!, jsonFileName),
                    GlobalConfigurationResponse::class.java)
        }

        internal fun setConfiguration(configuration: GlobalConfigurationResponse) {
            this.configuration = configuration
        }

        companion object {

            internal var environments: MutableList<Environment> = mutableListOf()
            var TEST_NET = Environment(KEY_ENV_TEST_NET, URL_CONFIG_TEST_NET, FILENAME_TEST_NET)
            var MAIN_NET = Environment(KEY_ENV_MAIN_NET, URL_CONFIG_MAIN_NET, FILENAME_MAIN_NET)

            init {
                environments.add(TEST_NET)
                environments.add(MAIN_NET)
            }
        }
    }

    companion object {
        private const val BRANCH = "mobile/v2.3"

        const val KEY_ENV_TEST_NET = "env_testnet"
        const val KEY_ENV_MAIN_NET = "env_prod"

        const val FILENAME_TEST_NET = "environment_testnet.json"
        const val FILENAME_MAIN_NET = "environment_mainnet.json"

        const val URL_CONFIG_MAIN_NET = "https://github-proxy.wvservices.com/" +
                "wavesplatform/waves-client-config/$BRANCH/environment_mainnet.json"
        const val URL_CONFIG_TEST_NET = "https://github-proxy.wvservices.com/" +
                "wavesplatform/waves-client-config/$BRANCH/environment_testnet.json"
        const val URL_COMMISSION_MAIN_NET = "https://github-proxy.wvservices.com/" +
                "wavesplatform/waves-client-config/$BRANCH/fee.json"

        private var instance: EnvironmentManager? = null
        private val handler = Handler()

        private const val GLOBAL_CURRENT_ENVIRONMENT_DATA = "global_current_environment_data"
        private const val GLOBAL_CURRENT_ENVIRONMENT = "global_current_environment"
        private const val GLOBAL_CURRENT_TIME_CORRECTION = "global_current_time_correction"

        @JvmStatic
        fun init(application: Application) {
            instance = EnvironmentManager()
            instance!!.application = application
            val envName = environmentName
            if (!TextUtils.isEmpty(envName)) {
                for (environment in Environment.environments) {
                    if (envName!!.equals(environment.name, ignoreCase = true)) {
                        val preferenceManager = PreferenceManager
                                .getDefaultSharedPreferences(instance!!.application)
                        if (preferenceManager.contains(GLOBAL_CURRENT_ENVIRONMENT_DATA)) {
                            val json = preferenceManager.getString(
                                    GLOBAL_CURRENT_ENVIRONMENT_DATA,
                                    Gson().toJson(environment.configuration))
                            environment.setConfiguration(Gson()
                                    .fromJson(json, GlobalConfigurationResponse::class.java))
                        } else {
                            environment.setConfiguration(environment.configuration!!)
                        }
                        instance!!.current = environment
                        break
                    }
                }
            }
        }

        fun createHostInterceptor(): HostSelectionInterceptor {
            instance!!.interceptor = HostSelectionInterceptor(servers)
            return instance!!.interceptor!!
        }

        @JvmStatic
        fun updateConfiguration(globalConfigurationObserver: Observable<GlobalConfigurationResponse>,
                                apiService: ApiService,
                                nodeService: NodeService) {
            if (instance == null) {
                throw NullPointerException("EnvironmentManager must be init first!")
            }

            instance!!.configurationDisposable = globalConfigurationObserver
                    .map { globalConfiguration ->
                        setConfiguration(globalConfiguration)
                        globalConfiguration.generalAssets.map { it.assetId }
                    }
                    .flatMap { apiService.assetsInfoByIds(it) }
                    .map { info ->
                        defaultAssets.clear()
                        for (assetInfo in info.data) {
                            val assetBalance = AssetBalanceResponse(
                                    assetId = if (assetInfo.assetInfo.id == Constants.WAVES_ASSET_ID_FILLED) {
                                        Constants.WAVES_ASSET_ID_EMPTY
                                    } else {
                                        assetInfo.assetInfo.id
                                    },
                                    quantity = assetInfo.assetInfo.quantity,
                                    isFavorite = assetInfo.assetInfo.id == Constants.WAVES_ASSET_ID_FILLED,
                                    issueTransaction = IssueTransactionResponse(
                                            id = assetInfo.assetInfo.id,
                                            assetId = assetInfo.assetInfo.id,
                                            name = findAssetIdByAssetId(
                                                    assetInfo.assetInfo.id)?.displayName
                                                    ?: assetInfo.assetInfo.name,
                                            decimals = assetInfo.assetInfo.precision,
                                            quantity = assetInfo.assetInfo.quantity,
                                            description = assetInfo.assetInfo.description,
                                            sender = assetInfo.assetInfo.sender,
                                            timestamp = assetInfo.assetInfo.timestamp.time),
                                    isGateway = findAssetIdByAssetId(
                                            assetInfo.assetInfo.id)?.isGateway ?: false,
                                    isFiatMoney = findAssetIdByAssetId(
                                            assetInfo.assetInfo.id)?.isFiat ?: false)
                            defaultAssets.add(assetBalance)
                        }
                    }
                    .compose(RxUtil.applyObservableDefaultSchedulers())
                    .subscribe({
                        instance!!.configurationDisposable!!.dispose()
                    }, { error ->
                        Log.e("EnvironmentManager", "Can't download GlobalConfigurationResponse!")
                        error.printStackTrace()
                        setConfiguration(environment.configuration!!)
                        instance!!.configurationDisposable!!.dispose()
                    })

            instance!!.timeDisposable = nodeService.utilsTime()
                    .compose(RxUtil.applyObservableDefaultSchedulers())
                    .subscribe({
                        val timeCorrection = it.ntp - currentTimeMillis
                        if (Math.abs(timeCorrection) > 30_000) {
                            PreferenceManager
                                    .getDefaultSharedPreferences(instance!!.application)
                                    .edit()
                                    .putLong(GLOBAL_CURRENT_TIME_CORRECTION,
                                            timeCorrection)
                                    .apply()
                        }
                        instance!!.timeDisposable!!.dispose()
                    }, { error ->
                        Log.e("EnvironmentManager", "Can't download time correction!")
                        error.printStackTrace()
                        instance!!.timeDisposable!!.dispose()
                    })

            instance!!.versionDisposable = githubDataManager.loadLastAppVersion()
                    .compose(RxUtil.applyObservableDefaultSchedulers())
                    .subscribe({ version ->
                        githubDataManager.preferencesHelper.lastAppVersion = version.lastVersion
                        instance!!.versionDisposable!!.dispose()
                    }, { error ->
                        error.printStackTrace()
                        instance!!.timeDisposable!!.dispose()
                    })
        }

        private fun setConfiguration(globalConfiguration: GlobalConfigurationResponse) {
            instance!!.interceptor!!.setHosts(globalConfiguration.servers)
            PreferenceManager
                    .getDefaultSharedPreferences(instance!!.application)
                    .edit()
                    .putString(GLOBAL_CURRENT_ENVIRONMENT_DATA,
                            Gson().toJson(globalConfiguration))
                    .apply()
            instance!!.current!!.setConfiguration(globalConfiguration)
        }

        @JvmStatic
        fun getTime(): Long {
            val timeCorrection = if (instance == null || instance!!.application == null) {
                0L
            } else {
                PreferenceManager
                        .getDefaultSharedPreferences(instance!!.application)
                        .getLong(GLOBAL_CURRENT_TIME_CORRECTION, 0L)
            }
            return currentTimeMillis + timeCorrection
        }

        fun setCurrentEnvironment(current: Environment) {
            PreferenceManager.getDefaultSharedPreferences(instance!!.application)
                    .edit()
                    .putString(GLOBAL_CURRENT_ENVIRONMENT, current.name)
                    .remove(GLOBAL_CURRENT_ENVIRONMENT_DATA)
                    .apply()
            restartApp(instance!!.application!!)
        }

        val environmentName: String?
            get() {
                val preferenceManager = PreferenceManager
                        .getDefaultSharedPreferences(instance!!.application)
                return preferenceManager.getString(
                        GLOBAL_CURRENT_ENVIRONMENT, Environment.MAIN_NET.name)
            }

        private fun findAssetIdByAssetId(assetId: String): GlobalConfigurationResponse.ConfigAsset? {
            return instance?.current?.configuration?.generalAssets?.firstOrNull { it.assetId == assetId }
        }

        private fun loadJsonFromAsset(application: Application, fileName: String): String {
            return try {
                val inputStream = application.assets.open(fileName)
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()
                String(buffer, Charset.defaultCharset())
            } catch (ex: IOException) {
                ex.printStackTrace()
                ""
            }
        }

        val netCode: Byte
            get() = environment.configuration!!.scheme[0].toByte()

        val globalConfiguration: GlobalConfigurationResponse
            get() = environment.configuration!!

        val name: String
            get() = environment.name

        @JvmStatic
        val servers: GlobalConfigurationResponse.Servers
            get() = environment.configuration!!.servers

        val defaultAssets = mutableListOf<AssetBalanceResponse>()

        val environment: Environment
            get() = instance!!.current!!

        private fun restartApp(application: Application) {
            handler.postDelayed({
                val packageManager = application.packageManager
                val intent = packageManager.getLaunchIntentForPackage(application.packageName)
                if (intent != null) {
                    val componentName = intent.component
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    application.startActivity(mainIntent)
                    System.exit(0)
                }
            }, 300)
        }
    }
}