package cc.thornbird.tbcamtest;

import android.util.Log;
import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

import java.util.concurrent.Semaphore;

/**
 * Created by thornbird on 2017/12/21.
 */

public class BaseTestCases implements CamTestCases {
    private static final String TAG = "TBCamTest_BaseTestCases";
    private CamTestReport mCamTestReport = null;
    private Api2Camera mApi2Cam = null;

    private SurfaceView mPreviewView = null;
    private SurfaceHolder mPreviewHolder = null;
    private final int mPreviewTime = 500; // 500ms
    private final int mRecordingTime = 1000;

    private volatile Semaphore mSemaphore = new Semaphore(0);

    public  BaseTestCases(CameraInfoCache mCIF)
    {
        mCamTestReport = new CamTestReprot();
        mApi2Cam = new Api2Camera(mCIF);

        mPreviewView = mCIF.getPreviewSurface();
        mPreviewHolder = mPreviewView.getHolder();
        mCIF.setPreviewVisibility();
    }

    public CamTestReprot doRunTestCases()
    {
        CamLogger.i(TAG, "doRunTestCases...");
        testOpenOneCameraAndClose();
        testStartPreview();
        testTakePicture();
        testRecording();
        CamIsFinish();
        mSemaphore.release();
        return mCamTestReport;
    }

    private void testOpenOneCameraAndClose()
    {
        CamLogger.i(TAG, "testOpenOneCameraAndClose! ");

        mApi2Cam.openCamera();
        mCamTestReport.addTestResult("Close One Camera Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
        mCamTestReport.addTestResult("Close One Camera Test",mApi2Cam.OpsResult());
    }

    private void testStartPreview()
    {
        CamLogger.i(TAG, "testStartPreview! ");

        mApi2Cam.openCamera();
        mApi2Cam.startPreview(mPreviewHolder.getSurface());
        mCamTestReport.addTestResult("StartPreview Test",mApi2Cam.OpsResult());
        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.closeCamera();
    }

    private void testTakePicture()
    {
        CamLogger.i(TAG, "testTakePicture! ");

        mApi2Cam.openCamera();
        mApi2Cam.startPreview(mPreviewHolder.getSurface());
        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.takePicture();
        mCamTestReport.addTestResult("takePicture Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
    }

    private void testRecording()
    {
        CamLogger.i(TAG, "testRecording! ");

        mApi2Cam.openCamera();
        mApi2Cam.startRecordingPreview(mPreviewHolder.getSurface());
        mApi2Cam.startRecording();
        mCamTestReport.addTestResult("startRecording Test",mApi2Cam.OpsResult());
        try {
            Thread.sleep(mRecordingTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.stopRecording();
        mApi2Cam.closeCamera();
    }

    private void CamIsFinish()
    {
        mApi2Cam.OpsIsFinish();
    }

    public void stop()
    {
        CamLogger.i(TAG, "Stop camera!!! ");
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
