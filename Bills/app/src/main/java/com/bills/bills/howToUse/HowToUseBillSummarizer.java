package com.bills.bills.howToUse;

import android.app.Activity;
import android.content.Context;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.bills.bills.R;
import com.bills.bills.fragments.BillSummarizerFragment;
import com.bills.billslib.Contracts.Constants;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.targets.Target;
import com.github.amlcurran.showcaseview.targets.ViewTarget;

/**
 * Created by aviel on 2/24/18.
 */

public class HowToUseBillSummarizer {
    private final String PREFS_SHOWCASE_INTERNAL = "showcase_internal";
    private Target mViewTarget;
    private Activity mActivity;
    private ShowcaseView mShowcaseView;
    private Integer mCounter = 0;
    private onFinishedListener mListener;
    private ViewGroup mWorkOnView;

    public HowToUseBillSummarizer(Activity activity, onFinishedListener listener, ViewGroup workOnView){
        mActivity = activity;
        mListener = listener;
        mWorkOnView = workOnView;
    }

    public void SetShowcaseViewBillSummarizer() {
        if(mActivity
                .getSharedPreferences(PREFS_SHOWCASE_INTERNAL, Context.MODE_PRIVATE)
                .getBoolean("hasShot" + Constants.SHOT_ID_BILL_SUMMARIZER, false)){
            mListener.finished();
            return;
        }
        ViewEnablement.DisableView(mWorkOnView);
        mViewTarget = new ViewTarget(R.id.common_bill_heading, mActivity);
        mShowcaseView = new ShowcaseView.Builder(mActivity)
                .setTarget(mViewTarget)
                .setOnClickListener(onClickListener)
                .setContentTitle(R.string.common_bill_heading_text_view_title)
                .setStyle(R.style.CustomShowcaseTheme)
                .setContentText(R.string.common_bill_heading_text_view_description)
                .singleShot(Constants.SHOT_ID_BILL_SUMMARIZER)
                .build();
        mShowcaseView.setButtonText(mActivity.getString(R.string.next_desc));

        //set margin of next_desc button
        RelativeLayout.LayoutParams lps = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lps.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        lps.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        int margin = ((Number) (mActivity.getResources().getDisplayMetrics().density * 18)).intValue();
        lps.setMargins(margin, margin, margin, margin);
        mShowcaseView.setButtonPosition(lps);
        mShowcaseView.setButtonPosition(lps);
    }

    View.OnClickListener onClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            switch (mCounter) {
                case 0:
                    mViewTarget = new ViewTarget(R.id.my_bill_heading, mActivity);
                    mShowcaseView.setShowcase(mViewTarget, true);
                    mShowcaseView.setContentTitle(mActivity.getString(R.string.my_bill_heading_text_view_title));
                    mShowcaseView.setContentText(mActivity.getString(R.string.my_bill_heading_text_view_description));
                    mShowcaseView.setButtonText(mActivity.getString(R.string.next_desc));
                    break;
                case 1:
                    mViewTarget = new ViewTarget(R.id.tip_heading, mActivity);
                    mShowcaseView.setShowcase(mViewTarget, true);
                    mShowcaseView.setContentTitle(mActivity.getString(R.string.tip_heading_text_view_title));
                    mShowcaseView.setContentText(mActivity.getString(R.string.tip_heading_text_view_description));
                    mShowcaseView.setButtonText(mActivity.getString(R.string.close));
                    mShowcaseView.forceTextPosition(ShowcaseView.ABOVE_SHOWCASE);
                    break;
                case 2:
                    mShowcaseView.hide();
                    ViewEnablement.EnableView(mWorkOnView);
                    mListener.finished();
                    break;
            }
            mCounter++;
        }
    };

    public interface onFinishedListener{
        void finished();
    }
}
