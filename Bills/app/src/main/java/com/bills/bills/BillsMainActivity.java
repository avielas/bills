package com.bills.bills;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.widget.Toast;

import com.bills.bills.firebase.FirebaseLogger;
import com.bills.bills.firebase.FirebaseUploader;
import com.bills.bills.firebase.PassCodeResolver;
import com.bills.bills.fragments.BillSummarizerFragment;
import com.bills.billslib.Fragments.CameraFragment;
import com.bills.bills.fragments.WelcomeScreenFragment;
import com.bills.billslib.Contracts.BillRow;
import com.bills.billslib.Core.BillsLog;
import com.bills.billslib.Core.MainActivityBase;
import com.bills.billslib.Utilities.GMailSender;
import com.bills.billslib.Utilities.Utilities;
import com.firebase.ui.auth.AuthUI;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.mail.MessagingException;

public class BillsMainActivity extends MainActivityBase implements
        WelcomeScreenFragment.OnFragmentInteractionListener,
        BillSummarizerFragment.OnFragmentInteractionListener,
        CameraFragment.OnFragmentInteractionListener{

    private String Tag = BillsMainActivity.class.getName();
    private static final int RC_SIGN_IN = 123;

    private static final String UsersDbKey = "users";
    private static final String BillsPerUserDbKey = "BillsPerUser";
    private final String RowsDbKey = "Rows";
    private String mUid;
    private Context mContext;
    private String mNowForFirstSession;
    private static Boolean mFirstEnteringInitCommonSession;
    private static Boolean mFirstEnteringInitPrivateSessionSecondUser;

    //Fragments
    private BillSummarizerFragment mBillSummarizerFragment;
    private WelcomeScreenFragment mWelcomeFragment;
    private CameraFragment mCameraFragment;
    private Fragment mCurrentFragment;

    //Firebase Authentication members
    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;

    //Storage and DB paths
    private String mMyLogRootPath;
    private String mAppLogRootPath;
    private String mAppStoragePath;

    private PassCodeResolver mPassCodeResolver;
    private UUID mSessionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;
        SetDefaultUncaughtExceptionHandler();
        setContentView(R.layout.activity_bills_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        mBillSummarizerFragment = new BillSummarizerFragment();
        mWelcomeFragment = new WelcomeScreenFragment();
        mCameraFragment = new CameraFragment();
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, mWelcomeFragment).commit();
        mCurrentFragment = mWelcomeFragment;

        //Firebase Authentication initialization
        mAuth = FirebaseAuth.getInstance();
        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null){
                    //user is signed in
                    mUid = user.getUid();
                    InitPrivateSession();
                    Toast.makeText(BillsMainActivity.this, "You are now signed in. Welcome", Toast.LENGTH_LONG).show();
                }else{
                    //user is signed out
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false)
                                    .setAvailableProviders(
                                            Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                                    new AuthUI.IdpConfig.Builder(AuthUI.PHONE_VERIFICATION_PROVIDER).build(),
                                                    new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        StartWelcomeScreen();
    }

    private void InitPrivateSession() {
        String now = Utilities.GetTimeStamp();

        //saving for later use as first Session folder at my logs
        mNowForFirstSession = now;
        mSessionId = UUID.randomUUID();
        mMyLogRootPath  = UsersDbKey + "/" + mUid + "/Logs/" + now;
        mAppLogRootPath = UsersDbKey + "/" + mUid;
        mAppStoragePath = BillsPerUserDbKey + "/" + mUid + "/" + now;
        mFirstEnteringInitCommonSession = true;
        mFirstEnteringInitPrivateSessionSecondUser = true;

        /**** First initialization of private session. Actually we initiate appFirebaseLogPath to  ****/
        /**** root path because at this stage we shouldn't print anything to application log path. ****/
        /**** Same for mAppStoragePath.                                                        ****/
        BillsLog.AddNewSession(mSessionId,
                               new FirebaseLogger(mUid,
                                                  mMyLogRootPath,
                                                  mAppLogRootPath));
        mPassCodeResolver = null;
        mPassCodeResolver = new PassCodeResolver(mUid, mAppLogRootPath, mNowForFirstSession);
    }

    private void InitCommonSession(){
        String now = mFirstEnteringInitCommonSession ? mNowForFirstSession : Utilities.GetTimeStamp();
        mSessionId = UUID.randomUUID();
        mAppStoragePath = BillsPerUserDbKey + "/" + mUid + "/" + now;
        BillsLog.AddNewSession(mSessionId,
                               new FirebaseLogger(mUid,
                                  mMyLogRootPath + "/" + now,
                                 mAppLogRootPath + "/" + now + "/Logs"));
        mFirstEnteringInitCommonSession = false;
        mPassCodeResolver.SetNow(now);
    }

    /***
     * The following function erase the previous set of DB/Storage paths. Actually it erase the set of InitPrivateSession
     * which should be re-defined for second user
     * @param userUid - current user id
     * @param relativeDbAndStoragePath - relative DB path of application. used to extract the application directory timestamp
     */
    private void InitPrivateSessionSecondUser(String userUid, String relativeDbAndStoragePath){
        String now = relativeDbAndStoragePath.split("/")[1];
        mSessionId = UUID.randomUUID();
        String nowNewSessionChild = mFirstEnteringInitPrivateSessionSecondUser ? now : Utilities.GetTimeStamp();
        mMyLogRootPath = UsersDbKey + "/" + userUid + "/Logs/" + now;
        mAppLogRootPath = UsersDbKey + "/" + relativeDbAndStoragePath + "/Logs";
        mAppStoragePath = BillsPerUserDbKey + "/" + userUid + "/" + nowNewSessionChild;
        BillsLog.AddNewSession(mSessionId,
                new FirebaseLogger(userUid,
                        mMyLogRootPath + "/" + nowNewSessionChild,
                        mAppLogRootPath));
        mFirstEnteringInitPrivateSessionSecondUser = false;
        mPassCodeResolver.SetNow(now);
    }

    private void UninitCommonSession(){
        BillsLog.UninitCommonSession(mSessionId, mMyLogRootPath);
    }

    public void onResume(){
        super.onResume();
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    public void onPause(){
        super.onPause();
        if(mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
//        //TODO: clear all displayed data
    }

    @Override
    public void onBackPressed(){
        if(mCurrentFragment == mCameraFragment || mCurrentFragment == mBillSummarizerFragment){
            UninitCommonSession();
            StartWelcomeScreen();
        }else{
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
        super.onSaveInstanceState(outState);
    }

    public void StartWelcomeScreen() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Replace whatever is in the fragment_container view with this fragment,
        // and add the transaction to the back stack so the user can navigate back
        transaction.replace(R.id.fragment_container, mWelcomeFragment);
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();
        mCurrentFragment = mWelcomeFragment;
    }

    @Override
    public void StartCamera() {
        InitCommonSession();
        mPassCodeResolver.GetPassCode(new PassCodeResolver.IPassCodeResolverCallback() {
            @Override
            public void OnPassCodeResovled(Integer passCode, String relativeDbAndStoragePath, String userUid) {
                mCameraFragment.Init(mSessionId, passCode, relativeDbAndStoragePath, mContext);
//                BillsLog.AddNewSession(mSessionId, new FirebaseLogger(userUid, "users/" + userUid, "users/" + relativeDbAndStoragePath));
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

                // Replace whatever is in the fragment_container view with this fragment,
                // and add the transaction to the back stack so the user can navigate back
                transaction.replace(R.id.fragment_container, mCameraFragment);
                transaction.addToBackStack(null);

                // Commit the transaction
                transaction.commit();
                mCurrentFragment = mCameraFragment;
            }

            @Override
            public void OnPassCodeResolveFail(String error) {
                Toast.makeText(BillsMainActivity.this, "משהו השתבש... נא לנסות שוב", Toast.LENGTH_SHORT).show();
                ReturnToWelcomeScreen();
            }
        });
    }

    @Override
    public void StartSummarizer(int passCode) {
        //TODO - BUG - see where the second user print logs instead of printing to
        //TODO - app logs
//        InitCommonSession();
        mPassCodeResolver.GetRelativePath(passCode, new PassCodeResolver.IPassCodeResolverCallback() {
            @Override
            public void OnPassCodeResovled(Integer passCode, String relativeDbAndStoragePath, String userUid) {
                InitPrivateSessionSecondUser(userUid, relativeDbAndStoragePath);
                mBillSummarizerFragment.Init(mSessionId,
                        BillsMainActivity.this.getApplicationContext(),
                        passCode,
                        "users/" + relativeDbAndStoragePath + "/" + RowsDbKey,
                        "BillsPerUser/" + relativeDbAndStoragePath);
                FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.fragment_container, mBillSummarizerFragment);
                transaction.addToBackStack(null);

                // Commit the transaction
                transaction.commit();
                mCurrentFragment = mBillSummarizerFragment;
            }

            @Override
            public void OnPassCodeResolveFail(String error) {
                Toast.makeText(BillsMainActivity.this, "הקוד שהזנת לא נמצא...", Toast.LENGTH_SHORT).show();
            }
        });

    }

    @Override
    public void ProceedToSummarizerFragment(final List<BillRow> rows, final byte[] image,
                                            final Integer passCode, final String relativeDbAndStoragePath) {

        String rowDbKeyPath = UsersDbKey + "/" + relativeDbAndStoragePath + "/" + RowsDbKey;
        mBillSummarizerFragment.Init(mSessionId, BillsMainActivity.this.getApplicationContext(), passCode, rowDbKeyPath, rows);

        FirebaseUploader uploader = new FirebaseUploader(mSessionId, rowDbKeyPath, mAppStoragePath, BillsMainActivity.this);
        uploader.UploadRows(rows, image, new FirebaseUploader.IFirebaseUploaderCallback() {

            @Override
            public void OnSuccess() {}

            @Override
            public void OnFail(String message) {
                Log.e(Tag, "Error accured while uploading bill rows. Error: " + message);
                ReturnToWelcomeScreen();
            }
        });

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.fragment_container, mBillSummarizerFragment);
        transaction.addToBackStack(null);

        // Commit the transaction
        transaction.commit();
        mCurrentFragment = mBillSummarizerFragment;
    }

    @Override
    public void ReturnToWelcomeScreen() {
        StartWelcomeScreen();
    }

    @Override
    public void ReturnToWelcomeScreen(final byte[] image, String relativeDbAndStoragePath) {
        UploadBillImageToStorage(image, relativeDbAndStoragePath);
        mCurrentFragment = mWelcomeFragment;
        ReturnToWelcomeScreen();
    }

    private void UploadBillImageToStorage(byte[] image, String relativeDbAndStoragePath) {
        String relativeDbAndStoragePathToUpload = UsersDbKey + "/" + relativeDbAndStoragePath;
        FirebaseUploader uploader = new FirebaseUploader(mSessionId, relativeDbAndStoragePathToUpload, mAppStoragePath, BillsMainActivity.this);
        uploader.UploadFullBillImage(image);
    }

    @Override
    public void Finish() {
        finish();
    }

    private void SetDefaultUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread paramThread, final Throwable paramThrowable) {
                Thread thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try  {
                            GMailSender sender = new GMailSender("billsplitapplication@gmail.com", "billsplitapplicationisthebest");
                            try {
                                String userDetails = Build.MANUFACTURER + "-" + Build.MODEL +
                                        ". OS is " + Build.VERSION.RELEASE;
                                sender.SendEmail("Uncaught exception has been thrown from " + userDetails,
                                        paramThrowable.getMessage().toString(),
                                        "billsplitapplication@gmail.com",
                                        "billsplitapplication@gmail.com");
                            } catch (MessagingException e) {
                                e.printStackTrace();
                            }
                            finish();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
                thread.start();
            }
        });
    }
}
