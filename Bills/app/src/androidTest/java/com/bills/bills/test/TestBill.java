package com.bills.bills.test;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;

import com.bills.billslib.Contracts.Constants;
import com.bills.billslib.Contracts.Enums.Language;
import com.bills.billslib.Core.BillAreaDetector;
import com.bills.billslib.Core.ImageProcessingLib;
import com.bills.billslib.Core.TemplateMatcher;
import com.bills.billslib.Core.TesseractOCREngine;
import com.bills.billslib.Utilities.FilesHandler;

import org.beyka.tiffbitmapfactory.TiffBitmapFactory;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Created by avielavr on 5/19/2017.
 */

public class TestBill extends Thread{
    String _rootBrandModelDirectory;
    String _restaurant;
    String _bill;
    StringBuilder _results;

    public TestBill(String rootBrandModelDirectory, String restaurant, String bill)
    {
        _rootBrandModelDirectory = rootBrandModelDirectory;
        _restaurant = restaurant;
        _bill = bill;
        _results = new StringBuilder();
    }

    @Override
    public void run(){
        synchronized (this) {
            TemplateMatcher templateMatcher;
            TesseractOCREngine tesseractOCREngine;
            tesseractOCREngine = new TesseractOCREngine();
            try {
                tesseractOCREngine.Init(Constants.TESSERACT_SAMPLE_DIRECTORY, Language.Hebrew);
            } catch (Exception e) {
                e.printStackTrace();
            }

            String expectedTxtFileName = _restaurant.toString() + ".txt";
            List<String> expectedBillTextLines = null;

            try {
                expectedBillTextLines = FilesHandler.ReadTxtFile(_rootBrandModelDirectory + _restaurant + "/" + expectedTxtFileName);
            } catch (IOException e) {
                e.printStackTrace();
            }

            _results.append("Test of " + _bill + System.getProperty("line.separator"));
            Bitmap billBitmap = TifToBitmap(_bill);
            Bitmap warpedBitmap = WarpPerspective(billBitmap, _bill);
            Mat warpedMat = new Mat();
            Mat warpedMatCopy = new Mat();
            Utils.bitmapToMat(warpedBitmap, warpedMat);
            Utils.bitmapToMat(warpedBitmap, warpedMatCopy);
            Bitmap processedBillBitmap = Bitmap.createBitmap(warpedMat.width(), warpedMat.height(), Bitmap.Config.ARGB_8888);
            Mat processedBillMat = ImageProcessingLib.PreprocessingForTM(warpedMat);
            Utils.matToBitmap(processedBillMat, processedBillBitmap);
//            File file = new File(currBill);
//            String pathToSave = file.getParent();
//            FilesHandler.SaveToJPGFile(processedBillMat, pathToSave + "/processedBillMat.jpg");
            templateMatcher = new TemplateMatcher(tesseractOCREngine, processedBillBitmap);
            templateMatcher.Match();

            Mat processedItemsAreaMat = ImageProcessingLib.PreprocessingForParsing(warpedMatCopy);
            int numOfItems = templateMatcher.priceAndQuantity.size();
            LinkedHashMap<Rect, Rect>[] connectionsItemsArea = templateMatcher.connectionsItemsArea;
            ArrayList<ArrayList<Rect>> locationsItemsArea = templateMatcher.locationsItemsArea;
            ArrayList<Rect> itemLocationsRect = templateMatcher.itemLocationsRect;
            ArrayList<Bitmap> itemLocationsByteArray = templateMatcher.itemLocationsByteArray;
            /***** we use processedBillBitmap second time to prevent another Bitmap allocation due to *****/
            /***** Out Of Memory when running 4 threads parallel                                      *****/
            Utils.matToBitmap(processedItemsAreaMat, processedBillBitmap);
            templateMatcher = new TemplateMatcher(tesseractOCREngine, processedBillBitmap);
            templateMatcher.connectionsItemsArea = connectionsItemsArea;
            templateMatcher.locationsItemsArea = locationsItemsArea;
            templateMatcher.itemLocationsRect = itemLocationsRect;
            templateMatcher.itemLocationsByteArray = itemLocationsByteArray;
            templateMatcher.ParsingItemsAreaWithRects(numOfItems);
            LinkedHashMap ocrResultCroppedBill = GetOcrResults(templateMatcher);
            CompareExpectedToOcrResult(ocrResultCroppedBill, expectedBillTextLines);

            billBitmap.recycle();
            warpedBitmap.recycle();
            processedBillBitmap.recycle();
            warpedMat.release();
            warpedMatCopy.release();
            processedBillMat.release();
            processedItemsAreaMat.release();
            tesseractOCREngine.End();

            synchronized (System.out) {
                System.out.println(_results);
            }
        }
    }

    /**
     *
     * @param tifFilePath path of tif file
     * @return rotated bitmap
     */
    private Bitmap TifToBitmap(String tifFilePath) {
        BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
        Bitmap bitmap = null;
        bitmapOptions.inMutable = true;
        bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        TiffBitmapFactory.Options options = new TiffBitmapFactory.Options();
        options.inAvailableMemory = 1024 * 1024 * 10 * 3; //30 mb
        File file = new File(tifFilePath);
        bitmap = TiffBitmapFactory.decodeFile(file, options);

        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        bitmap.recycle();

        return rotated;
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
     * Crop the bill from original capture and warp it
     * @param bill original capture of bill
     * @param billFullName bill full name
     * @return warped bill
     */
    private Bitmap WarpPerspective(Bitmap bill, String billFullName) {
        Point mTopLeft = new Point();
        Point mTopRight = new Point();
        Point mButtomLeft = new Point();
        Point mButtomRight = new Point();
        BillAreaDetector.GetBillCorners(bill , mTopLeft, mTopRight, mButtomRight, mButtomLeft);

        /** Preparing Warp Perspective Dimensions **/
        int newWidth = (int) Math.max(mButtomRight.x - mButtomLeft.x, mTopRight.x - mTopLeft.x);
        int newHeight = (int) Math.max(mButtomRight.y - mTopRight.y, mButtomLeft.y - mTopLeft.y);
        int xBegin = (int) Math.min(mTopLeft.x, mButtomLeft.x);
        int yBegin = (int) Math.min(mTopLeft.y, mTopRight.y);
        Bitmap resizedBitmap = Bitmap.createBitmap(bill, xBegin, yBegin, newWidth, newHeight);
        Bitmap warpedBitmap = Bitmap.createBitmap(newWidth , newHeight, bill.getConfig());
        mTopLeft.x = mTopLeft.x - xBegin;
        mTopLeft.y = mTopLeft.y - yBegin;
        mTopRight.x = mTopRight.x - xBegin;
        mTopRight.y = mTopRight.y - yBegin;
        mButtomRight.x = mButtomRight.x - xBegin;
        mButtomRight.y = mButtomRight.y - yBegin;
        mButtomLeft.x = mButtomLeft.x - xBegin;
        mButtomLeft.y = mButtomLeft.y - yBegin;

        if(!ImageProcessingLib.WarpPerspective(resizedBitmap, warpedBitmap, mTopLeft, mTopRight, mButtomRight, mButtomLeft)) {
            _results.append("Failed to warp perspective " + billFullName + System.getProperty("line.separator"));
            warpedBitmap.recycle();
            resizedBitmap.recycle();
            return null;
        }

//        File file = new File(billFullName);
//        String warpPathToSave = file.getParent();
//        FilesHandler.SaveToJPGFile(warpedBitmap, warpPathToSave + "/warped.jpg");
        resizedBitmap.recycle();
        return warpedBitmap;
    }

    /**
     * comparing line to line ocr results of bill vs expected txt file
     * @param ocrResultCroppedBill ocr results of cropped bill
     * @param expectedBillTextLines expected bill lines from txt file
     */
    private void CompareExpectedToOcrResult(LinkedHashMap ocrResultCroppedBill, List<String> expectedBillTextLines) {
        _results.append("Validating Ocr Result" + System.getProperty("line.separator"));
        Integer accuracyPercent = Compare(ocrResultCroppedBill, expectedBillTextLines);

        if(ocrResultCroppedBill.size() != expectedBillTextLines.size())
        {
            _results.append("ocrResultCroppedBill contains "+ ocrResultCroppedBill.size() + " lines, but" +
                    " expectedBillTextLines contains "+ expectedBillTextLines.size()+" lines" + System.getProperty("line.separator"));
        }

        _results.append("Accuracy is " + accuracyPercent + "%" + System.getProperty("line.separator"));

//        _testsCount++;
//        _testsAccuracyPercentSum += accuracyPercent;
    }

    /**
     *
     * @param ocrResult ocr result of bill included price and quantity
     * @param expectedBillTextLines expected bill lines from txt file
     * @return true in case of equal results. false if unequal
     */
    private Integer Compare(LinkedHashMap ocrResult, List<String> expectedBillTextLines) {
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
        return accuracyPercent.intValue();
    }
}

