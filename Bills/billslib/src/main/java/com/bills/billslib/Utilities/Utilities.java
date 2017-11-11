package com.bills.billslib.Utilities;

import android.graphics.Bitmap;
import android.util.Log;

import com.bills.billslib.Contracts.Constants;
import com.bills.billslib.Contracts.Enums.LogLevel;
import com.bills.billslib.Contracts.Enums.LogsDestination;
import com.bills.billslib.Core.BillsLog;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;


/**
 * Created by avielavr on 5/8/2017.
 */

public class Utilities {
    private static String Tag = Utilities.class.getName();

    public static boolean SaveToPNGFile(Bitmap bmp, String path){
        FileOutputStream out = null;
        try {
            File file = new File(path);
            if(file.exists()){
                file.delete();
            }

            File folder = new File(file.getParent());
            if(!folder.exists())
            {
                Boolean isSuccess = folder.mkdirs();
                if(!isSuccess) {
                    BillsLog.Log(Tag, LogLevel.Error, "Can't create directory(ies)", LogsDestination.BothUsers);
                    return false;
                }
            }
            out = new FileOutputStream(path);

            // bmp is your Bitmap instance, PNG is a lossless format, the compression factor (100) is ignored
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            BillsLog.Log(Tag, LogLevel.Info, "SaveToPNGFile success!", LogsDestination.BothUsers);
            return true;
        } catch (Exception e) {
            String logMessage = "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
            return false;
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
            } catch (IOException e) {
                String logMessage = "Can't free output stream. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
                return false;
            }
        }
    }

    public static boolean SaveToTXTFile(byte[] image, String fileFullName){
        BufferedOutputStream bos = null;
        try {
            bos = new BufferedOutputStream(new FileOutputStream(fileFullName));
            bos.write(image);
            bos.flush();
            bos.close();
        }
        catch (Exception e)
        {
            String logMessage = "Can't create directory(ies). \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
            return false;
        }finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    String logMessage = "Can't free allocated buffer. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
                    BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean SaveMatToPNGFile(Mat mat, String path){
        Bitmap bmp = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bmp);
        try {
            return SaveToPNGFile(bmp, path);
        }
        catch (Exception e){
            String logMessage = "SaveMatToPNGFile Failed. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
            return false;
        }
        finally {
            bmp.recycle();
        }
    }

    public static void SaveBytesToPNGFile(byte[] image, String fileFullName){
        Mat mat = null;
        try {
            mat = Bytes2MatAndRotateClockwise90(image);
            SaveMatToPNGFile(mat, fileFullName);
        } catch (Exception e) {
            BillsLog.Log(Tag, LogLevel.Error, "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage(), LogsDestination.BothUsers);
        }
        finally {
            if(mat != null){
                mat.release();
            }
        }
    }

    public static byte[] ImageTxtFile2ByteArray(String path) throws IOException {
        File file = new File(path);
        int size = (int) file.length();
        byte[] image = new byte[size];
        BufferedInputStream bis = null;
        try{
            bis = new BufferedInputStream(new FileInputStream(file));
            bis.read(image);
        }catch (Exception e){
            String logMessage = "failed to read input stream. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
            return null;
        }finally {
            if(bis != null){
                bis.close();
            }
        }
        return image;
    }

    /**
     *
     * @param fileFullName txt file full name on device
     * @return list of string with file lines
     * @throws IOException
     */
    public static List<String> ReadTextFile(String fileFullName) throws IOException {
        BufferedReader bufferedReader = null;
        List<String> lines = new ArrayList<>();
        try{
            bufferedReader = new BufferedReader(new FileReader(fileFullName));
            // do reading, usually loop until end of file reading
            String line = bufferedReader.readLine();
            while (line != null) {
                lines.add(line);
                line = bufferedReader.readLine();
            }
        }
        catch (Exception e){
            BillsLog.Log(Tag, LogLevel.Error, "failed to read text file: " + e.getMessage(), LogsDestination.BothUsers);
            return null;
        }
        finally {
            if(bufferedReader != null) {
                bufferedReader.close();
            }
        }
        return lines;
    }

    public static Mat LoadRotatedBillMat(String billFullName) throws Exception {
        byte[] bytes = ImageTxtFile2ByteArray(billFullName);
        if(bytes == null){
            throw new Exception();
        }
        return Bytes2MatAndRotateClockwise90(bytes);
    }

    /**
     * Set output stream for 'System.out.println'. The test prints just to file.
     * Read TEST_README for more info
     * @throws FileNotFoundException
     */
    public static void SetOutputStream(String fileName){
        File file = new File(fileName);
        PrintStream printStreamToFile = null;
        try {
            printStreamToFile = new PrintStream(file);
        } catch (Exception e) {
            String logMessage = "Failed to set output stream. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
            return;
        }
        System.setOut(printStreamToFile);
    }

    public static String GetLastCapturedBillPath() {
        File f = new File(Constants.IMAGES_PATH);
        File[] listFiles = null;

        try {
            listFiles = f.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    String fileName = file.getName();
                    return fileName.startsWith("ocrBytes") && fileName.endsWith(".txt");
                }
            });

            if(listFiles == null || listFiles.length == 0){
                BillsLog.Log(Tag, LogLevel.Error, "Failed to get files from directory.", LogsDestination.BothUsers);
                return null;
            }
            //sorting to take the last capture which took by Bills app
            Arrays.sort(listFiles, new Comparator() {
                public int compare(Object o1, Object o2) {
                    if (((File) o1).lastModified() > ((File) o2).lastModified()) {
                        return -1;
                    } else if (((File) o1).lastModified() < ((File) o2).lastModified()) {
                        return +1;
                    } else {
                        return 0;
                    }
                }
            });
        }
        catch (Exception e){
            String logMessage = "GetLastCapturedBillPath Failed. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
        }
        return listFiles[0].getPath();
    }

    public static boolean CreateDirectory(String path) {
        try{
        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.d("AAA", "failure");
//                BillsLog.Log(Tag, LogLevel.Error, "Creation of directory " + path + " failed, check does Android Manifest have permission to write to external storage.");
                return false;
            }
            else {
                Log.d("AAA", "succeed");
//                BillsLog.Log(Tag, LogLevel.Info, "Creation of directory " + path + " succeed");
                return true;
            }
        } else {
            Log.d("AAA", "exist");
//            BillsLog.Log(Tag, LogLevel.Info, "Directory " + path + " already exists!");
            return false;
        }
        }
        catch (Exception e){
            Log.d("AAA", e.getMessage());
//            BillsLog.Log(Tag, LogLevel.Error, "StackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage());
            return false;
        }
    }

    public static String GetTimeStamp() {
        DateFormat sdf = new SimpleDateFormat("dd_MM_yyyy HH:mm:ss_SSS");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Jerusalem"));
        Date date = new Date();
        return sdf.format(date);
    }

    public static Mat Bytes2MatAndRotateClockwise90(byte[] bytes) throws Exception {
        if (!OpenCVLoader.initDebug()) {
            BillsLog.Log(Tag, LogLevel.Error, "Failed to initialize OpenCVLoader.", LogsDestination.BothUsers);
            return null;
        }
        Mat bgrMat = null;
        MatOfByte matOfByte = new MatOfByte(bytes);
        Mat jpegData = new Mat(1, bytes.length, CvType.CV_8UC1);
        try{
            jpegData.put(0, 0, bytes);
            bgrMat = Imgcodecs.imdecode(jpegData, Imgcodecs.IMREAD_COLOR);
            Imgproc.cvtColor(bgrMat, bgrMat, Imgproc.COLOR_RGB2BGRA, 4);
            Mat dst = new Mat(bgrMat.height(), bgrMat.width(), bgrMat.type());
            Core.flip(bgrMat.t(), dst, 1);
            return dst;
        }
        catch (Exception e){
            String logMessage = "Failed to convert bytes to mat. \nStackTrace: " + e.getStackTrace() + "\nException Message: " + e.getMessage();
            BillsLog.Log(Tag, LogLevel.Error, logMessage, LogsDestination.BothUsers);
            return null;
        }
        finally{
            if(matOfByte != null) {
                matOfByte.release();
            }
            if(jpegData != null) {
                jpegData.release();
            }
            if(bgrMat != null) {
                bgrMat.release();
            }
        }
    }

    public static Bitmap ConvertFirebaseBytesToBitmap(byte[] bytes, Integer itemWidth, Integer itemHeight){
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        Bitmap commonItemBitmap = Bitmap.createBitmap(itemWidth, itemHeight, Bitmap.Config.ARGB_8888);
        commonItemBitmap.copyPixelsFromBuffer(buffer);
        return commonItemBitmap;
    }
}