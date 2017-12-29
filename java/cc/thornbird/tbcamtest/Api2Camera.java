package cc.thornbird.tbcamtest;

import android.os.ConditionVariable;
import android.view.Surface;
import android.os.SystemClock;
import android.os.Handler;
import android.os.HandlerThread;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.InputConfiguration;

import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.graphics.ImageFormat;
import android.media.MediaRecorder;

import android.util.Size;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by thornbird on 2017/12/21.
 */

public class Api2Camera implements CameraInterface {
    private static final String TAG = "TBCamTest_Api2Camera";
    private static int CamLogger_NTH_FRAME = 30;

    private enum CamCmd{CAM_NULL,CAM_OPEN,CAM_CLOSE,CAM_STARTPREVIEW,CAM_TAKEPICTURE,CAM_STOPPREVIEW,CAM_STARTRECORDING,CAM_STOPRECORDING};
    private enum CamCmdResult{CAM_OP_SUCCESS,CAM_OP_FALSE};
    private CamCmd mCamCmd;
    private CamCmdResult mCamOpResult;

    private CameraInfoCache mCamInfo = null;
    private CameraManager mCameraManager = null;

    private CameraDevice mCameraDevice = null;
    private CameraCaptureSession mCurrentCaptureSession = null;

    volatile private Surface mPreviewSurface = null;

    private HandlerThread mOpsThread;
    private Handler mOpsHandler;
    private HandlerThread mJpegListenerThread;
    private Handler mJpegListenerHandler;


    private volatile Semaphore mSemaphore = new Semaphore(1);
    private volatile boolean mAllThingsInitialized = false;
    private boolean mFirstFrameArrived = false;

    private boolean mZslMode = false;
    private ImageReader mJpegImageReader;
    private ImageReader mYuvImageReader;
    private int mYuvImageCounter;
    private ImageReader mRawImageReader;

    private Image mYuvLastReceivedImage = null;
    private Image mRawLastReceivedImage = null;

    private static final int YUV_IMAGEREADER_SIZE = 8;
    private static final int RAW_IMAGEREADER_SIZE = 8;
    private static final int IMAGEWRITER_SIZE = 2;

    private long mReprocessingRequestNanoTime;

    private Size mVideoSize;
    private MediaRecorder mMediaRecorder;
    private boolean mIsRecordingVideo;

    // Used for saving JPEGs.
    private HandlerThread mUtilityThread;
    private Handler mUtilityHandler;

    private ConditionVariable mOpsCondittion;

    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback(){
        public void onOpened(CameraDevice camera)
        {
            CameraTime.t_open_end = SystemClock.elapsedRealtime();
            mCameraDevice = camera;
            CamLogger.d(TAG, "STARTUP_REQUIREMENT Done opening camera " + mCamInfo.getCameraId() +
                    ". HAL open took: (" + (CameraTime.t_open_end - CameraTime.t_open_start) + " ms)");
            CamOpsFinish(CamCmd.CAM_OPEN,CamCmdResult.CAM_OP_SUCCESS);
        }
        public void onDisconnected(CameraDevice camera)
        {
            CamLogger.d(TAG,"onDisconnected");
        }
        public void onError(CameraDevice camera, int error)
        {
            CamLogger.e(TAG,"onError"+"error val is"+error);
            CamOpsFinish(CamCmd.CAM_OPEN,CamCmdResult.CAM_OP_FALSE);
        }
    };

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback(){
        public void onConfigured(CameraCaptureSession session)
        {
            mCurrentCaptureSession = session;
            CamLogger.d(TAG, "capture session onConfigured().");
            PreviewCaptureRequest();
        }
        public void onReady(CameraCaptureSession session) {
            CamLogger.d(TAG, "capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)");
        }
        public void onConfigureFailed(CameraCaptureSession session)
        {
            CamLogger.e(TAG,"onConfigureFailed");
        }
    };

    private CameraCaptureSession.StateCallback mJpegSessionStateCallback = new CameraCaptureSession.StateCallback(){
        public void onConfigured(CameraCaptureSession session)
        {
            mCurrentCaptureSession = session;
            CamLogger.d(TAG, "JPEG capture session onConfigured().");
            JpegCaptureRequest();
        }
        public void onReady(CameraCaptureSession session) {
            CamLogger.d(TAG, "JPEG capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)");
        }
        public void onConfigureFailed(CameraCaptureSession session)
        {
            CamLogger.e(TAG,"JPEG onConfigureFailed");
            CamOpsFinish(CamCmd.CAM_TAKEPICTURE,CamCmdResult.CAM_OP_FALSE);
        }
    };

    private CameraCaptureSession.StateCallback mVideoSessionStateCallback = new CameraCaptureSession.StateCallback(){
        public void onConfigured(CameraCaptureSession session)
        {
            mCurrentCaptureSession = session;
            CamLogger.d(TAG, "VIDEO capture session onConfigured().");
            VideoPreviewCaptureRequest();
        }
        public void onReady(CameraCaptureSession session) {
            CamLogger.d(TAG, "VIDEO capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)");
        }
        public void onConfigureFailed(CameraCaptureSession session)
        {
            CamLogger.d(TAG,"VIDEO onConfigureFailed");
            CamOpsFinish(CamCmd.CAM_STARTRECORDING,CamCmdResult.CAM_OP_FALSE);
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (!mFirstFrameArrived) {
                CamOpsFinish(CamCmd.CAM_STARTPREVIEW,CamCmdResult.CAM_OP_SUCCESS);
                mFirstFrameArrived = true;
                long now = SystemClock.elapsedRealtime();
                long dt = now - CameraTime.t0;
                long camera_dt = now - CameraTime.t_session_go + CameraTime.t_open_end - CameraTime.t_open_start;
                long repeating_req_dt = now - CameraTime.t_burst;
                CamLogger.d(TAG, "App control to first frame: (" + dt + " ms)");
                CamLogger.d(TAG, "HAL request to first frame: (" + repeating_req_dt + " ms) " + " Total HAL wait: (" + camera_dt + " ms)");
            }
            else
                CamLogger.v(TAG,"Receive Preview Data!");
        }
    };


    ImageReader.OnImageAvailableListener mJpegImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        CamLogger.e(TAG, "Null image returned JPEG");
                        return;
                    }
                    Image.Plane plane0 = img.getPlanes()[0];
                    final ByteBuffer buffer = plane0.getBuffer();
                    long dt = System.nanoTime() - mReprocessingRequestNanoTime;
                    CamLogger.d(TAG, String.format("JPEG buffer available, w=%d h=%d time=%d size=%d dt=%.1f ms",
                            img.getWidth(), img.getHeight(), img.getTimestamp(), buffer.capacity(), 0.000001 * dt));
                    // Save JPEG on the utility thread,
                    final byte[] jpegBuf;
                    if (buffer.hasArray()) {
                        jpegBuf = buffer.array();
                    } else {
                        jpegBuf = new byte[buffer.capacity()];
                        buffer.get(jpegBuf);
                    }
                    mCamInfo.saveFile(jpegBuf,img.getWidth(), img.getHeight(),0);
                    img.close();

                    CamOpsFinish(CamCmd.CAM_TAKEPICTURE,CamCmdResult.CAM_OP_SUCCESS);
              }
            };
    ImageReader.OnImageAvailableListener mYuvImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        CamLogger.e(TAG, "Null image returned YUV1");
                        return;
                    }

                    Image.Plane[] plane = img.getPlanes();
                    final byte[][] DataBuf = new byte[3][];
                    for(int i=0;i<plane.length;i++) {
                        final ByteBuffer buffer = plane[i].getBuffer();
                        CamLogger.d(TAG,"ByteBuffer: "+buffer.capacity());
                        if (buffer.hasArray()) {
                            DataBuf[i] = buffer.array();
                        } else {
                            DataBuf[i] = new byte[buffer.capacity()];
                            buffer.get(DataBuf[i]);
                        }
                    }
//                    saveFile(DataBuf[0],img.getWidth(), img.getHeight(),1);
//                    saveFile(DataBuf[1],img.getWidth(), img.getHeight(),1);

                    final byte[] saveData = new byte[DataBuf[0].length+DataBuf[1].length];
                    System.arraycopy(DataBuf[0],0,saveData,0,DataBuf[0].length);
                    System.arraycopy(DataBuf[1],0,saveData,DataBuf[0].length,DataBuf[1].length);    // NV12
                    CamLogger.d(TAG,"Date len:"+DataBuf[0].length+" "+DataBuf[1].length);

                    mUtilityHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mCamInfo.saveFile(saveData, mCamInfo.getYuvStreamSize().getWidth(), mCamInfo.getYuvStreamSize().getHeight(), 1);
                        }
                    });

                    CamLogger.d(TAG,"YuvImageListener RECIEVE img!!!");

                    if (mYuvLastReceivedImage != null) {
                        mYuvLastReceivedImage.close();
                    }
                    mYuvLastReceivedImage = img;
                    if (++mYuvImageCounter % CamLogger_NTH_FRAME == 0) {
                        CamLogger.v(TAG, "YUV buffer available, Frame #=" + mYuvImageCounter + " w=" + img.getWidth() + " h=" + img.getHeight() + " time=" + img.getTimestamp());
                    }

                    CamOpsFinish(CamCmd.CAM_TAKEPICTURE,CamCmdResult.CAM_OP_SUCCESS);
                }
            };

    ImageReader.OnImageAvailableListener mRawImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        CamLogger.e(TAG, "Null image returned YUV1");
                        return;
                    }

                    if (mRawLastReceivedImage != null) {
                        mRawLastReceivedImage.close();
                    }

                    Image.Plane plane0 = img.getPlanes()[0];
                    final ByteBuffer buffer = plane0.getBuffer();

                    final byte[] DateBuf;
                    if (buffer.hasArray()) {
                        DateBuf = buffer.array();
                    } else {
                        DateBuf = new byte[buffer.capacity()];
                        buffer.get(DateBuf);
                    }

                    mCamInfo.saveFile(DateBuf,img.getWidth(), img.getHeight(),2);

                    mRawLastReceivedImage = img;
                    CamLogger.d(TAG,"mRawImageListener RECIEVE img!!!");

                    CamOpsFinish(CamCmd.CAM_TAKEPICTURE,CamCmdResult.CAM_OP_SUCCESS);
                }
            };



    public Api2Camera(CameraInfoCache mCIF)
    {
        mCamInfo = mCIF;
        mCameraManager = mCamInfo.getCameraManger();

        mOpsThread = new HandlerThread("CameraOpsThread");
        mOpsThread.start();
        mOpsHandler = new Handler(mOpsThread.getLooper());

        mUtilityThread = new HandlerThread("UtilityThread");
        mUtilityThread.start();
        mUtilityHandler = new Handler(mUtilityThread.getLooper());

        mUtilityHandler.post(new Runnable() {
            @Override
            public void run() {
                InitializeAllTheThings();
                mAllThingsInitialized = true;
                CamLogger.v(TAG, "ImageReader initialization done.");
            }
        });

        mOpsCondittion = new ConditionVariable();
    }

    private void InitializeAllTheThings() {
        // Thread to handle returned JPEGs.
        mJpegListenerThread = new HandlerThread("CameraJpegThread");
        mJpegListenerThread.start();
        mJpegListenerHandler = new Handler(mJpegListenerThread.getLooper());

        // Create ImageReader to receive JPEG image buffers via reprocessing.
        mJpegImageReader = ImageReader.newInstance(
                mCamInfo.getYuvStreamSize().getWidth(),
                mCamInfo.getYuvStreamSize().getHeight(),
                ImageFormat.JPEG,
                2);
        mJpegImageReader.setOnImageAvailableListener(mJpegImageListener, mJpegListenerHandler);

        mYuvImageReader = ImageReader.newInstance(
                mCamInfo.getYuvStreamSize().getWidth(),
                mCamInfo.getYuvStreamSize().getHeight(),
                ImageFormat.YUV_420_888,
                YUV_IMAGEREADER_SIZE);
        mYuvImageReader.setOnImageAvailableListener(mYuvImageListener, mOpsHandler);

        mRawImageReader = ImageReader.newInstance(
                mCamInfo.getRawStreamSize().getWidth(),
                mCamInfo.getRawStreamSize().getHeight(),
                mCamInfo.getRawStreamFormat(),                     //ImageFormat.RAW10
                8);
        mRawImageReader.setOnImageAvailableListener(mRawImageListener,null);
    }

    private void CamOpsReq(CamCmd cmd)
    {
        try {
            mSemaphore.acquire();
            mCamCmd = cmd;
            mOpsCondittion.close();
            CamLogger.v(TAG, "CAM CMD:"+mCamCmd);
        } catch (InterruptedException e) {
            CamLogger.e(TAG,"Sem acquire ERROR!");
        }
    }

    private void CamOpsFinish(CamCmd cmd,CamCmdResult result)
    {
        mCamOpResult = result;

        if(mCamCmd == cmd)
            mOpsCondittion.open();

        mSemaphore.release();
        CamLogger.v(TAG, "Last Cam cmd is finish!");
    }

    public void openCamera()
    {
        CamLogger.d(TAG, "STARTUP_REQUIREMENT opening camera " + mCamInfo.getCameraId());
        CamOpsReq(CamCmd.CAM_OPEN);
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                CameraTime.t_open_start = SystemClock.elapsedRealtime();
                try {
                    mCameraManager.openCamera(mCamInfo.getCameraId(), mCameraStateCallback, null);
                } catch (CameraAccessException e) {
                    CamLogger.e(TAG, "Unable to openCamera().");
                }
            }
        });
    }

    public void closeCamera()
    {
        CamLogger.d(TAG, "Closing camera " + mCamInfo.getCameraId());
        CamOpsReq(CamCmd.CAM_CLOSE);
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCameraDevice != null) {
                    if(mCurrentCaptureSession != null) {
                        try {
                            mCurrentCaptureSession.abortCaptures();
                        } catch (CameraAccessException e) {
                            CamLogger.e(TAG, "Could not abortCaptures().");
                        }
                        mCurrentCaptureSession = null;
                    }
                    mCameraDevice.close();
                }
                CamOpsFinish(CamCmd.CAM_CLOSE,CamCmdResult.CAM_OP_SUCCESS);
            }
        });
        CamLogger.d(TAG, "Done Closing camera " + mCamInfo.getCameraId());
    }
    public void StopCamera()
    {
        CamLogger.d(TAG, "StopCamera: " + mCamInfo.getCameraId());
        mUtilityThread.quit();
        mOpsThread.quit();

                if (mCameraDevice != null) {
                    if(mCurrentCaptureSession != null) {
                        try {
                            mCurrentCaptureSession.abortCaptures();
                        } catch (CameraAccessException e) {
                            CamLogger.e(TAG, "Could not abortCaptures().");
                        }
                        mCurrentCaptureSession = null;
                    }
                    mCameraDevice.close();
                }

        CamLogger.d(TAG, "StopCamera!!! ");
    }

    public void startPreview(Surface surface)
    {
        mPreviewSurface = surface;
        mZslMode = false;
        CamStartPreview();
    }

    public void startPreview(Surface surface,boolean ZslMode)
    {
        mPreviewSurface = surface;
        mZslMode = ZslMode;
        CamStartPreview();
    }
    public void startRecordingPreview(Surface surface)
    {
        mPreviewSurface = surface;
        mZslMode = false;
        try {
            setUpMediaRecorder();
        } catch (IOException e) {
            e.printStackTrace();
        };
        CamStartVideoPreview();
    }

    private void CamStartVideoPreview()
    {
        CamLogger.d(TAG, "Start CamStartVideoPreview..");
        CamOpsReq(CamCmd.CAM_STARTPREVIEW);
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCameraDevice != null && mPreviewSurface != null && mMediaRecorder != null) {
                    // It used to be: this needed to be posted on a Handler.
                    startVideoCaptureSession();
                }
            }
        });
    }

    private void setUpMediaRecorder() throws IOException {
        mMediaRecorder = new MediaRecorder();
        mVideoSize = mCamInfo.getVideoStreamSize();

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mCamInfo.getVideoFilePath());
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
//        int orientation = ORIENTATIONS.get(rotation);
//        mMediaRecorder.setOrientationHint(orientation);
        mMediaRecorder.prepare();
    }

    private void startVideoCaptureSession() {
        CameraTime.t_session_go = SystemClock.elapsedRealtime();

        CamLogger.d(TAG, "Start Configuring Video CaptureSession..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(3);

        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(mMediaRecorder.getSurface());

        try {
            mCameraDevice.createCaptureSession(outputSurfaces, mVideoSessionStateCallback, null);
            CamLogger.v(TAG, "  Call to startVideoCaptureSession complete.");
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Error configuring ISP.");
        }
    }

    private void VideoPreviewCaptureRequest()
    {
        CameraTime.t_burst = SystemClock.elapsedRealtime();
        CamLogger.d(TAG, "VideoPreviewCaptureRequest...");
        try {
            mFirstFrameArrived = false;
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            b1.addTarget(mPreviewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            b1.addTarget(recorderSurface);

            mCurrentCaptureSession.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler);
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    private void CamStartPreview()
    {
        CamLogger.d(TAG, "Start CamStartPreview..");
        CamOpsReq(CamCmd.CAM_STARTPREVIEW);
        mOpsHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mCameraDevice != null && mPreviewSurface != null) {
                        // It used to be: this needed to be posted on a Handler.
                        startCaptureSession();
                    }
                }
        });
    }

    private void startCaptureSession() {
        CameraTime.t_session_go = SystemClock.elapsedRealtime();

        CamLogger.d(TAG, "Start Configuring CaptureSession..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(3);

        outputSurfaces.add(mPreviewSurface);

        try {
                mCameraDevice.createCaptureSession(outputSurfaces, mSessionStateCallback, null);
                CamLogger.v(TAG, "  Call to createCaptureSession complete.");
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Error configuring ISP.");
        }
    }

    private void PreviewCaptureRequest()
    {
        CameraTime.t_burst = SystemClock.elapsedRealtime();
        CamLogger.d(TAG, "PreviewCaptureRequest...");
        try {
            mFirstFrameArrived = false;
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b1.addTarget(mPreviewSurface);
            mCurrentCaptureSession.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler);
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    private void JpegCaptureRequest()
    {
        CameraTime.t_burst = SystemClock.elapsedRealtime();
        CamLogger.d(TAG, "JpegCaptureRequest...");
        try {
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b1.addTarget(mJpegImageReader.getSurface());
            mCurrentCaptureSession.capture(b1.build(), null, mOpsHandler);
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    public void takePicture()
    {
        CamLogger.v(TAG, "takePicture..");
        CamOpsReq(CamCmd.CAM_TAKEPICTURE);
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                mReprocessingRequestNanoTime = System.nanoTime();

                if(mZslMode)
                    takeZslPicture();
                else
                    takeCapturePicture();
            }
        });
    }

    private void takeCapturePicture()
    {
        JpegPictureCaptureSession();
    }

    private void JpegPictureCaptureSession() {
        CameraTime.t_session_go = SystemClock.elapsedRealtime();

        CamLogger.v(TAG, "Start Configuring JpegPictureCaptureSession..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(3);

        outputSurfaces.add(mJpegImageReader.getSurface());

        try {
            mCameraDevice.createCaptureSession(outputSurfaces, mJpegSessionStateCallback, null);
            CamLogger.v(TAG, "Call to JpegPictureCaptureSession complete.");
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Error configuring ISP.");
        }
    }

    private void takeZslPicture()
    {
/*
        try {

            CaptureRequest.Builder b1 = mCameraDevice.createReprocessCaptureRequest(mLastTotalCaptureResult);
            // Todo: Read current orientation instead of just assuming device is in native orientation
            b1.set(CaptureRequest.JPEG_QUALITY, (byte) 95);
            b1.addTarget(mJpegImageReader.getSurface());
            mCurrentCaptureSession.capture(b1.build(), mReprocessingCaptureCallback, mOpsHandler);

            mReprocessingRequestNanoTime = System.nanoTime();
        } catch (CameraAccessException e) {
            CamLogger.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
*/
        mYuvLastReceivedImage = null;
        CamLogger.v(TAG, "  Reprocessing request submitted.");
    }

    public void startRecording()
    {
        try {
            CamOpsReq(CamCmd.CAM_STARTRECORDING);
            // UI
            mIsRecordingVideo = true;

            // Start recording
            CamLogger.v(TAG, "  startRecording...");
            mMediaRecorder.start();
            CamOpsFinish(CamCmd.CAM_STARTRECORDING,CamCmdResult.CAM_OP_SUCCESS);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void stopRecording()
    {
        CamOpsReq(CamCmd.CAM_STOPRECORDING);
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        CamOpsFinish(CamCmd.CAM_STOPRECORDING,CamCmdResult.CAM_OP_SUCCESS);
        CamLogger.v(TAG, "  stopRecording!!!");
    }

    public Boolean OpsIsFinish()
    {
        CamOpsReq(CamCmd.CAM_NULL);
        CamLogger.v(TAG, "Op is Finish!");
        CamOpsFinish(CamCmd.CAM_NULL,CamCmdResult.CAM_OP_SUCCESS);
        return true;
    }

    public Boolean OpsResult()
    {
        CamLogger.v(TAG, "Cam WAIT cmd finish!!!");
        mOpsCondittion.block();
        if(mCamOpResult == CamCmdResult.CAM_OP_SUCCESS)
            return true;
        else if(mCamOpResult == CamCmdResult.CAM_OP_FALSE) {
            CamLogger.e(TAG, "Cam CMD:"+mCamCmd +" Result is FALSE!!!");
            return false;
        }
        else {
            CamLogger.e(TAG, "Cam result is INVAL!!!");
            return false;
        }
    }
}
