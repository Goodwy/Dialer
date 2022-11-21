package com.goodwy.dialer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.goodwy.commons.adapters.MyRecyclerViewAdapter
import com.goodwy.commons.extensions.getProperPrimaryColor
import com.goodwy.commons.extensions.getProperTextColor
import com.goodwy.dialer.activities.SimpleActivity
import com.goodwy.dialer.extensions.config
import com.goodwy.dialer.helpers.Config
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import kotlinx.android.synthetic.main.fragment_recents.view.*

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    protected var activity: SimpleActivity? = null

    private lateinit var config: Config

    fun setupFragment(activity: SimpleActivity) {
        config = activity.config
        if (this.activity == null) {
            this.activity = activity

            setupFragment()
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor(), activity.getProperPrimaryColor())
        }
    }

    fun finishActMode() {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
        (recents_list?.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    abstract fun setupFragment()

    abstract fun setupColors(textColor: Int, primaryColor: Int, properPrimaryColor: Int)

    abstract fun onSearchClosed()

    abstract fun onSearchQueryChanged(text: String)
}
