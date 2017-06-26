package com.bills.bills.test;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.Pair;

import com.bills.billslib.Contracts.Constants;
import com.bills.billslib.Contracts.Enums.Language;
import com.bills.billslib.Core.ImageProcessingLib;
import com.bills.billslib.Core.TemplateMatcher;
import com.bills.billslib.Core.TesseractOCREngine;
import com.bills.billslib.Utilities.FilesHandler;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Queue;

/**
 * Created by avielavr on 5/19/2017.
 */

public class TestBill extends Thread{
    String _rootBrandModelDirectory;
    String _restaurant;
    String _billFullName;
    StringBuilder _results;
    Queue<Double> _accuracyPercentQueue;
    Queue<Pair> _passedResultsQueue;
    Queue<Pair> _failedResultsQueue;

    public TestBill(String rootBrandModelDirectory, String restaurant, String bill,
                    Queue<Double> accuracyPercentQueue,
                    Queue<Pair> passedResultsQueue,
                    Queue<Pair> failedResultsQueue)
    {
        _rootBrandModelDirectory = rootBrandModelDirectory;
        _restaurant = restaurant;
        _billFullName = bill;
        _results = new StringBuilder();
        _accuracyPercentQueue = accuracyPercentQueue;
        _passedResultsQueue = passedResultsQueue;
        _failedResultsQueue = failedResultsQueue;
    }

    @Override
    public void run(){
        synchronized (this) {
            TemplateMatcher templateMatcher;
            TesseractOCREngine tesseractOCREngine;
            tesseractOCREngine = new TesseractOCREngine();
            String expectedTxtFileName = _restaurant.toString() + ".txt";
            List<String> expectedBillTextLines = null;

            if (!OpenCVLoader.initDebug()) {
//                Log.d(Tag, "Failed to initialize OpenCV.");
//                return false;
            }
            Mat warpedMat = new Mat();
            Mat warpedMatCopy = new Mat();

            try {
                _results.append(System.getProperty("line.separator") + "Test of " + _billFullName + System.getProperty("line.separator"));
                tesseractOCREngine.Init(Constants.TESSERACT_SAMPLE_DIRECTORY, Language.Hebrew);
                expectedBillTextLines = FilesHandler.ReadTxtFile(_rootBrandModelDirectory + _restaurant + "/" + expectedTxtFileName);
                warpedMat = FilesHandler.GetWarpedBillMat(_billFullName);
                warpedMatCopy = warpedMat.clone();
//                File file = new File(_billFullName);
//                String pathToSave = file.getParent();
//                FilesHandler.SaveToJPGFile(billBitmap, pathToSave + "/billBitmap.jpg");
            } catch (Exception e) {
                e.printStackTrace();
            }

            Bitmap processedBillBitmap = Bitmap.createBitmap(warpedMat.width(), warpedMat.height(), Bitmap.Config.ARGB_8888);
            ImageProcessingLib.PreprocessingForTM(warpedMat);
            Utils.matToBitmap(warpedMat, processedBillBitmap);
            templateMatcher = new TemplateMatcher(tesseractOCREngine, processedBillBitmap);
            try{
                templateMatcher.Match();
            }
            catch (Exception e){
                _results.append(" " + Log.getStackTraceString(e)+ System.getProperty("line.separator"));
                String filename = _billFullName.substring(_billFullName.lastIndexOf("/")+1);
                _failedResultsQueue.add(new Pair( _restaurant+filename, _results));
                return;
            }

            ImageProcessingLib.PreprocessingForParsing(warpedMatCopy);
            int numOfItems = templateMatcher.priceAndQuantity.size();
            /***** we use processedBillBitmap second time to prevent another Bitmap allocation due to *****/
            /***** Out Of Memory when running 4 threads parallel                                      *****/
            Utils.matToBitmap(warpedMatCopy, processedBillBitmap);
            templateMatcher.InitializeBeforeSecondUse(processedBillBitmap);
            templateMatcher.Parsing(numOfItems);
            LinkedHashMap ocrResultCroppedBill = GetOcrResults(templateMatcher);
            CompareExpectedToOcrResult(ocrResultCroppedBill, expectedBillTextLines);
            processedBillBitmap.recycle();
            warpedMat.release();
            warpedMatCopy.release();
            tesseractOCREngine.End();
            String filename = _billFullName.substring(_billFullName.lastIndexOf("/")+1);
            _passedResultsQueue.add(new Pair(_restaurant+filename, _results));
        }
    }

    /**
     *
     * @param templateMatcher TM structure
     * @return LinkedHashMap structure
     */
    private LinkedHashMap GetOcrResults(TemplateMatcher templateMatcher) {
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
        _results.append("Validating Ocr Result" + System.getProperty("line.separator"));
        Double accuracyPercent = Compare(ocrResultCroppedBill, expectedBillTextLines);

        if(ocrResultCroppedBill.size() != expectedBillTextLines.size())
        {
            _results.append("ocrResultCroppedBill contains "+ ocrResultCroppedBill.size() + " lines, but" +
                    " expectedBillTextLines contains "+ expectedBillTextLines.size()+" lines" + System.getProperty("line.separator"));
        }

        String formattedAccuracyPercent = String.format("%.02f", accuracyPercent);
        _results.append("Accuracy is " + formattedAccuracyPercent + "%" + System.getProperty("line.separator"));
        _accuracyPercentQueue.add(accuracyPercent);
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
                _results.append("line "+ lineNumber +" doesn't exist on ocr results" + System.getProperty("line.separator"));
                countInvalids++;
                lineNumber++;
                continue;
            }
            Double quantity = (Double)ocrResultLine.get("quantity");
            Integer ocrResultQuantity = quantity.intValue();
            Double ocrResultPrice = (Double)ocrResultLine.get("price");
            if(!expectedPrice.equals(ocrResultPrice))
            {
                _results.append("line "+lineNumber+" - Price: expected "+expectedPrice+", "+"ocr "+ocrResultPrice
                        + System.getProperty("line.separator"));
                ++countInvalids;
            }
            if(!expectedQuantity.equals(ocrResultQuantity))
            {
                _results.append("line "+lineNumber+" - Quantity: expected "+expectedQuantity+", "+"ocr "+ocrResultQuantity
                        + System.getProperty("line.separator"));
                ++countInvalids;
            }
            lineNumber++;
        }
        //calculate the accuracy percent
        accuracyPercent = ((lineNumber*2 - countInvalids)/(lineNumber*2)) * 100;
        return accuracyPercent;
    }
}

