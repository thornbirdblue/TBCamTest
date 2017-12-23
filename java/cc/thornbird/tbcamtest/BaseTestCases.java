package cc.thornbird.tbcamtest;

import android.view.View;
import android.view.SurfaceView;
import android.view.SurfaceHolder;

/**
 * Created by thornbird on 2017/12/21.
 */

public class BaseTestCases implements CamTestCases {
    private CamTestReprot mCamTestReport;
    private Api2Camera mApi2Cam;

    private SurfaceView mPreviewView;
    private SurfaceHolder mPreviewHolder;
    private final int mPreviewTime = 10000; // 500ms
    private final int mRecordingTime = 10000;

    public  BaseTestCases(CameraInfoCache mCIF)
    {
        mCamTestReport = new CamTestReprot();
        mApi2Cam = new Api2Camera(mCIF,mCamTestReport);

        mPreviewView = mCIF.getPreviewSurface();
        mPreviewHolder = mPreviewView.getHolder();
        mCIF.setPreviewVisibility();
    }

    public CamTestReprot doRunTestCases()
    {
        testOpenOneCamera();
        testStartPreview();
        try {
            Thread.sleep(mPreviewTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        testTakePicture();
        testStartPreview();
        testRecording();
//        testCameraClose();
        return mCamTestReport;
    }

    private void testOpenOneCamera()
    {
        mApi2Cam.openCamera();
    }

    private void testStartPreview()
    {
        mApi2Cam.startPreview(mPreviewHolder.getSurface());
    }

    private void testTakePicture()
    {
        mApi2Cam.takePicture();
    }

    private void testCameraClose()
    {
        mApi2Cam.closeCamera();
    }

    private void testRecording()
    {
        mApi2Cam.startRecording();
        try {
            Thread.sleep(mRecordingTime);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        mApi2Cam.stopRecording();
    }

    public void stop()
    {
        mApi2Cam.closeCamera();
    }
}
