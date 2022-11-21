package com.goodwy.dialer.helpers

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import com.behaviorule.arturdumchev.library.*
import com.goodwy.dialer.R
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import kotlinx.android.synthetic.main.activity_call_history.view.*
import kotlinx.android.synthetic.main.top_view.view.*

class CallHistoryTopBehavior(
    context: Context?,
    attrs: AttributeSet?
) : BehaviorByRules(context, attrs) {

    override fun calcAppbarHeight(child: View): Int = with(child) {
        return height
    }

    override fun View.provideAppbar(): AppBarLayout = call_history_appbar
    override fun View.provideCollapsingToolbar(): CollapsingToolbarLayout = collapsing_toolbar
    override fun canUpdateHeight(progress: Float): Boolean = progress >= GONE_VIEW_THRESHOLD

    override fun View.setUpViews(): List<RuledView> {
        val height = height
        return listOf(
            RuledView(
                top_details,
                BRuleYOffset(
                    min = -(height/4).toFloat(),
                    max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                )
            ),
            RuledView(
                call_history_image,
                BRuleXOffset(
                    min = 0f, max = pixels(R.dimen.image_right_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = pixels(R.dimen.zero), max = pixels(R.dimen.image_top_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleScale(min = 0.5f, max = 1f)
            ),
            RuledView(
                call_history_name,
                BRuleXOffset(
                    min = 0f, max = pixels(R.dimen.name_right_margin),
                    interpolator = ReverseInterpolator(LinearInterpolator())
                ),
                BRuleYOffset(
                    min = -pixels(R.dimen.name_top_margin), max = pixels(R.dimen.zero),
                    interpolator = LinearInterpolator()
                ),
                BRuleScale(min = 0.8f, max = 1f)
            )
        )
    }

    private fun actionBarSize(context: Context?): Float {
        val styledAttributes = context!!.theme?.obtainStyledAttributes(IntArray(1) { android.R.attr.actionBarSize })
        val actionBarSize = styledAttributes?.getDimension(0, 0F)
        styledAttributes?.recycle()
        return actionBarSize ?: context.pixels(R.dimen.toolbar_height)
    }

    private fun getScreenWidth(): Int {
        return Resources.getSystem().displayMetrics.widthPixels
    }


    companion object {
        const val GONE_VIEW_THRESHOLD = 0.8f
    }
}
