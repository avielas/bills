package com.bills.billslib.Fragments;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.Toast;

import com.bills.billslib.Contracts.Enums.LogsDestination;
import com.bills.billslib.R;
import com.bills.billslib.Camera.CameraRenderer;
import com.bills.billslib.Camera.IOnCameraFinished;
import com.bills.billslib.Contracts.BillRow;
import com.bills.billslib.Contracts.Constants;
import com.bills.billslib.Contracts.Enums.Language;
import com.bills.billslib.Contracts.Enums.LogLevel;
import com.bills.billslib.Contracts.Interfaces.IOcrEngine;
import com.bills.billslib.Core.BillAreaDetector;
import com.bills.billslib.Core.BillsLog;
import com.bills.billslib.Core.ImageProcessingLib;
import com.bills.billslib.Core.TemplateMatcher;
import com.bills.billslib.Core.TesseractOCREngine;
import com.bills.billslib.Utilities.Utilities;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link CameraFragment.OnFragmentInteractionListener} interface
 * to handle interaction events.
 */
public class CameraFragment extends Fragment implements View.OnClickListener, IOnCameraFinished {
    protected String Tag = CameraFragment.class.getName();
    private Handler mHandler;
//    private Handler h = new Handler(mContext.getMainLooper());
    private Dialog mProgressDialog;
    private Context mContext;

    //Camera Renderer
    private CameraRenderer mRenderer;

    //Camera Elements
    private TextureView mCameraPreviewView = null;
    private Button mCameraCaptureButton = null;

    //selection order: auto->on->off
    private Button mCameraFlashMode = null;
    private Integer mCurrentFlashMode = R.drawable.camera_screen_flash_auto;

    protected OnFragmentInteractionListener mListener;

    protected IOcrEngine mOcrEngine;

    protected Integer mPassCode;
    protected String mRelativeDbAndStoragePath;

    public CameraFragment() {
        // Required empty public constructor
    }

    public void Init(Context context, Integer passCode, String relativeDbAndStoragePath){
        mContext = context;
        mPassCode = passCode;
        mRelativeDbAndStoragePath = relativeDbAndStoragePath;
        mHandler = new Handler();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnFragmentInteractionListener) {
            mListener = (OnFragmentInteractionListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        mContext = null;
        mPassCode = null;
        mRelativeDbAndStoragePath = null;
        mHandler = null;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mRenderer = new CameraRenderer(getContext());
        mRenderer.SetOnCameraFinishedListener(this);

        mCameraPreviewView = (TextureView) getView().findViewById(R.id.camera_textureView);
        mCameraPreviewView.setSurfaceTextureListener(mRenderer);
        mCameraPreviewView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        mRenderer.setAutoFocus();
                        break;
                }
                return true;
            }
        });

        mCameraPreviewView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                mRenderer.onSurfaceTextureSizeChanged(null, v.getWidth(), v.getHeight());
            }
        });

        mCameraCaptureButton = (Button) getView().findViewById(R.id.camera_capture_button);
        mCameraCaptureButton.setOnClickListener(this);

        mCameraFlashMode = (Button)getView().findViewById(R.id.camera_flash_mode);
        mCameraFlashMode.setBackgroundResource(mCurrentFlashMode);
        mCameraFlashMode.setTag(mCurrentFlashMode);
        mCameraFlashMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mCurrentFlashMode == R.drawable.camera_screen_flash_auto){
                    mCurrentFlashMode = R.drawable.camera_screen_flash_on;
                    mRenderer.SetFlashMode(Camera.Parameters.FLASH_MODE_ON);

                }else if(mCurrentFlashMode == R.drawable.camera_screen_flash_on){
                    mCurrentFlashMode = R.drawable.camera_screen_flash_off;
                    mRenderer.SetFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                }else {
                    mCurrentFlashMode = R.drawable.camera_screen_flash_auto;
                    mRenderer.SetFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                }
                mCameraFlashMode.setBackgroundResource(mCurrentFlashMode);
            }
        });

        if(mOcrEngine == null) {
            mOcrEngine = new TesseractOCREngine();
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    mOcrEngine.Init(Constants.TESSERACT_SAMPLE_DIRECTORY, Language.Hebrew);
                }
            });
            t.start();
        }

    }

    @Override
    public void onClick(View v) {
        mRenderer.setAutoFocus();
        mRenderer.takePicture();
    }

    @Override
    public void OnCameraFinished(final byte[] image) {
        mProgressDialog = new Dialog(mContext);
        Thread t = new Thread() {
            public void run() {
                try {
                    mHandler.post(mShowProgressDialog);
                    if (!OpenCVLoader.initDebug()) {
                        String logMessage = "Failed to initialize OpenCV.";
                        BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
                        mListener.Finish();
                        ErrorReporter(logMessage, logMessage);
                        throw new RuntimeException(logMessage);
//                        return;
                    }
                    Mat billMat = null;
                    Mat billMatCopy = null;
                    Bitmap processedBillBitmap = null;
                    TemplateMatcher templateMatcher;
                    int numOfItems;
                    BillAreaDetector areaDetector = new BillAreaDetector();
                    Point topLeft = new Point();
                    Point topRight = new Point();
                    Point buttomLeft = new Point();
                    Point buttomRight = new Point();

                    while (!mOcrEngine.Initialized()) {
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            String logMessage = "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                            String toastMessage = "Exception has been thrown. See logs and try again!";
                            ErrorReporter(logMessage, toastMessage);
                            throw new RuntimeException(e);
//                            return;
                        }
                    }

                    try {
                        billMat = Utilities.Bytes2MatAndRotateClockwise90(image);
                        if(billMat == null){
                            String logMessage = "failed to convert bytes to mat or rotating the image";
                            String toastMessage = "Failed to convert or rotating image. See logs and try again!";
                            ErrorReporter(logMessage, toastMessage);
                            throw new RuntimeException(logMessage);
//                            return;
                        }
                        if (!areaDetector.GetBillCorners(billMat, topLeft, topRight, buttomRight, buttomLeft)) {
                            String logMessage = "failed to get bills corners";
                            ErrorReporter(logMessage, logMessage);
                            throw new RuntimeException(logMessage);
//                            return;
                        }

                        try {
                            billMat = ImageProcessingLib.WarpPerspective(billMat, topLeft, topRight, buttomRight, buttomLeft);
                            billMatCopy = billMat.clone();
                        } catch (Exception e) {
                            String logMessage = "Warp perspective has been failed. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                            String toastMessage = "Warp perspective has been failed. See logs and try again!";
                            ErrorReporter(logMessage, toastMessage);
                            throw new RuntimeException(e);
//                            return;
                        }

                        BillsLog.Log(Tag, LogLevel.Info, "Warped perspective successfully.", LogsDestination.BothUsers);

                        processedBillBitmap = Bitmap.createBitmap(billMat.width(), billMat.height(), Bitmap.Config.ARGB_8888);
                        ImageProcessingLib.PreprocessingForTM(billMat);
                        Utils.matToBitmap(billMat, processedBillBitmap);

                        templateMatcher = new TemplateMatcher(mOcrEngine, processedBillBitmap);
                        try {
                            templateMatcher.Match();
                            BillsLog.Log(Tag, LogLevel.Info, "Template matcher succeeded.", LogsDestination.BothUsers);
                        } catch (Exception e) {
                            String logMessage = "Template matcher has been threw an exception. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                            String toastMessage = "Template matcher has been threw an exception. See logs and try again!";
                            ErrorReporter(logMessage, toastMessage);
                            throw new RuntimeException(e);
//                            return;
                        }

                        ImageProcessingLib.PreprocessingForParsing(billMatCopy);
                        numOfItems = templateMatcher.priceAndQuantity.size();

                        /***** we use processedBillBitmap second time to prevent another Bitmap allocation due to *****/
                        /***** Out Of Memory when running 4 threads parallel                                      *****/
                        Utils.matToBitmap(billMatCopy, processedBillBitmap);
                        templateMatcher.InitializeBeforeSecondUse(processedBillBitmap);
                        templateMatcher.Parsing(numOfItems);

                        List<BillRow> rows = new ArrayList<>();
                        int index = 0;
                        for (Double[] row : templateMatcher.priceAndQuantity) {
                            Bitmap item = templateMatcher.itemLocationsByteArray.get(index);
                            Double price = row[0];
                            Integer quantity = row[1].intValue();
                            rows.add(new BillRow(price, quantity, index, item));
                            index++;
                        }
                        mListener.StartSummarizerFragment(rows, image, mPassCode, mRelativeDbAndStoragePath);
                        BillsLog.Log(Tag, LogLevel.Info, "Parsing finished", LogsDestination.BothUsers);
                    }catch (Exception e){
                        String logMessage = "Exception has been thrown. StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                        String toastMessage = "Exception has been thrown. See logs and try again!";
                        ErrorReporter(logMessage, toastMessage);
                        throw new RuntimeException(e);
//                        mListener.StartCameraFragment(image);
                    }
                    finally {
                        if(null != billMat){
                            billMat.release();
                        }
                        if(null != processedBillBitmap){
                            processedBillBitmap.recycle();
                        }
                        if(null != billMatCopy){
                            billMatCopy.release();
                        }
                        mHandler.post(mHideProgressDialog);
                    }
                } catch (Exception e) {
                    String logMessage = "Exception has been thrown. StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                    BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
                    mHandler.post(mHideProgressDialog);
                    throw new RuntimeException(e);
//                    mListener.StartCameraFragment(image);
                }
            }
        };
        t.start();
    }

    // Create runnable for posting progress dialog
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

    private void ErrorReporter(String logMessage, final String toastMessage) {
        BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
        mHandler.post(mHideProgressDialog);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, toastMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        void StartSummarizerFragment(List<BillRow> rows, byte[] image, Integer passCode, String relativeDbAndStoragePath);
        void StartWelcomeFragment();
        void Finish();
        void StartCameraFragment(final byte[] image);
    }
}
