package cc.thornbird.tbcamtest;

import android.hardware.camera2.CameraAccessException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

/**
 * Created by thornbird on 2017/12/21.
 */

public class CamTestMode {
    private static final String TAG = "TBCamTest";
    public static final int TM_BaseTest_Mode = 1;
    public static final int TM_BaseFuncTest_Mode = 2;
    public static final  int TM_FeatureTest_Mode = 3;
    public static final  int TM_AutoTest_Mode = 4;
    public static final  int TM_PerfTest_Mode = 5;
    public static final  int TM_StressTest_Mode = 6;

    private CamTestCases mCamTestCases;

    private Boolean isTestRunning = false;
    private HandlerThread mTestCaseThread;
    private Handler mTestCaseHandler;

    private CamTestCallBack mCamTestCallBack;

    interface CamTestCallBack{
        Boolean CamTestIsFinish();
    }

    public CamTestMode(int Mode,CameraInfoCache mCamInfoCache,CamTestCallBack mCB)
    {
        mCamTestCallBack = mCB;

        mTestCaseThread = new HandlerThread("CameraTestCaseThread");
        mTestCaseThread.start();
        mTestCaseHandler = new Handler(mTestCaseThread.getLooper());

        switch(Mode)
        {
            case TM_BaseTest_Mode:
                mCamTestCases = new BaseTestCases(mCamInfoCache);
                break;
            case TM_BaseFuncTest_Mode:
                mCamTestCases = new BaseFuncTestCases(mCamInfoCache);
                break;
            case TM_FeatureTest_Mode:
                mCamTestCases = new BaseFeatureTestCases(mCamInfoCache);
                break;
            case TM_AutoTest_Mode:

                break;
            case TM_PerfTest_Mode:

                break;
            case TM_StressTest_Mode:
                mCamTestCases = new StressTestCases(mCamInfoCache);
                break;
            default:
                mCamTestCases = null;
        }
    }

    public void run()
    {
        if(mCamTestCases == null) {
            CamLogger.e(TAG,"CamTestCases is NULL!!!");
            return;
        }

        mTestCaseHandler.post(new Runnable() {
            @Override
            public void run() {
                isTestRunning = true;
                mCamTestCases.doRunTestCases();
                mCamTestCases.testIsFinish();
                mCamTestCallBack.CamTestIsFinish();
            }
        });
    }

    public void stop()
    {
        if(mCamTestCases == null) {
            CamLogger.e(TAG,"CamTestCases is NULL!!!");
            return;
        }

       if(isTestRunning) {
           mCamTestCases.stop();
           mTestCaseThread.quit();
           isTestRunning = false;
       }
    }
}
