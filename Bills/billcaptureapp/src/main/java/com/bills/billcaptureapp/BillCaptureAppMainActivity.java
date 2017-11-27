package com.bills.billcaptureapp;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.Window;

import com.bills.billcaptureapp.fragments.StartScreenFragment;
import com.bills.billslib.Contracts.BillRow;
import com.bills.billslib.Contracts.Constants;
import com.bills.billslib.Contracts.Enums.LogLevel;
import com.bills.billslib.Contracts.Enums.LogsDestination;
import com.bills.billslib.Core.BillsLog;
import com.bills.billslib.Core.MainActivityBase;
import com.bills.billslib.Utilities.Utilities;
import com.bills.testslib.CameraFragment;
import com.bills.testslib.TestsUtilities;
import java.util.List;
import java.util.UUID;

public class BillCaptureAppMainActivity extends MainActivityBase implements
        com.bills.billslib.Fragments.CameraFragment.OnFragmentInteractionListener,
        StartScreenFragment.OnFragmentInteractionListener{
    private String Tag = BillCaptureAppMainActivity.class.getName();
    private CameraFragment mCameraFragment;
    private StartScreenFragment mStartScreenFragment;
    private Fragment mCurrentFragment;
    private Handler mHandler;
    private String currFileNameToSave;
    private UUID mSessionId;
    Dialog mProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bill_capture_app_main);
        mSessionId = UUID.randomUUID();
        TestsUtilities.InitBillsLogToLogcat(mSessionId);
        mCameraFragment = new CameraFragment();
        mStartScreenFragment = new StartScreenFragment();
        mProgressDialog = new Dialog(this);
        mHandler = new Handler();
        ReturnToWelcomeScreen();
    }

    public void StartCameraFragment() {
        try {
            mCameraFragment.Init(mSessionId, 0, "", this);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, mCameraFragment);
            //addToBackStack because of the back button
            transaction.addToBackStack(null);

            // Commit the transaction
            transaction.commit();
            mCurrentFragment = mCameraFragment;
        } catch (Exception e) {
            BillsLog.Log(mSessionId, LogLevel.Error, "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage(), LogsDestination.BothUsers, Tag);
        }
    }

    @Override
    public void NotifyClickedButton(StartScreenFragment.CaptureType captureType, String restaurantName) {
        String folderToSaveOn = Constants.TESSERACT_SAMPLE_DIRECTORY + Build.BRAND + "_" + Build.MODEL + "/" + restaurantName;
        switch (captureType){
            case SIMPLE:
                currFileNameToSave = folderToSaveOn + "/ocrBytes.txt";
                break;
            case RIGHT:
                currFileNameToSave = folderToSaveOn + "/ocrBytes1.txt";
                break;
            case LEFT:
                currFileNameToSave = folderToSaveOn + "/ocrBytes2.txt";
                break;
            case REMOTLY:
                currFileNameToSave = folderToSaveOn + "/ocrBytes3.txt";
                break;
            case STRAIGHT:
                currFileNameToSave = folderToSaveOn + "/ocrBytes4.txt";
                break;
            default:
                throw new UnsupportedOperationException(Tag + ": NotifyClickedButton");
        }
    }

    @Override
    public void ProceedToSummarizerFragment(List<BillRow> rows, byte[] image, Integer passCode, String relativeDbAndStoragePath) {
        throw new UnsupportedOperationException(Tag + ": ProceedToSummarizerFragment");
    }

    @Override
    public void onBackPressed(){
        if(mCurrentFragment == mCameraFragment || mCurrentFragment == mStartScreenFragment){
            ReturnToWelcomeScreen();
        }else{
            super.onBackPressed();
        }
    }

    @Override
    public void ReturnToWelcomeScreen() {
        ReturnToWelcomeScreen();
    }

    @Override
    public void Finish() {
        throw new UnsupportedOperationException(Tag + ": Finish");
    }

    @Override
    public void ReturnToWelcomeScreen(final byte[] image, String mRelativeDbAndStoragePath) {
        try {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.fragment_container, mStartScreenFragment);
            transaction.addToBackStack(null);
            // Commit the transaction
            transaction.commit();
            mCurrentFragment = mStartScreenFragment;
            mProgressDialog = new Dialog(this);
            if (null != image) {
                Thread t = new Thread() {
                public void run() {
                    try {
                        mHandler.post(mShowProgressDialog);
//                        Utilities.SaveBytesToPNGFile(image, currFileNameToSave);
                        Utilities.SaveToTXTFile(mSessionId, image, currFileNameToSave);
                        mHandler.post(mHideProgressDialog);
                    } catch (Exception e) {
                        BillsLog.Log(mSessionId, LogLevel.Error, "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage(), LogsDestination.BothUsers, Tag);
                    }
                }
                };
                t.start();
            }
        } catch (Exception e) {
            BillsLog.Log(mSessionId, LogLevel.Error, "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage(), LogsDestination.BothUsers, Tag);
        }
    }

    // Create runnable for posting
    final Runnable mHideProgressDialog = new Runnable() {
        public void run() {
            if(mProgressDialog != null)
            {
                mProgressDialog.cancel();
                mProgressDialog.hide();
            }
        }
    };

    // Create runnable for posting
    final Runnable mShowProgressDialog = new Runnable() {
        public void run() {
            mProgressDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            mProgressDialog.setContentView(R.layout.custom_dialog_progress);
            mProgressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
        }
    };
}
