package cc.thornbird.tbcamtest;

import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.concurrent.Semaphore;


/**
 * Created by thornbird on 2018/1/4.
 */

public class StressTestCases implements CamTestCases {
    private static final String TAG = "TBCamTest_StressTestCasess";
    private CamTestReport mCamTestReport = null;
    private final int StressTestCaseNum = 3;

    private final int OpenCloseStressNum = 1000;
    private final int takePictureStressNum = 500;
    private final int RecorderStressNum = 500;

    private Api2Camera mApi2Cam = null;

    private SurfaceView mPreviewView = null;
    private SurfaceHolder mPreviewHolder = null;
    private final int mPreviewTime = 500; // 500ms
    private final int mRecordingTime = 1000;

    private volatile Semaphore mSemaphore = new Semaphore(0);

    public  StressTestCases(CameraInfoCache mCIF)
    {
        mCamTestReport = new CamTestReport(StressTestCaseNum);
        mApi2Cam = new Api2Camera(mCIF);

        mPreviewView = mCIF.getPreviewSurface();
        mPreviewHolder = mPreviewView.getHolder();
        mCIF.setPreviewVisibility();
    }
    public void doRunTestCases()
    {
        CamLogger.i(TAG, "doRunTestCases...");
        mCamTestReport.clearLastResult();
        stressOpenOneCameraAndClose();
        stressTakePicture();
        stressRecording();
        CamIsFinish();
        mCamTestReport.printTestResult();
        mSemaphore.release();
    }

    private void stressOpenOneCameraAndClose()
    {
        CamLogger.i(TAG, "stressOpenOneCameraAndClose! ");
        for(int i=0;i<OpenCloseStressNum;i++) {
            CamLogger.d(TAG, "stressOpenOneCameraAndClose: "+i);
            mApi2Cam.openCamera();
            mApi2Cam.closeCamera();
        }
        mCamTestReport.addTestResult("Stress Open Camera Test",mApi2Cam.OpsResult());
    }

    private void stressTakePicture()
    {
        CamLogger.i(TAG, "stressTakePicture! ");

        mApi2Cam.openCamera();
        mApi2Cam.startPreview(mPreviewHolder.getSurface());
        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        for(int i=0;i<takePictureStressNum;i++) {
            CamLogger.d(TAG, "stressTakePicture: "+i);
            mApi2Cam.takePicture();
        }
        mCamTestReport.addTestResult("takePicture Test",mApi2Cam.OpsResult());
        mApi2Cam.closeCamera();
    }

    private void stressRecording()
    {
        CamLogger.i(TAG, "stressRecording! ");

        mApi2Cam.openCamera();
        for(int i=0;i<RecorderStressNum;i++) {
            CamLogger.d(TAG, "stressRecording: "+i);
            mApi2Cam.startRecordingPreview(mPreviewHolder.getSurface());
            mApi2Cam.startRecording();
            try {
                Thread.sleep(mRecordingTime);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mApi2Cam.stopRecording();
        }
        mCamTestReport.addTestResult("Stress Recording Test", mApi2Cam.OpsResult());
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
