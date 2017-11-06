package com.bills.billslib.Core;

import com.bills.billslib.Contracts.Enums.LogLevel;
import com.bills.billslib.Contracts.Enums.LogsPathToPrintTo;
import com.bills.billslib.Contracts.Interfaces.ILogger;

/**
 * Created by michaelvalershtein on 09/09/2017.
 */

public class BillsLog {
    private static ILogger mLogger;
    private BillsLog(){}

    public static void Init(ILogger logger){
        mLogger = logger;
    }

    public static void Log(String tag, LogLevel logLevel, String message, LogsPathToPrintTo logsPathToPrintTo){
        if(mLogger == null){
            throw new ExceptionInInitializerError("BillsLog was not initialized.");
        }
        mLogger.Log(tag, logLevel, message, logsPathToPrintTo);
    }
}
