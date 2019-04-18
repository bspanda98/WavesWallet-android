/*
 * Created by Ershov Aleksandr on 9/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.ui.search_asset

import android.os.Bundle
import android.support.v4.view.ViewCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.TextUtils
import android.view.LayoutInflater
import com.arellomobile.mvp.presenter.InjectPresenter
import com.arellomobile.mvp.presenter.ProvidePresenter
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.entity.MultiItemEntity
import com.jakewharton.rxbinding2.widget.RxTextView
import com.wavesplatform.wallet.R
import com.wavesplatform.wallet.v2.data.model.remote.response.AssetBalance
import com.wavesplatform.wallet.v2.ui.base.view.BaseActivity
import com.wavesplatform.wallet.v2.ui.home.wallet.assets.AssetsFragment
import com.wavesplatform.wallet.v2.ui.home.wallet.assets.details.AssetDetailsActivity
import com.wavesplatform.wallet.v2.util.launchActivity
import kotlinx.android.synthetic.main.activity_search_asset.*
import kotlinx.android.synthetic.main.layout_empty_data.view.*
import org.w3c.dom.Text
import pers.victor.ext.click
import pers.victor.ext.gone
import pers.victor.ext.visiable
import javax.inject.Inject

class SearchAssetActivity : BaseActivity(), SearchAssetView {

    @Inject
    @InjectPresenter
    lateinit var presenter: SearchAssetPresenter

    @Inject
    lateinit var adapter: SearchAssetAdapter

    @ProvidePresenter
    fun providePresenter(): SearchAssetPresenter = presenter

    override fun configLayoutRes() = R.layout.activity_search_asset

    override fun onBackPressed() {
        finish()
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onViewReady(savedInstanceState: Bundle?) {
        setStatusBarColor(R.color.basic50)
        setNavigationBarColor(R.color.basic50)
        ViewCompat.setElevation(appbar_layout, 8F)
        clear_button.click {
            search_view.setText("")
            presenter.search("")
        }
        cancel_button.click { onBackPressed() }
        recycle_assets.layoutManager = LinearLayoutManager(this)
        adapter.bindToRecyclerView(recycle_assets)

        val emptyView = LayoutInflater.from(this)
                .inflate(R.layout.address_book_empty_state, null)
        emptyView.text_empty.text = getString(R.string.search_asset_empty)
        adapter.emptyView = emptyView

        adapter.onItemClickListener = BaseQuickAdapter.OnItemClickListener { _, _, position ->
            val item = this.adapter.getItem(position) as AssetBalance
            launchActivity<AssetDetailsActivity>(AssetsFragment.REQUEST_ASSET_DETAILS) {
                putExtra(AssetDetailsActivity.BUNDLE_ASSET_TYPE, item.itemType)
                if (item.isHidden) {
                    putExtra(AssetDetailsActivity.BUNDLE_ASSET_POSITION, position - 1)
                } else {
                    putExtra(AssetDetailsActivity.BUNDLE_ASSET_POSITION, position)
                }
                putExtra(AssetDetailsActivity.BUNDLE_ASSET_SEARCH, search_view.text.toString())
            }
        }

        eventSubscriptions.add(RxTextView.textChanges(search_view)
                .subscribe { search ->
                    if (TextUtils.isEmpty(search)) {
                        clear_button.gone()
                    } else {
                        clear_button.visiable()
                    }
                    presenter.search(search.toString())
                })

        presenter.search("")

        search_view.requestFocus()

    }

    override fun setSearchResult(list: List<MultiItemEntity>) {
        adapter.setNewData(list)
    }
}
