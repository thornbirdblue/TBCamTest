package cc.thornbird.tbcamtest;

/**
 * Created by thornbird on 2017/12/29.
 */

public class CamTestReport {
    private static final String TAG = "TBCamTest_CamTestReport";

    private TestResult[] mTestResult;
    private int mTestNum = 0;
    private int mTotalTestCases;

    public CamTestReport(int TestCaseNum)
    {
        mTestResult = new TestResult[TestCaseNum];
        mTotalTestCases = TestCaseNum;

        for(int i=0;i<mTotalTestCases;i++)
            mTestResult[i] = new TestResult();

        mTestNum = 0;
    }

    public void addTestResult(String Test,Boolean result)
    {
        if(mTestNum >= mTotalTestCases) {
            CamLogger.e(TAG, "ERROR TestNum is more than REQUEST TestCaseNum(" + mTotalTestCases + ")!!!");
            return;
        }

        mTestResult[mTestNum++].setValue(Test,result);
    }

    public void printTestResult()
    {
        for(int i=0;i<mTotalTestCases;i++)
            mTestResult[i].printValue();
    }

    private class TestResult
    {
        public String mCaseName;
        public Boolean mResult;

        public void setValue(String Test,Boolean result)
        {
            mCaseName = Test;
            mResult = result;
        }
        public void printValue()
        {
            CamLogger.e(TAG,"TestCase "+mCaseName+": "+mResult);
        }
    }
}
