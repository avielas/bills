package com.bills.billslib.Core;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import com.bills.billslib.Camera.IOnCameraFinished;
import com.bills.billslib.Contracts.IOcrEngine;
import com.googlecode.leptonica.android.WriteFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by mvalersh on 12/2/2016.
 */


public class TemplateMatcher {
    private IOcrEngine mOCREngine;
    private Bitmap mFullBillProcessedImage;
    private Bitmap mFullBillImage;
    private int itemColumn;
    public final ArrayList<Double[]> priceAndQuantity = new ArrayList<>();
    public final ArrayList<Rect> itemLocationsRect = new ArrayList<>();
    public final ArrayList<byte[]> itemLocationsByteArray = new ArrayList<>();
    Boolean secondColumnIsConnected;
    Boolean oneBeforeLastColumnConnected;

    /**
     * @param ocrEngine     initialized ocr engine
     * @param fullBillPreprocessedImage full, processed and warped bill image
     */
    public TemplateMatcher(IOcrEngine ocrEngine, Bitmap fullBillPreprocessedImage) {
        if (!ocrEngine.Initialized()) {
            throw new IllegalArgumentException("OCREngine must be initialized.");
        }
        mOCREngine = ocrEngine;
        mFullBillProcessedImage = fullBillPreprocessedImage;
        secondColumnIsConnected = false;
        oneBeforeLastColumnConnected = false;
    }

    public TemplateMatcher(IOcrEngine ocrEngine, Bitmap fullBillPreprocessedImage, Bitmap fullBillImage) {
        if (!ocrEngine.Initialized()) {
            throw new IllegalArgumentException("OCREngine must be initialized.");
        }
        mOCREngine = ocrEngine;
        mFullBillProcessedImage = fullBillPreprocessedImage;
        mFullBillImage = fullBillImage;
        secondColumnIsConnected = false;
        oneBeforeLastColumnConnected = false;
    }

    public boolean Match() {
        boolean success;
        ArrayList<ArrayList<Rect>> locations = GetWordLocations(mFullBillProcessedImage);
        int lineIndex = 0;
        //print all word locations to Log
        for (ArrayList<Rect> line : locations) {
            String str = "";

            for (Rect word : line) {
                if(word == null){
                    continue;
                }
                str += word.right + "--> ";
            }
            Log.d(this.getClass().getSimpleName(), "Line " + lineIndex++ + ": " + str);
        }

        LinkedHashMap<Rect, Rect>[] connections = new LinkedHashMap[locations.size() - 1];

        SetConnections(locations, connections);

        int start = -1;
        List<Map.Entry<Integer, Integer>> startEndOfAreasList = new ArrayList<>();
        //find largest "connected" area. Two lines are connected if there are at least two words in "similar" location which connected
        IdentifyOptionalConnectedAreas(locations, connections, start, startEndOfAreasList);

        int maxSizeIndex = Integer.MIN_VALUE;
        for(int i = 0, maxSize = Integer.MIN_VALUE; i < startEndOfAreasList.size(); i++){
            if(maxSize < Math.abs(startEndOfAreasList.get(i).getValue() - startEndOfAreasList.get(i).getKey())){
                maxSize = Math.abs(startEndOfAreasList.get(i).getValue() - startEndOfAreasList.get(i).getKey());
                maxSizeIndex = i;
            }
        }

        int itemsAreaStart = startEndOfAreasList.get(maxSizeIndex).getKey();
        int itemsAreaEnd = startEndOfAreasList.get(maxSizeIndex).getValue();

        try {
            GetPriceAndQuantity(itemsAreaStart, itemsAreaEnd, connections, locations);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SetItemsLocations(itemsAreaStart, itemsAreaEnd, connections, locations);
        success = true;

        return success;
    }

    private boolean SaveToFile(Bitmap bmp, String path){
        FileOutputStream out = null;
        try {
            File file = new File(path);
            if(file.exists()){
                file.delete();
            }
            out = new FileOutputStream(path);

            // bmp is your Bitmap instance, PNG is a lossless format, the compression factor (100) is ignored
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public Bitmap MatchWhichReturnCroppedItemsArea() {
        boolean success;
        ArrayList<ArrayList<Rect>> locations = GetWordLocations(mFullBillProcessedImage);
        int lineIndex = 0;
        //print all word locations to Log
        for (ArrayList<Rect> line : locations) {
            String str = "";

            for (Rect word : line) {
                if(word == null){
                    continue;
                }
                str += word.right + "--> ";
            }
            Log.d(this.getClass().getSimpleName(), "Line " + lineIndex++ + ": " + str);
        }

        LinkedHashMap<Rect, Rect>[] connections = new LinkedHashMap[locations.size() - 1];

        SetConnections(locations, connections);

        int start = -1;
        List<Map.Entry<Integer, Integer>> startEndOfAreasList = new ArrayList<>();
        //find largest "connected" area. Two lines are connected if there are at least two words in "similar" location which are connected
        IdentifyOptionalConnectedAreas(locations, connections, start, startEndOfAreasList);

        int maxSizeIndex = Integer.MIN_VALUE;
        for(int i = 0, maxSize = Integer.MIN_VALUE; i < startEndOfAreasList.size(); i++){
            if(maxSize < Math.abs(startEndOfAreasList.get(i).getValue() - startEndOfAreasList.get(i).getKey())){
                maxSize = Math.abs(startEndOfAreasList.get(i).getValue() - startEndOfAreasList.get(i).getKey());
                maxSizeIndex = i;
            }
        }

        int itemsAreaStart = startEndOfAreasList.get(maxSizeIndex).getKey();
        int itemsAreaEnd = startEndOfAreasList.get(maxSizeIndex).getValue();

        try {
            GetPriceAndQuantity(itemsAreaStart, itemsAreaEnd, connections, locations);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SetItemsLocations(itemsAreaStart, itemsAreaEnd, connections, locations);
        success = true;

        return CreatingImageFromRects(startEndOfAreasList, connections);
    }

    public void ParsingItemsArea(int numOfItems) {
        ArrayList<ArrayList<Rect>> locations = GetWordLocations(mFullBillProcessedImage);

        LinkedHashMap<Rect, Rect>[] connections = new LinkedHashMap[locations.size() - 1];

        SetConnections(locations, connections);

        int itemsAreaStart = 0;
        int itemsAreaEnd = numOfItems - 1;

        try {
            GetPriceAndQuantity(itemsAreaStart, itemsAreaEnd, connections, locations);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SetItemsLocations(itemsAreaStart, itemsAreaEnd, connections, locations);
    }

    private void SetConnections(ArrayList<ArrayList<Rect>> locations, LinkedHashMap<Rect, Rect>[] connections) {
        for (int i = 0; i < locations.size()-1; i++){
            connections[i] = new LinkedHashMap<>();
            for(int j = 0; j < locations.get(i).size(); j++){
                Rect word = locations.get(i).get(j);
                for(int k = 0; k < locations.get(i+1).size(); k++){  //compare current line word to all next line words until match
                    Rect nextLineWord = locations.get(i+1).get(k);
                    if(InRange(word, nextLineWord, 10)){
                        connections[i].put(word, nextLineWord);
                        break;
                    }
                }
            }
        }
    }

    private void SetItemsLocations(int itemsAreaStart, int itemsAreaEnd,
                                   LinkedHashMap<Rect, Rect>[] connections,
                                   ArrayList<ArrayList<Rect>> locations) {

        Rect[][] lineConnectionRects = new Rect[itemsAreaEnd - itemsAreaStart + 1][connections[itemsAreaStart].size()];
        for(int i = itemsAreaStart; i < itemsAreaEnd; i++){
            try {
                if(i == itemsAreaEnd){
                    //in case of last items area lines, we making a special calculation
//                    connection = (Rect) (connections[i-1].values().toArray()[j]);
                    connections[i-1].values().toArray(lineConnectionRects[i - itemsAreaStart -1]);
                }
                else {
                    connections[i].keySet().toArray(lineConnectionRects[i - itemsAreaStart]);
                }
            }catch (Exception ex){
                //TODO: handle exception
            }
        }

        try{
            int i = 0;
            for(Map.Entry<Rect, Rect> entry : connections[itemsAreaEnd - 1].entrySet()){
                lineConnectionRects[itemsAreaEnd - itemsAreaStart][i++] = entry.getValue();
            }
        }
        catch (Exception ex){
            ex.printStackTrace();
        }

        for (int i = 0; i < lineConnectionRects.length; i++){
            int prevConnectedRect = 0;
            for(int j = 0; j < locations.get(i + itemsAreaStart).size(); j++){
                if(lineConnectionRects[i][itemColumn] == locations.get(i + itemsAreaStart).get(j)){
                    Rect itemLocation = new Rect(
                            locations.get(i + itemsAreaStart).get(prevConnectedRect + 1).left,
                            locations.get(i + itemsAreaStart).get(prevConnectedRect + 1).top,
                            locations.get(i + itemsAreaStart).get(j).right,
                            locations.get(i + itemsAreaStart).get(prevConnectedRect + 1).bottom);
                    itemLocationsRect.add(itemLocation);

                    /** the following code save product name as ByteArray for later serialize to BillSummarizer **/
                    mOCREngine.SetRectangle(itemLocation);
                    Rect itemsRect = null;
                    try {
                        //TODO crashing here sometimes
                        List<Rect> textLines =  mOCREngine.GetTextlines();
                        itemsRect = textLines.get(0);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    int xBegin = itemsRect.left;
                    int xEnd = itemsRect.right;
                    int yBegin = itemsRect.top;
                    int yEnd = itemsRect.bottom;
                    Bitmap bitmap = Bitmap.createBitmap(mFullBillProcessedImage, xBegin, yBegin, xEnd-xBegin, yEnd-yBegin);

                    ByteArrayOutputStream stream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    itemLocationsByteArray.add(stream.toByteArray());
                    bitmap.recycle();
                    stream.reset();
                    /****** end ******/
                    break;
                }
                //TODO I added 'i + itemsAreaStart == connections.length ||'
                //TODO just due to parsing items area bug (second call of TM). It should be refactored ASAP !!!
                if(i + itemsAreaStart == connections.length ||
                   connections[i + itemsAreaStart].containsKey(locations.get(i + itemsAreaStart).get(j))){
                    prevConnectedRect = j;
                }
                else
                if(connections[i + itemsAreaStart - 1].containsValue(locations.get(i + itemsAreaStart).get(j))){
                    prevConnectedRect = j;
                }
            }
        }
    }

    private void IdentifyOptionalConnectedAreas(ArrayList<ArrayList<Rect>> locations, LinkedHashMap<Rect, Rect>[] connections, int start, List<Map.Entry<Integer, Integer>> startEndOfAreasList) {
        for(int i = 0; i < locations.size() - 1; i++){
            if(IsLinesConnected(locations.get(i), locations.get(i+1), connections[i])){
                if(start == -1 ){
                    start = i;
                }
            }
            else{
                if(start >= 0){
                    start = ValidatingTitleLineLocation(start, connections, locations);
                    /**** start+1 because title always included at items area ****/
                    startEndOfAreasList.add(new AbstractMap.SimpleEntry<>(start, i));
                    Log.d(this.getClass().getSimpleName(), "Found area: " + start + "-->" + i);
                    start = -1;
                    secondColumnIsConnected = false;
                    oneBeforeLastColumnConnected = false;
                }
            }
        }
    }

    private int ValidatingTitleLineLocation(int start, LinkedHashMap<Rect, Rect>[] connections, ArrayList<ArrayList<Rect>> locations) {
        Boolean isTitleAtItemsArea;

        isTitleAtItemsArea = IsTitleAtItemsArea(start, connections, locations);

        return isTitleAtItemsArea ? start + 1 : start;
    }

    private Boolean IsTitleAtItemsArea(int start, LinkedHashMap<Rect, Rect>[] connections, ArrayList<ArrayList<Rect>> locations) {
        if(start - 1 < 0)
        {
            return true;
        }

        int numberOfConnections = connections[start].size();
        if(3 <= numberOfConnections || 7 <= numberOfConnections)
        {
            if(IsTitleConnected(locations.get(start), connections[start-1]))
            {
                return false;
            }
            return true;
        }
        return true;
    }

    private Boolean IsTitleConnected(ArrayList<Rect> firstLineItemsArea, LinkedHashMap<Rect, Rect> titleConnections) {

        if(firstLineItemsArea.size() < 3 || titleConnections.size() < 3)
        {
            return false;
        }

        Boolean isFirstColumnConnected = IsConnected(titleConnections, firstLineItemsArea.get(0));
        Boolean isLastColumnConnected = IsConnected(titleConnections, firstLineItemsArea.get(firstLineItemsArea.size() - 1));
        Boolean isSecondOrOneBeforeLastColumnConnected = false;

        if(secondColumnIsConnected)
        {
            isSecondOrOneBeforeLastColumnConnected = IsConnected(titleConnections, firstLineItemsArea.get(1));
        }
        else if(oneBeforeLastColumnConnected)
        {
            isSecondOrOneBeforeLastColumnConnected = IsConnected(titleConnections, firstLineItemsArea.get(firstLineItemsArea.size() - 2));
        }

        return isFirstColumnConnected && isLastColumnConnected && isSecondOrOneBeforeLastColumnConnected;
    }

    private Boolean IsConnected(LinkedHashMap<Rect, Rect> connections, Rect rect) {
        for (Map.Entry<Rect, Rect> connection : connections.entrySet()) {
            if(connection.getValue() == rect)
            {
                return true;
            }
        }
        return false;
    }

    private void GetPriceAndQuantity(int itemsAreaStart, int itemsAreaEnd,
                                     LinkedHashMap<Rect, Rect>[] connections,
                                     ArrayList<ArrayList<Rect>> locations) throws Exception {
        mOCREngine.SetImage(mFullBillProcessedImage);

        mOCREngine.SetNumbersOnlyFormat();

        double[][] parsedNumbersArray = new double[itemsAreaEnd - itemsAreaStart + 1][connections[itemsAreaStart].size()];

        GetParsedNumbers(itemsAreaStart, itemsAreaEnd, connections, parsedNumbersArray, locations);

        int[] parsedNumbersArraySizes = new int[connections[itemsAreaStart].size()];

        GetParsedNumbersSizes(parsedNumbersArray, parsedNumbersArraySizes);

        int[] sortedParsedNumbersArraySizes = new int[parsedNumbersArraySizes.length];
        System.arraycopy(parsedNumbersArraySizes, 0, sortedParsedNumbersArraySizes, 0, parsedNumbersArraySizes.length);
        Arrays.sort(sortedParsedNumbersArraySizes);


        int priceColumn = Integer.MIN_VALUE;
        int quantityColumn = Integer.MIN_VALUE;

        //find quantity column
        for(int i = 0; i < parsedNumbersArraySizes.length; i++){
            if(parsedNumbersArraySizes[i] == sortedParsedNumbersArraySizes[1]){
                quantityColumn = i;
                break;
            }
        }

        //find price column
        for(int i = 0; i < parsedNumbersArraySizes.length; i++){
            if(parsedNumbersArraySizes[i] == sortedParsedNumbersArraySizes[2]){
                priceColumn = i;
                break;
            }
        }

        for(int i = 0; i < parsedNumbersArray.length; i++){
            priceAndQuantity.add(i, new Double[]{parsedNumbersArray[i][priceColumn], parsedNumbersArray[i][quantityColumn]});
        }
    }

    private void GetParsedNumbersSizes(double[][] parsedNumbersArray, int[] parsedNumbersArraySizes) {
        for(int i = 0; i < parsedNumbersArray.length; i++){
            double[] sortedLine = new double[parsedNumbersArray[0].length];
            System.arraycopy(parsedNumbersArray[i], 0, sortedLine, 0, sortedLine.length);
            Arrays.sort(sortedLine);

            for(int j = 0; j < parsedNumbersArray[0].length; j++){
                for(int k = 0; k < sortedLine.length; k++){
                    if(sortedLine[k] == parsedNumbersArray[i][j]){
                        parsedNumbersArraySizes[j] += k;
                    }
                }
            }
        }
    }

    private void GetParsedNumbers(int itemsAreaStart, int itemsAreaEnd,
                                  LinkedHashMap<Rect, Rect>[] connections,
                                  double[][] parsedNumbersArray,
                                  ArrayList<ArrayList<Rect>> locations) throws Exception {
        int i;
        itemColumn = CalculateIndexOfItemsColumn(itemsAreaStart, itemsAreaEnd, connections, locations);
        for(i = itemsAreaStart; i < itemsAreaEnd; i++) {
            int j = -1;
            for (Rect entry : connections[i].keySet()) {
                j++;
                if(j == itemColumn){
                    parsedNumbersArray[i - itemsAreaStart][j] = -1;
                    continue;
                }
                Double parsedNumber = 0.0;
                mOCREngine.SetRectangle(entry);
                try{
                    String parsedNumberString = mOCREngine.GetUTF8Text();
                    parsedNumberString = CleaningParsedNumber(parsedNumberString);
                    parsedNumber = Double.parseDouble(parsedNumberString);
                }
                catch(Exception ex){
                    parsedNumbersArray[i - itemsAreaStart][j] = -2;
                    continue;
                }
                parsedNumbersArray[i - itemsAreaStart][j] = parsedNumber;
            }
        }

        int j = -1;
        for(Map.Entry<Rect, Rect> entry : connections[itemsAreaEnd - 1].entrySet()) {
            j++;
            if(j == itemColumn){
                parsedNumbersArray[i - itemsAreaStart][j] = -1;
                continue;
            }
            Double parsedNumber = 0.0;
            mOCREngine.SetRectangle(entry.getValue());
            try {
                String parsedNumberString = mOCREngine.GetUTF8Text();
                parsedNumberString = CleaningParsedNumber(parsedNumberString);
                parsedNumber = Double.parseDouble(parsedNumberString);
            } catch (Exception ex) {
                parsedNumbersArray[i - itemsAreaStart][j] = -1;
                continue;
            }
            parsedNumbersArray[i - itemsAreaStart][j] = parsedNumber;
        }
    }

    private String CleaningParsedNumber(String parsedNumberString) {
        parsedNumberString = parsedNumberString.replace(" ", "");
        while (parsedNumberString.startsWith("."))
        {
            parsedNumberString = parsedNumberString.substring(1);
        }

        while (parsedNumberString.endsWith("."))
        {
            parsedNumberString = parsedNumberString.substring(0,parsedNumberString.length()-2);
        }
        return  parsedNumberString;
    }

    Boolean IsLinesConnected(ArrayList<Rect> line, ArrayList<Rect> nextLine, LinkedHashMap<Rect, Rect> connections)
    {
       Boolean isFirstConnectedCofigurationExist;
       Boolean isSecondConnectedCofigurationExist;

       if(!secondColumnIsConnected && !oneBeforeLastColumnConnected) {
           secondColumnIsConnected = line.size() >= 3 &&
                   connections.get(line.get(0)) == nextLine.get(0) &&
                   connections.get(line.get(line.size() - 1)) == nextLine.get(nextLine.size() - 1) &&
                   connections.get(line.get(1)) == nextLine.get(1);
           oneBeforeLastColumnConnected = line.size() >= 3 &&
                   connections.get(line.get(0)) == nextLine.get(0) &&
                   connections.get(line.get(line.size() - 1)) == nextLine.get(nextLine.size() - 1) &&
                   connections.get(line.get(line.size() - 2)) == nextLine.get(nextLine.size() - 2);

           if(secondColumnIsConnected && oneBeforeLastColumnConnected)
           {
               oneBeforeLastColumnConnected = false;
           }

           return (secondColumnIsConnected && !oneBeforeLastColumnConnected) ||
                 (!secondColumnIsConnected && oneBeforeLastColumnConnected);
       }
       else
       {
           isFirstConnectedCofigurationExist = line.size() >= 3 &&
                   connections.get(line.get(0)) == nextLine.get(0) &&
                   connections.get(line.get(line.size() - 1)) == nextLine.get(nextLine.size() - 1) &&
                   connections.get(line.get(1)) == nextLine.get(1);
           isSecondConnectedCofigurationExist = line.size() >= 3 &&
                   connections.get(line.get(0)) == nextLine.get(0) &&
                   connections.get(line.get(line.size() - 1)) == nextLine.get(nextLine.size() - 1) &&
                   connections.get(line.get(line.size() - 2)) == nextLine.get(nextLine.size() - 2);
           return (isFirstConnectedCofigurationExist && secondColumnIsConnected) ||
                  (isSecondConnectedCofigurationExist && oneBeforeLastColumnConnected);
       }
    }

    private boolean InRange(Rect word, Rect nextLineWord, int range) {
        return  ((word.right >= nextLineWord.right && word.right - range <= nextLineWord.right) ||
                (word.right <= nextLineWord.right && word.right + range >= nextLineWord.right));
    }

    private ArrayList<ArrayList<Rect>> GetWordLocations(Bitmap processedBillImage){
        ArrayList<ArrayList<Rect>> locations = new ArrayList<>();
        try {
            int lineCount = 0;
            int wordCount = 0;

            mOCREngine.SetImage(processedBillImage);
            mOCREngine.SetNumbersOnlyFormat();
            List<Rect> textlines = mOCREngine.GetTextlines();


            // go over each line and find all numbers with their locations.
//            while (textLne != null) {
            while(lineCount < textlines.size()) {
                Rect textLine = textlines.get(lineCount++);
                mOCREngine.SetRectangle(textLine);
                List<Rect> textWords = mOCREngine.GetWords();
                Rect wordRect = textWords.get(wordCount++);
                if (wordRect != null) {

                    locations.add(new ArrayList<Rect>());
                    while (wordRect != null) {
                        locations.get(lineCount - 1).add(wordRect);
                        wordRect = textWords.get(wordCount++);
                    }
                    wordCount = 0;
                }
            }
        }
        catch(Exception ex){
            Log.d(this.getClass().getSimpleName(), "Failed to map numbered values to location. Error: " + ex.getMessage());
            return null;
        }

        return locations;
    }

    private int CalculateIndexOfItemsColumn(int itemsAreaStart, int itemsAreaEnd, LinkedHashMap<Rect, Rect>[] connections, ArrayList<ArrayList<Rect>> locations) {
        int i;
        int[] sumPerColumns = new int[connections[itemsAreaStart].size()];
        for(i = itemsAreaStart; i <= itemsAreaEnd; i++) {
            int start=0,current=0;
            int[] calculatePerColumns = new int[connections[itemsAreaStart].size()];
            for (int j=0; j<connections[itemsAreaStart].size(); j++) {
                Rect connection;
                if(i == itemsAreaEnd)
                {
                    //in case of last items area lines, we making a special calculation
                    connection = (Rect) (connections[i-1].values().toArray()[j]);
                }
                else
                {
                    connection = (Rect) (connections[i].keySet().toArray()[j]);
                }

                while(true) {
                    ArrayList<Rect> locationsLine = locations.get(i);
                    if(locationsLine.get(current) == connection)
                    {
                        calculatePerColumns[j] = locationsLine.get(current).right - locationsLine.get(start).left;
//                        Log.d(this.getClass().getSimpleName(), "right: "+locationsLine.get(current).right+", left: "+locationsLine.get(start).left);
                        current++;
                        start = current;
                        break;
                    }
                    else
                    {
                        current++;
                    }
                }
            }
            UpdateSumPerColumn(sumPerColumns, calculatePerColumns);
        }
        return FindMaxValueIndex(sumPerColumns);
    }

    private int FindMaxValueIndex(int[] array){
        int maxIndex = 0;
        for (int i = 0; i < array.length; i++) {
            int currNumber = array[i];
            if ((currNumber > array[maxIndex])) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private void UpdateSumPerColumn(int[] sumPerColumns, int[] calculatePerColumns) {
        int[] sortedCalculatePerColumns = calculatePerColumns.clone();
        Arrays.sort(sortedCalculatePerColumns);
        for (int j=0; j < sumPerColumns.length; j++)
        {
            for(int i=0; i < sortedCalculatePerColumns.length ; i++)
            {
                if(calculatePerColumns[j] == sortedCalculatePerColumns[i])
                {
                    if(i == (sortedCalculatePerColumns.length - 1)) {
                        sumPerColumns[j] += i + 1;
                    }else {
                        sumPerColumns[j] += i;
                    }
                }
            }
        }
    }

    /**
     *
     * @return
     * returns mapping between line number and word index to
     *      parsed word(to Double) with its location at the Bitmap
     */
    private LinkedHashMap<Integer, LinkedHashMap<Integer, Map.Entry<Double, Rect>>> GetMapper() {

        LinkedHashMap<Integer, LinkedHashMap<Integer, Map.Entry<Double, Rect>>> mapper = new LinkedHashMap<>();

        try {
            int lineCount = 0;
            int wordCount = 0;

            mOCREngine.SetImage(mFullBillProcessedImage);
            mOCREngine.SetNumbersOnlyFormat();
            List<Rect> textlines = mOCREngine.GetTextlines();


            // go over each line and find all numbers with their locations.
            while (lineCount < textlines.size()) {
                Rect textLine = textlines.get(lineCount++);
                mOCREngine.SetRectangle(textLine);
                List<Rect> textWords = mOCREngine.GetWords();
                Rect wordRect = textWords.get(wordCount);

                Rect textWord = textWords.get(wordCount++);
                mapper.put(lineCount, new LinkedHashMap<Integer, Map.Entry<Double, Rect>>());
                while (textWord != null) {
                    mOCREngine.SetRectangle(textWord);
                    String word = null;
                    try {
                        word = mOCREngine.GetUTF8Text();
                        if (mOCREngine.MeanConfidence() < 70) {
                            throw new Exception();
                        }
                    } catch (Exception ex) {
                        mapper.get(lineCount).put(wordCount, new AbstractMap.SimpleEntry<Double, Rect>(new Double(0), wordRect));
                        wordRect = textWords.get(wordCount);

                        textWord = textWords.get(wordCount++);
                        continue;
                    }

                    double parsedWord;
                    try {
                        parsedWord = Double.parseDouble(word);
                    } catch (Exception ex) {
                        mapper.get(lineCount).put(wordCount, new AbstractMap.SimpleEntry<Double, Rect>(new Double(-1), wordRect));
                        wordRect = textWords.get(wordCount);

                        textWord = textWords.get(wordCount++);
                        continue;
                    }
                    mapper.get(lineCount).put(wordCount, new AbstractMap.SimpleEntry<Double, Rect>(new Double(parsedWord), wordRect));
                    wordRect = textWords.get(wordCount);

                    textWord = textWords.get(wordCount++);
                }
                wordCount = 0;
            }
        } catch (Exception ex) {
            Log.d(this.getClass().getSimpleName(), "Failed to map numbered values to location. Error: " + ex.getMessage());
            return null;
        }

        return mapper;

    }

    private Bitmap CreatingImageFromRects(List<Map.Entry<Integer, Integer>> startEndOfAreasList, LinkedHashMap<Rect, Rect>[] connections) {
        Log.d(this.getClass().getSimpleName(), "");
        int max = 0, beginIndex = 0, endIndex = 0;
        for (Map.Entry<Integer, Integer> entry : startEndOfAreasList) {
            int startLine = entry.getKey();
            int endLine = entry.getValue();
            int numberOfLines = endLine - startLine;
            if(numberOfLines > max)
            {
                max = numberOfLines;
                beginIndex = startLine;
                endIndex = endLine;
            }
        }
        return CreateImage(connections, beginIndex, endIndex);
    }

    private Bitmap CreateImage(LinkedHashMap<Rect, Rect>[] connections, int beginIndex, int endIndex) {
        final Bitmap newBill = Bitmap.createBitmap(mFullBillImage.getWidth(), mFullBillImage.getHeight(), Bitmap.Config.ARGB_8888);
        final Paint paint = new Paint();
        final Canvas canvas = new Canvas(newBill);

        canvas.drawColor(Color.WHITE);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(50.0f);

        for (int i = beginIndex; i < endIndex + 1; i++)
        {
            List<Rect> keyListCurrentIndex = null;
            if(i == endIndex)
            {
                keyListCurrentIndex = new ArrayList<>(connections[i-1].values());
            }
            else
            {
                keyListCurrentIndex = new ArrayList<>(connections[i].keySet());
            }

            for(int j = 0; j < keyListCurrentIndex.size(); j++)
            {
                int xBegin   = keyListCurrentIndex.get(j).left;
                int xEnd  = keyListCurrentIndex.get(j).right;
                int yBegin    = keyListCurrentIndex.get(j).top;
                int yEnd = keyListCurrentIndex.get(j).bottom;
                Bitmap bitmap = Bitmap.createBitmap(mFullBillProcessedImage, xBegin, yBegin, xEnd-xBegin, yEnd-yBegin);
                canvas.drawBitmap(mFullBillProcessedImage, keyListCurrentIndex.get(j), keyListCurrentIndex.get(j), paint);
                bitmap.recycle();
            }
        }
        return newBill;
    }
}
