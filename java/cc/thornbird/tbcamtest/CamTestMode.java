package cc.thornbird.tbcamtest;

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

    public CamTestMode(int Mode,CameraInfoCache mCamInfoCache)
    {
        switch(Mode)
        {
            case TM_BaseTest_Mode:
                mCamTestCases = new BaseTestCases(mCamInfoCache);
        }
    }

    public void run()
    {
        isTestRunning = true;
        mCamTestCases.doRunTestCases();
    }

    public void stop()
    {
        if(isTestRunning)
            mCamTestCases.stop();

        isTestRunning = false;
    }
}
