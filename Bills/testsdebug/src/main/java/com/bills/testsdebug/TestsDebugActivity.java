package com.bills.testsdebug;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import com.bills.billslib.Contracts.*;
import com.bills.billslib.Contracts.Enums.Language;
import com.bills.billslib.Core.BillAreaDetector;
import com.bills.billslib.Core.ImageProcessingLib;
import com.bills.billslib.Core.TemplateMatcher;
import com.bills.billslib.Core.TesseractOCREngine;
import com.bills.billslib.CustomViews.DragRectView;
import com.bills.billslib.Utilities.FilesHandler;
import com.gregacucnik.EditableSeekBar;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import uk.co.senab.photoview.PhotoViewAttacher;

import static android.view.View.GONE;

public class TestsDebugActivity extends AppCompatActivity implements View.OnClickListener{
    private enum StructureElement {
        NONE,
        HORIZONTAL_LINE,
        VERTICAL_LINE,
        RECTANGULAR,
        ELLIPTICAL,
        CROSS_SHAPED
    }
    TesseractOCREngine tesseractOCREngine;
    String _billName;
    String _restaurantName;
    String _brandAndModelPath;
    String _expectedTxtFileName;
    EditableSeekBar _adaptiveThresholdBlockSizeSeekBar;
    EditableSeekBar _adaptiveThresholdConstantSubtractedSeekBar;
    EditableSeekBar _dilateKernelSizeSeekBar;
    EditableSeekBar _erodeKernelSizeSeekBar;
    PhotoViewAttacher _photoViewAttacher;
    Bitmap _billWithPrintedRedLines;
    Bitmap _bill;
    Bitmap _processedBill;
    Bitmap _processedBillForCreateNewBill;
    Mat _rgba;
    Mat _gray;
    ImageView _processedImageView;
    ImageView _processedForCreateNewBillImageView;
    ImageView _originalImageView;
    Button _ocrOnPreprocessedButton;
    Button _templateMatcherButton;
    Button _printWordsLocationButton;
    Button _saveProccessedButton;
    Button _generateBillButton;
    Button _adaptiveThresholdButton;
    Button _dilateButton;
    Button _erodeButton;
    EditText _adaptiveThresholdIterationsEditText;
    EditText _dilateIterationsEditText;
    EditText _erodeIterationsEditText;
    StringBuilder _results;
    StringBuilder _algorithmsTracing;
    Spinner _kernelTypeSpinner;
    List<String> _kernelTypes;
    private final int BILLS_REQUEST_CODE = 1;
    TemplateMatcher templateMatcher;
    private Button _userCropFinished;
    private DragRectView _dragRectView;
    private Point _topLeft = new Point();
    private Point _topRight = new Point();
    private Point _buttomLeft = new Point();
    private Point _buttomRight = new Point();
    private LinearLayout _testsDebugView;
    private RelativeLayout _emptyRelativeLayoutView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tests_debug_view);
        _testsDebugView = (LinearLayout)findViewById(R.id.tests_debug_view);
        _emptyRelativeLayoutView = (RelativeLayout)findViewById(R.id.empty_relative_layout_view);
        _emptyRelativeLayoutView.setVisibility(GONE);
//        PreparingEnvironmentUtil.PrepareTesseract(this);
        //copy images to internal memory just in case of emulator
//        if(PreparingEnvironmentUtil.IsRunningOnEmulator(Build.MANUFACTURER, Build.MODEL))
//        {
//            PreparingEnvironmentUtil.PrepareImagesForTests(this);
//        }
        _billName = "29112016_2246_croppedCenter.jpg";
        _restaurantName = "mina1";
        _brandAndModelPath = Constants.TESSERACT_SAMPLE_DIRECTORY + Build.BRAND + "_" + Build.MODEL;
        //extract date from bill, for creating expected txt file name
        _expectedTxtFileName = _restaurantName + ".txt";
        _originalImageView = (ImageView)findViewById(R.id.originalImageView);
        _processedImageView = (ImageView)findViewById(R.id.processedImageView);
        _processedForCreateNewBillImageView = (ImageView)findViewById(R.id.processedForCreateNewBillImageView);
        _adaptiveThresholdBlockSizeSeekBar =(EditableSeekBar) findViewById(R.id.adaptiveThresholdBlockSizeEditableSeekBar);
        _adaptiveThresholdConstantSubtractedSeekBar =(EditableSeekBar) findViewById(R.id.adaptiveThresholdConstantSubtractedEditableSeekBar);
        _dilateKernelSizeSeekBar =(EditableSeekBar) findViewById(R.id.dilateEditableSeekBar);
        _erodeKernelSizeSeekBar =(EditableSeekBar) findViewById(R.id.erodeEditableSeekBar);
        _ocrOnPreprocessedButton = (Button) findViewById(R.id.runOcrOnPreprocessedButton);
        _templateMatcherButton = (Button) findViewById(R.id.templateMatcherButton);
        _printWordsLocationButton = (Button) findViewById(R.id.printWordsLocationButton);
        _saveProccessedButton = (Button) findViewById(R.id.saveProccessedButton);
        _generateBillButton =  (Button) findViewById(R.id.generateBillButton);
        _adaptiveThresholdButton = (Button) findViewById(R.id.adaptiveThresholdBlockSizeButton);
        _dilateButton = (Button) findViewById(R.id.dilateButton);
        _erodeButton = (Button) findViewById(R.id.erodeButton);
        _adaptiveThresholdIterationsEditText = (EditText) findViewById(R.id.adaptiveThresholdIterationsNumber);
        _adaptiveThresholdIterationsEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        _dilateIterationsEditText = (EditText) findViewById(R.id.dilateIterationsNumber);
        _dilateIterationsEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        _erodeIterationsEditText = (EditText) findViewById(R.id.erodeIterationsNumber);
        _erodeIterationsEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
        _results = new StringBuilder();
        _algorithmsTracing = new StringBuilder();
        _algorithmsTracing.append("Algorithms Tracing:");
        _algorithmsTracing.append(System.getProperty("line.separator"));
        _kernelTypeSpinner = (Spinner) findViewById(R.id.kernelTypeSpinner);
        _kernelTypes = new ArrayList<>();
        _kernelTypes.add(StructureElement.NONE.toString());
        _kernelTypes.add(StructureElement.HORIZONTAL_LINE.toString());
        _kernelTypes.add(StructureElement.VERTICAL_LINE.toString());
        _kernelTypes.add(StructureElement.RECTANGULAR.toString());
        _kernelTypes.add(StructureElement.ELLIPTICAL.toString());
        _kernelTypes.add( StructureElement.CROSS_SHAPED.toString());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, _kernelTypes);
        _kernelTypeSpinner.setAdapter(adapter);

        if (!OpenCVLoader.initDebug()) {
            Log.d("aa", "Failed to initialize OpenCV.");
        }

        _rgba = new Mat();
        _gray = new Mat();
        try {
            _bill = GetLastWarpedBill();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            _billWithPrintedRedLines = GetLastWarpedBill();
        } catch (IOException e) {
            e.printStackTrace();
        }
        _processedBill = Bitmap.createBitmap(_bill.getWidth(), _bill.getHeight(), Bitmap.Config.ARGB_8888);
        _processedBillForCreateNewBill = Bitmap.createBitmap(_bill.getWidth(), _bill.getHeight(), Bitmap.Config.ARGB_8888);

        //Show original image on ImageView
        _originalImageView.setImageBitmap(_bill);
        _photoViewAttacher = new PhotoViewAttacher(_originalImageView);

        PreprocessingForTM();
        AddListenerOcrOnPreprocessedButton();
        AddListenerSaveProccessedButton();
        AddListenerGenerateBillButton();
        AddListenerTemplateMatcherButton();
        AddListenerPrintWordsLocationButton();
        AddListenerAdaptiveThresholdButton();
        AddListenerDilateButton();
        AddListenerErodeButton();
        tesseractOCREngine = new TesseractOCREngine();
        try {
            tesseractOCREngine.Init(Constants.TESSERACT_SAMPLE_DIRECTORY, Language.Hebrew);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void PreprocessingForTM() {
        Mat warpedMat = new Mat();
        Utils.bitmapToMat(_bill, warpedMat);
        Mat processedBillMat = ImageProcessingLib.PreprocessingForTM(warpedMat);
        _processedBill = Bitmap.createBitmap(warpedMat.width(), warpedMat.height(), Bitmap.Config.ARGB_8888);
        _processedBillForCreateNewBill = Bitmap.createBitmap(warpedMat.width(), warpedMat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(processedBillMat, _processedBill);
        _processedImageView.setImageBitmap(_processedBill);
        Utils.matToBitmap(warpedMat, _processedBillForCreateNewBill);
        _processedForCreateNewBillImageView.setImageBitmap(_processedBillForCreateNewBill);
        _photoViewAttacher = new PhotoViewAttacher(_processedForCreateNewBillImageView);
        warpedMat.release();
        processedBillMat.release();
    }

    private void PreprocessingForParsing() {
        Mat processedBillForParsingMat = new Mat();
        Utils.bitmapToMat(_processedBillForCreateNewBill, processedBillForParsingMat);
        Mat processedItemsAreaMat = ImageProcessingLib.PreprocessingForParsing(processedBillForParsingMat);
        Utils.matToBitmap(processedItemsAreaMat, _processedBill);
        _originalImageView.setImageBitmap(_bill);
        _photoViewAttacher = new PhotoViewAttacher(_originalImageView);
        _processedImageView.setImageBitmap(_processedBill);
        _photoViewAttacher = new PhotoViewAttacher(_processedImageView);
    }

    public void AddListenerAdaptiveThresholdButton() {
        _adaptiveThresholdButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    _algorithmsTracing.setLength(0);
                    _algorithmsTracing.append("Algorithms Tracing:");
                    _algorithmsTracing.append(System.getProperty("line.separator"));
                    int blockSize = _adaptiveThresholdBlockSizeSeekBar.getValue();
                    int constantSubtracted = _adaptiveThresholdConstantSubtractedSeekBar.getValue();
                    /*** convert block size to odd number according to opencv specs ***/
                    int blockSizeToOddNumber = blockSize%2 == 0 ? blockSize-1 : blockSize;
                    /****************/
                    _rgba.release();
                    _rgba = new Mat();
                    Utils.bitmapToMat(_bill, _rgba);
                    AdaptiveThreshold(_rgba, blockSizeToOddNumber, constantSubtracted);
                    Utils.matToBitmap(_rgba, _processedBill);
                    _processedImageView.setImageBitmap(_processedBill);
                    _photoViewAttacher = new PhotoViewAttacher(_processedImageView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void AdaptiveThreshold(Mat rgba, int blockSize, double C){
        ImageProcessingLib.AdaptiveThreshold(rgba, blockSize, C);
        _algorithmsTracing.append("AdaptiveThreshold: block size " + blockSize + ", constant subtracted " + C);
        _algorithmsTracing.append(System.getProperty("line.separator"));
    }

    private void ValidateOcrBillResult(String imageStatus, Bitmap billBitmap) throws Exception{
        List<String> expectedBillTextLines = FilesHandler.ReadTxtFile(_brandAndModelPath + "/" +_restaurantName + "/" + _expectedTxtFileName);
        _results.append("Test of " + imageStatus + " " + _restaurantName);
        _results.append(System.getProperty("line.separator"));
        LinkedHashMap ocrResultCroppedBill = GetOcrResults();
        CompareExpectedToOcrResult(ocrResultCroppedBill, expectedBillTextLines);
    }

    private LinkedHashMap GetOcrResults() {
        int i = 0;
        LinkedHashMap imageLinesLinkedHashMap = new LinkedHashMap();
        for(Double[] priceQuantity : templateMatcher.priceAndQuantity){
            imageLinesLinkedHashMap.put(i, new HashMap<>());
            HashMap lineHash = (HashMap)imageLinesLinkedHashMap.get(i);
            lineHash.put("product",templateMatcher.itemLocationsRect.get(i));
            lineHash.put("price",priceQuantity[0]);
            lineHash.put("quantity",priceQuantity[1]);
            i++;
        }
        return imageLinesLinkedHashMap;
    }

    /**
     * comparing line to line ocr results of bill vs expected txt file
     * @param ocrResultCroppedBill ocr results of cropped bill
     * @param expectedBillTextLines expected bill lines from txt file
     */
    private void CompareExpectedToOcrResult(LinkedHashMap ocrResultCroppedBill, List<String> expectedBillTextLines) {
//        _results.append("Validating Ocr Result:");
//        _results.append(System.getProperty("line.separator"));
//        Double accuracyPercent = Compare(ocrResultCroppedBill, expectedBillTextLines);
//        _results.append(System.getProperty("line.separator"));
//        if(ocrResultCroppedBill.size() != expectedBillTextLines.size())
//        {
//            _results.append("ocrResultCroppedBill contains "+ ocrResultCroppedBill.size() + " lines, but" +
//                    " expectedBillTextLines contains "+ expectedBillTextLines.size()+" lines");
//        }
        PrintParsedNumbers(ocrResultCroppedBill);
//        _results.append("Accuracy is "+ accuracyPercent+"%");
        _results.append(System.getProperty("line.separator"));
        _results.append(System.getProperty("line.separator"));
    }

    /**
     *
     * @param ocrResult ocr result of bill included price and quantity
     * @param expectedBillTextLines expected bill lines from txt file
     * @return true in case of equal results. false if unequal
     */
    private Double Compare(LinkedHashMap ocrResult, List<String> expectedBillTextLines) {
        int lineNumber = 0;
        Double countInvalids = 0.0;
        Double accuracyPercent;

        for (String expectedLine : expectedBillTextLines)
        {
            String[] rowsOfLine = expectedLine.split(" ");
            Double expectedPrice = Double.parseDouble(rowsOfLine[0]);
            Integer expectedQuantity = Integer.parseInt(rowsOfLine[1]);
            HashMap ocrResultLine = (HashMap)ocrResult.get(lineNumber);
            if(null == ocrResultLine)
            {
                _results.append("line "+ lineNumber +" doesn't exist on ocr results");
                _results.append(System.getProperty("line.separator"));
                lineNumber++;
                continue;
            }
            Double quantity = (Double)ocrResultLine.get("quantity");
            Integer ocrResultQuantity = quantity.intValue();
            Double ocrResultPrice = (Double)ocrResultLine.get("price");
            if(!expectedPrice.equals(ocrResultPrice))
            {
                _results.append("line "+lineNumber+" - Price: expected "+expectedPrice+", "+"ocr "+ocrResultPrice);
                _results.append(System.getProperty("line.separator"));
                ++countInvalids;
            }
            if(!expectedQuantity.equals(ocrResultQuantity))
            {
                _results.append("line "+lineNumber+" - Quantity: expected "+expectedQuantity+", "+"ocr "+ocrResultQuantity);
                _results.append(System.getProperty("line.separator"));
                ++countInvalids;
            }
            lineNumber++;
        }
        //calculate the accuracy percent
        accuracyPercent = ((lineNumber*2 - countInvalids)/(lineNumber*2)) * 100;
        return accuracyPercent;
    }

    private void PrintParsedNumbers(LinkedHashMap ocrResult) {
        int lineNumber = 0;
        _results.append("Validating Ocr Result:");
        _results.append(System.getProperty("line.separator"));
        for (int i=0; i < ocrResult.size(); i++)
        {
            HashMap ocrResultLine = (HashMap)ocrResult.get(lineNumber);

            Double quantity = (Double)ocrResultLine.get("quantity");
            Integer ocrResultQuantity = quantity.intValue();
            Double ocrResultPrice = (Double)ocrResultLine.get("price");

            _results.append("line "+lineNumber+" - Price: "+ocrResultPrice+", Quantity: "+ocrResultQuantity);
            _results.append(System.getProperty("line.separator"));
            lineNumber++;
        }
    }

    public void AddListenerDilateButton() {
        _dilateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {

                    int iterations = Integer.parseInt(_dilateIterationsEditText.getText().toString());
                    int kernelSize = _dilateKernelSizeSeekBar.getValue();
                    String selectedStructureElement = _kernelTypeSpinner.getSelectedItem().toString();
                    Utils.bitmapToMat(_processedBill, _rgba);
                    ImageProcessingLib.Dilate(_rgba, iterations, kernelSize, selectedStructureElement);
                    Utils.matToBitmap(_rgba, _processedBill);
                    _algorithmsTracing.append("Dilate: iterations " + iterations + ", kernel size " + kernelSize);
                    _algorithmsTracing.append(System.getProperty("line.separator"));
                    _processedImageView.setImageBitmap(_processedBill);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void AddListenerErodeButton() {
        _erodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    int iterations = Integer.parseInt(_erodeIterationsEditText.getText().toString());
                    int kernelSize = _erodeKernelSizeSeekBar.getValue();
                    String selectedStructureElement = _kernelTypeSpinner.getSelectedItem().toString();
                    Utils.bitmapToMat(_processedBill, _rgba);
                    ImageProcessingLib.Erode(_rgba, iterations, kernelSize, selectedStructureElement);
                    Utils.matToBitmap(_rgba, _processedBill);
                    _algorithmsTracing.append("Erode: iterations " + iterations + ", kernel size " + kernelSize);
                    _algorithmsTracing.append(System.getProperty("line.separator"));
                    _processedImageView.setImageBitmap(_processedBill);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void AddListenerOcrOnPreprocessedButton() {
        _ocrOnPreprocessedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    _results.setLength(0);
                    int numOfItems = templateMatcher.priceAndQuantity.size();
                    LinkedHashMap<Rect, Rect>[] connectionsItemsArea = templateMatcher.connectionsItemsArea;
                    ArrayList<ArrayList<Rect>> locationsItemsArea = templateMatcher.locationsItemsArea;
                    ArrayList<Rect> itemLocationsRect = templateMatcher.itemLocationsRect;
                    ArrayList<Bitmap> itemLocationsByteArray = templateMatcher.itemLocationsByteArray;
                    templateMatcher = null;
                    templateMatcher = new TemplateMatcher(tesseractOCREngine, _processedBill);
                    templateMatcher.connectionsItemsArea = connectionsItemsArea;
                    templateMatcher.locationsItemsArea = locationsItemsArea;
                    templateMatcher.itemLocationsRect = itemLocationsRect;
                    templateMatcher.itemLocationsByteArray = itemLocationsByteArray;
                    templateMatcher.Parsing(numOfItems);
                    //ValidateOcrBillResult("Original", _bill);
                    ValidateOcrBillResult("Processed", _processedBill);
                    OpenUserInputDialog();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void AddListenerTemplateMatcherButton() {
        _templateMatcherButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
//                    PreprocessingForTM();
                    templateMatcher = new TemplateMatcher(tesseractOCREngine, _processedBill);
                    templateMatcher.Match();
                    _bill.recycle();
                    _processedBill.recycle();
                    _bill = CreateItemsAreaBitmapFromTMRects(templateMatcher.connectionsItemsArea);
                    _processedBill = _bill.copy(_bill.getConfig(), true);
                    PreprocessingForParsing();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private Bitmap CreateItemsAreaBitmapFromTMRects(LinkedHashMap<Rect, Rect>[] connections) {
        final Bitmap newBill = Bitmap.createBitmap(_processedBill.getWidth(), _processedBill.getHeight(), Bitmap.Config.ARGB_8888);
        final Paint paint = new Paint();
        final Canvas canvas = new Canvas(newBill);

        canvas.drawColor(Color.WHITE);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(50.0f);
        List<Rect> keyListCurrentIndex = null;

        for (int i = 0; i < connections.length; i++)
        {
            if(i == connections.length-1)
            {
                keyListCurrentIndex = new ArrayList<>(connections[i-1].values());
            }
            else
            {
                keyListCurrentIndex = new ArrayList<>(connections[i].keySet());
            }

            for(int j = 0; j < keyListCurrentIndex.size(); j++)
            {
                /**** the following code is for debugging  ****/
//                int xBegin   = keyListCurrentIndex.get(j).left;
//                int xEnd  = keyListCurrentIndex.get(j).right;
//                int yBegin    = keyListCurrentIndex.get(j).top;
//                int yEnd = keyListCurrentIndex.get(j).bottom;
//                Bitmap bitmap = Bitmap.createBitmap(mFullBillProcessedImage, xBegin, yBegin, xEnd-xBegin, yEnd-yBegin);
//                FilesHandler.SaveToJPGFile(bitmap, Constants.IMAGES_PATH + "/rect_" + i + "_" + j + ".jpg");
//                bitmap.recycle();
                /**********************************************/
                canvas.drawBitmap(_processedBillForCreateNewBill, keyListCurrentIndex.get(j), keyListCurrentIndex.get(j), paint);
            }
        }
        return newBill;
    }

    public void AddListenerPrintWordsLocationButton() {
        _printWordsLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    _billWithPrintedRedLines.recycle();
//                    String pathToSave = Constants.IMAGES_PATH;
//                    FilesHandler.SaveToJPGFile(_bill, pathToSave + "/bill.jpg");
                    _billWithPrintedRedLines = PrintWordsRects(_bill, _processedBill);
                    _originalImageView.setImageBitmap(_billWithPrintedRedLines);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void AddListenerSaveProccessedButton() {
        _saveProccessedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
//                    String imagePathToSave = _brandAndModelPath + _restaurantName
//                                            + "nili_24_2_17";
//
//                    WriteCroppedImageToTIFFile(_bill, imagePathToSave);
                    String processedImagePathToSave = _brandAndModelPath + _restaurantName
                            + "processed_nili_24_2_17";

//                    WriteCroppedImageToTIFFile(_billWithPrintedRedLines, processedImagePathToSave);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void AddListenerGenerateBillButton() {
        _generateBillButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                try {
                    RunBillsMainFlow(BILLS_REQUEST_CODE);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void RunBillsMainFlow(int requestCode) {
        try {
            String imagePathToSave = Constants.CAMERA_CAPTURED_TXT_PHOTO_PATH;
            Intent intent = new Intent(getBaseContext(), CameraActivity.class);
            intent.putExtra(CameraActivity.BILLS_CROPPED_PHOTO_EXTRA_NAME, imagePathToSave);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(intent, requestCode);
            }
        } catch (Exception e) {
            Log.e(this.getClass().getSimpleName(), e.getMessage());
        }
    }

    private void OpenUserInputDialog() throws FileNotFoundException {
        LayoutInflater layoutInflaterAndroid = LayoutInflater.from(this);
        View mView = layoutInflaterAndroid.inflate(R.layout.user_input_dialog_box, null);
        TextView textView = (TextView)mView.findViewById(R.id.testOutput);
        textView.setMovementMethod(new ScrollingMovementMethod());
        _results.append(System.getProperty("line.separator"));
        _results.append(_algorithmsTracing);
        textView.setText(_results);
        AlertDialog.Builder alertDialogBuilderUserInput = new AlertDialog.Builder(this);
        alertDialogBuilderUserInput.setView(mView);
        alertDialogBuilderUserInput
                .setCancelable(false)
                .setPositiveButton("Export to file", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialogBox, int id) {
                        // ToDo get user input here
                        try {
                            SetOutputStreamToFile();
                            System.out.println(_results);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialogBox, int id) {
                                dialogBox.cancel();
                            }
                        });
        AlertDialog alertDialogAndroid = alertDialogBuilderUserInput.create();
        alertDialogAndroid.show();
    }

    /**
     * Set output stream for 'System.out.println'. The test prints just to file.
     * Read TEST_README for more info
     * @throws FileNotFoundException
     */
    private void SetOutputStreamToFile() throws FileNotFoundException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("ddMMyyyy_HHmmss");
        String currentDateAndTime = simpleDateFormat.format(new Date());
        File file = new File(_brandAndModelPath + _restaurantName + "/preprocessing_results_" + currentDateAndTime +".txt");
        PrintStream printStreamToFile = new PrintStream(file);
        System.setOut(printStreamToFile);
    }

    private Bitmap GetLastWarpedBill() throws IOException {
        Bitmap bitmap = GetLastCapturedBill();
        BillAreaDetector areaDetector = new BillAreaDetector();
        if (!areaDetector.GetBillCorners(bitmap , _topLeft, _topRight, _buttomRight, _buttomLeft)) {
            Log.d(this.getClass().getSimpleName(), "Failed ot get bounding rectangle automatically.");
            return bitmap;
        }
        /** Preparing Warp Perspective Dimensions **/
        Bitmap warpedBitmap = null;
        try{
            warpedBitmap = ImageProcessingLib.WarpPerspective(bitmap, _topLeft, _topRight, _buttomRight, _buttomLeft);
        }
        catch (Exception ex){
            Log.d(this.getClass().getSimpleName(), "Failed to warp perspective");
            return bitmap;
        }
        bitmap.recycle();
        return warpedBitmap;
    }

    private Bitmap GetLastCapturedBill() throws IOException {
        byte[] bytes = FilesHandler.ImageTxtFile2ByteArray(Constants.CAMERA_CAPTURED_TXT_PHOTO_PATH);
        Bitmap bitmap = FilesHandler.ByteArrayToBitmap(bytes);
        bitmap = FilesHandler.Rotating(bitmap);
        return bitmap;
    }

    public Bitmap PrintWordsRects(Bitmap bitmap, Bitmap _processedBill){
        List<Rect> words;
        Bitmap printedBitmap = Bitmap.createBitmap(bitmap);
        try{
            tesseractOCREngine.SetImage(_processedBill);
            List<Rect> lineRects = tesseractOCREngine.GetTextlines();

            /************ the following is waiting for GC to finish his job. ********/
            /************ without it the red lines will not be printed. *************/
//            Thread.sleep(50);
            /**************************/
            Paint paint = new Paint();
            Canvas canvas = new Canvas(printedBitmap);
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);

            for (Rect line: lineRects) {
                tesseractOCREngine.SetRectangle(line);
                words = tesseractOCREngine.GetWords();
                for (Rect rect : words) {
                    canvas.drawRect(rect, paint);
                }
            }
        }
        catch(Exception ex){
            Log.d(this.getClass().getSimpleName(), "Failed to map numbered values to location. Error: " + ex.getMessage());
            return null;
        }
            return printedBitmap;
        }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        switch (resultCode) {
            case RESULT_OK:
                _bill.recycle();
                try {
                    _bill = GetLastCapturedBill();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                _userCropFinished = new Button(this);
                _userCropFinished.setText("Done");
                _userCropFinished.setOnClickListener(this);
                _dragRectView = new DragRectView(this);
                BillAreaDetector areaDetector = new BillAreaDetector();

                if (!areaDetector.GetBillCorners(_bill , _topLeft, _topRight, _buttomRight, _buttomLeft)) {
                    Log.d(this.getClass().getSimpleName(), "Failed ot get bounding rectangle automatically.");
                    _dragRectView.TopLeft = null;
                    _dragRectView.TopRight = null;
                    _dragRectView.ButtomLeft = null;
                    _dragRectView.ButtomRight = null;
                }
                else {
                    int x = (int) Math.round((720.0/_bill.getWidth())* _topLeft.x);
                    int y = (int) Math.round((1118.0/_bill.getHeight())* _topLeft.y);
                    _dragRectView.TopLeft = new android.graphics.Point(x, y);

                    x = (int) Math.round((720.0/_bill.getWidth())* _topRight.x);
                    y = (int) Math.round((1118.0/_bill.getHeight())* _topRight.y);
                    _dragRectView.TopRight = new android.graphics.Point(x, y);

                    x = (int) Math.round((720.0/_bill.getWidth())* _buttomRight.x);
                    y = (int) Math.round((1118.0/_bill.getHeight())* _buttomRight.y);
                    _dragRectView.ButtomRight = new android.graphics.Point(x, y);

                    x = (int) Math.round((720.0/_bill.getWidth())* _buttomLeft.x);
                    y = (int) Math.round((1118.0/_bill.getHeight())* _buttomLeft.y);
                    _dragRectView.ButtomLeft = new android.graphics.Point(x, y);
                }

                BitmapDrawable bitmapDrawable = new BitmapDrawable(_bill);
                _dragRectView.setBackground(bitmapDrawable);
                _dragRectView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.BOTTOM;
                _userCropFinished.setLayoutParams(params);
                _testsDebugView.setVisibility(GONE);
                _emptyRelativeLayoutView.setVisibility(View.VISIBLE);
                _emptyRelativeLayoutView.addView(_dragRectView);
                _emptyRelativeLayoutView.addView(_userCropFinished);
                break;
            default:
                //mBeginBillSplitFlowButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View v) {

        if(v == _userCropFinished){
            double stretchFactorX = (1.0 * _bill.getWidth()) / _dragRectView.getBackground().getBounds().width();
            double stretchFactorY = (1.0 * _bill.getHeight()) / _dragRectView.getBackground().getBounds().height();

            double x = _dragRectView.TopLeft.x * stretchFactorX;
            double y = _dragRectView.TopLeft.y * stretchFactorY ;
            _topLeft = new Point(x,y);

            x = _dragRectView.TopRight.x * stretchFactorX;
            y = _dragRectView.TopRight.y * stretchFactorY;
            _topRight = new Point(x,y);

            x = _dragRectView.ButtomLeft.x * stretchFactorX;
            y = _dragRectView.ButtomLeft.y * stretchFactorY;
            _buttomLeft = new Point(x,y);

            x = _dragRectView.ButtomRight.x * stretchFactorX;
            y = _dragRectView.ButtomRight.y * stretchFactorY;
            _buttomRight = new Point(x,y);

            /** Preparing Warp Perspective Dimensions **/
            Bitmap warpedBitmap = null;
            try{
                warpedBitmap = ImageProcessingLib.WarpPerspective(_bill, _topLeft, _topRight, _buttomRight, _buttomLeft);
            }
            catch (Exception ex){
                Log.d(this.getClass().getSimpleName(), "Failed to warp perspective");
                return;
            }
            _billWithPrintedRedLines.recycle();
            _processedBill.recycle();
            _processedBillForCreateNewBill.recycle();
            _bill = warpedBitmap.copy(warpedBitmap.getConfig(), true);
            _billWithPrintedRedLines = _bill.copy(_bill.getConfig(), true);
            _emptyRelativeLayoutView.removeView(_dragRectView);
            _emptyRelativeLayoutView.removeView(_userCropFinished);
            _emptyRelativeLayoutView.setVisibility(GONE);
            _testsDebugView.setVisibility(View.VISIBLE);
            _originalImageView.setImageBitmap(_bill);
            warpedBitmap.recycle();
            PreprocessingForTM();
        }
    }
}
