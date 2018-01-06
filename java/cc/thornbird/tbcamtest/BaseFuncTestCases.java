package cc.thornbird.tbcamtest;

import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.Semaphore;

/**
 * Created by thornbird on 2018/1/6.
 */

public class BaseFuncTestCases implements CamTestCases {
    private static final String TAG = "TBCamTest_BaseFuncTestCases";
    private CamTestReport mCamTestReport = null;
    private final int BaseTestCaseNum = 4;

    private Api2Camera mApi2Cam = null;

    private SurfaceView mPreviewView = null;
    private SurfaceHolder mPreviewHolder = null;
    private final int mPreviewTime = 500; // 500ms
    private final int mRecordingTime = 1000;

    private boolean mCamTest;
    private boolean mRecorderTest;
    private boolean mRawCaptureTest;
    private boolean mYuvReprocessTest;

    private volatile Semaphore mSemaphore = new Semaphore(0);

    public  BaseFuncTestCases(CameraInfoCache mCIF)
    {
        mCamTestReport = new CamTestReport(BaseTestCaseNum);
        mApi2Cam = new Api2Camera(mCIF);

        mPreviewView = mCIF.getPreviewSurface();
        mPreviewHolder = mPreviewView.getHolder();
        mCIF.setPreviewVisibility();

        mCamTest = mCIF.getCamSupport();
        mRecorderTest = mCIF.getRecorderSupport();
        mRawCaptureTest = mCIF.getRawSupport();
        mYuvReprocessTest = mCIF.getYuvReprocessSupport();
    }

    public void doRunTestCases()
    {
        CamLogger.i(TAG, "doRunTestCases...");
        mCamTestReport.clearLastResult();
        if(mCamTest) {
            testZSLJpegTakePicture();
            testZSLYuvTakePicture();
            testZSLRawTakePicture();
            testZSLYuvReprocess();
        }

        CamIsFinish();
        mCamTestReport.printTestResult();
        mSemaphore.release();
    }
    private void testZSLJpegTakePicture()
    {
        CamLogger.i(TAG, "testZSLJpegTakePicture! ");

        mApi2Cam.openCamera();
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("ZSLJpegTakePicture Test",mApi2Cam.OpsResult());
            return;
        }

        mApi2Cam.startPreview(mPreviewHolder.getSurface(),true,Api2Camera.ZSL_JPEG);
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("ZSLJpegTakePicture Test",mApi2Cam.OpsResult());
            return;
        }

        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.takePicture(Api2Camera.ZSL_JPEG);
        mCamTestReport.addTestResult("ZSLJpegTakePicture Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
    }
    private void testZSLYuvTakePicture()
    {
        CamLogger.i(TAG, "testZSLYuvTakePicture! ");

        mApi2Cam.openCamera();
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("ZSLYuvTakePicture Test",mApi2Cam.OpsResult());
            return;
        }

        mApi2Cam.startPreview(mPreviewHolder.getSurface(),true,Api2Camera.ZSL_YUV);
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("ZSLYuvTakePicture Test",mApi2Cam.OpsResult());
            return;
        }
        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.takePicture(Api2Camera.ZSL_YUV);
        mCamTestReport.addTestResult("ZSLYuvTakePicture Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
    }

    private void testZSLRawTakePicture()
    {
        CamLogger.i(TAG, "testZSLRawTakePicture! ");
        if(mRawCaptureTest != true)
        {
            CamLogger.i(TAG, "Sensor can't support RAW TakePicture! ");
            mCamTestReport.addTestResult("ZSLRawTakePicture Test",false);
            return;
        }

        mApi2Cam.openCamera();
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("ZSLRawTakePicture Test",mApi2Cam.OpsResult());
            return;
        }

        mApi2Cam.startPreview(mPreviewHolder.getSurface(),true,Api2Camera.ZSL_RAW);
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("ZSLRawTakePicture Test",mApi2Cam.OpsResult());
            return;
        }

        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.takePicture(Api2Camera.ZSL_RAW);
        mCamTestReport.addTestResult("ZSLRawTakePicture Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
    }

    private void testZSLYuvReprocess()
    {
        CamLogger.i(TAG, "testZSLYuvReprocess! ");

        if(mYuvReprocessTest != true)
        {
            CamLogger.i(TAG, "Sensor can't support YUV Reprocess! ");
            mCamTestReport.addTestResult("testZSLYuvReprocess Test",false);
            return;
        }

        mApi2Cam.openCamera();
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("testZSLYuvReprocess Test",mApi2Cam.OpsResult());
            return;
        }

        mApi2Cam.startPreview(mPreviewHolder.getSurface(),true,Api2Camera.ZSL_YUV_REPROCESS);
        if(mApi2Cam.OpsResult() == false)
        {
            mCamTestReport.addTestResult("testZSLYuvReprocess Test",mApi2Cam.OpsResult());
            return;
        }
        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.takePicture(Api2Camera.ZSL_YUV_REPROCESS);
        mCamTestReport.addTestResult("testZSLYuvReprocess Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
    }

    private void CamIsFinish()
    {
        mApi2Cam.OpsIsFinish();
    }

    public void stop()
    {
        CamLogger.d(TAG, "Stop camera!!! ");
        mApi2Cam.StopCamera();
    }

    public Boolean testIsFinish()
    {
        try {
            mSemaphore.acquire();
        } catch (InterruptedException e) {
            CamLogger.e(TAG,"Sem acquire ERROR!");
        }
        return true;
    }

}
