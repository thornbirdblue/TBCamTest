package cc.thornbird.tbcamtest;

import android.view.Surface;
import android.os.SystemClock;
import android.os.Handler;
import android.os.HandlerThread;

import android.util.Log;

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
    private static int LOG_NTH_FRAME = 30;

    private CamTestReprot mCamTestReport = null;

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

    private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback(){
        public void onOpened(CameraDevice camera)
        {
            CameraTime.t_open_end = SystemClock.elapsedRealtime();
            mCameraDevice = camera;
            Log.d(TAG, "STARTUP_REQUIREMENT Done opening camera " + mCamInfo.getCameraId() +
                    ". HAL open took: (" + (CameraTime.t_open_end - CameraTime.t_open_start) + " ms)");
            CamOpsFinish();
        }
        public void onDisconnected(CameraDevice camera)
        {
            Log.d(TAG,"onDisconnected");
        }
        public void onError(CameraDevice camera, int error)
        {
            Log.d(TAG,"onError"+"error val is"+error);
        }
    };

    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback(){
        public void onConfigured(CameraCaptureSession session)
        {
            mCurrentCaptureSession = session;
            Log.d(TAG, "capture session onConfigured().");
            PreviewCaptureRequest();
        }
        public void onReady(CameraCaptureSession session) {
            Log.d(TAG, "capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)");
        }
        public void onConfigureFailed(CameraCaptureSession session)
        {
            Log.d(TAG,"onConfigureFailed");
        }
    };

    private CameraCaptureSession.StateCallback mJpegSessionStateCallback = new CameraCaptureSession.StateCallback(){
        public void onConfigured(CameraCaptureSession session)
        {
            mCurrentCaptureSession = session;
            Log.d(TAG, "JPEG capture session onConfigured().");
            JpegCaptureRequest();
        }
        public void onReady(CameraCaptureSession session) {
            Log.d(TAG, "JPEG capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)");
        }
        public void onConfigureFailed(CameraCaptureSession session)
        {
            Log.d(TAG,"JPEG onConfigureFailed");
        }
    };

    private CameraCaptureSession.StateCallback mVideoSessionStateCallback = new CameraCaptureSession.StateCallback(){
        public void onConfigured(CameraCaptureSession session)
        {
            mCurrentCaptureSession = session;
            Log.d(TAG, "VIDEO capture session onConfigured().");
            VideoPreviewCaptureRequest();
        }
        public void onReady(CameraCaptureSession session) {
            Log.d(TAG, "VIDEO capture session onReady().  HAL capture session took: (" + (SystemClock.elapsedRealtime() - CameraTime.t_session_go) + " ms)");
        }
        public void onConfigureFailed(CameraCaptureSession session)
        {
            Log.d(TAG,"VIDEO onConfigureFailed");
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            if (!mFirstFrameArrived) {
                CamOpsFinish();
                mFirstFrameArrived = true;
                long now = SystemClock.elapsedRealtime();
                long dt = now - CameraTime.t0;
                long camera_dt = now - CameraTime.t_session_go + CameraTime.t_open_end - CameraTime.t_open_start;
                long repeating_req_dt = now - CameraTime.t_burst;
                Log.d(TAG, "App control to first frame: (" + dt + " ms)");
                Log.d(TAG, "HAL request to first frame: (" + repeating_req_dt + " ms) " + " Total HAL wait: (" + camera_dt + " ms)");
            }
            else
                Log.d(TAG, "Receive Preview frame!");
        }
    };

    private CameraCaptureSession.CaptureCallback mJpegCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                Log.d(TAG, "JpegCaptureCallback: onCaptureCompleted!!!");
                CamOpsFinish();
        }
    };

    ImageReader.OnImageAvailableListener mJpegImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned JPEG");
                        return;
                    }
                    Image.Plane plane0 = img.getPlanes()[0];
                    final ByteBuffer buffer = plane0.getBuffer();
                    long dt = System.nanoTime() - mReprocessingRequestNanoTime;
                    Log.d(TAG, String.format("JPEG buffer available, w=%d h=%d time=%d size=%d dt=%.1f ms",
                            img.getWidth(), img.getHeight(), img.getTimestamp(), buffer.capacity(), 0.000001 * dt));
                    // Save JPEG on the utility thread,
                    final byte[] jpegBuf;
                    if (buffer.hasArray()) {
                        jpegBuf = buffer.array();
                    } else {
                        jpegBuf = new byte[buffer.capacity()];
                        buffer.get(jpegBuf);
                    }
                    saveFile(jpegBuf,img.getWidth(), img.getHeight(),0);
                    img.close();
              }
            };
    ImageReader.OnImageAvailableListener mYuvImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV1");
                        return;
                    }

                    Image.Plane[] plane = img.getPlanes();
                    final byte[][] DataBuf = new byte[3][];
                    for(int i=0;i<plane.length;i++) {
                        final ByteBuffer buffer = plane[i].getBuffer();
                        Log.d(TAG,"ByteBuffer: "+buffer.capacity());
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
                    Log.d(TAG,"Date len:"+DataBuf[0].length+" "+DataBuf[1].length);

                    mUtilityHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            saveFile(saveData, mCamInfo.getYuvStreamSize().getWidth(), mCamInfo.getYuvStreamSize().getHeight(), 1);
                        }
                    });

                    Log.d(TAG,"YuvImageListener RECIEVE img!!!");

                    if (mYuvLastReceivedImage != null) {
                        mYuvLastReceivedImage.close();
                    }
                    mYuvLastReceivedImage = img;
                    if (++mYuvImageCounter % LOG_NTH_FRAME == 0) {
                        Log.v(TAG, "YUV buffer available, Frame #=" + mYuvImageCounter + " w=" + img.getWidth() + " h=" + img.getHeight() + " time=" + img.getTimestamp());
                    }

                }
            };

    ImageReader.OnImageAvailableListener mRawImageListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image img = reader.acquireLatestImage();
                    if (img == null) {
                        Log.e(TAG, "Null image returned YUV1");
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

                    saveFile(DateBuf,img.getWidth(), img.getHeight(),2);

                    mRawLastReceivedImage = img;
                    Log.d(TAG,"mRawImageListener RECIEVE img!!!");
                }
            };

    private void saveFile(byte[] Data,int w,int h,int type){
        String filename = "";
        String filetype = "";
        try {
            switch(type)
            {
                case 0:
                    filetype="JPG";
                    break;
                case 1:
                    filetype="yuv";
                    break;
                case 2:
                    filetype="raw";
                    break;
                default:
                    Log.w(TAG,"unknow file type");
            }

            filename = String.format("/sdcard/DCIM/Camera/SNAP_%dx%d_%d.%s", w,h,System.currentTimeMillis(),filetype);
            File file;
            while (true) {
                file = new File(filename);
                if (file.createNewFile()) {
                    break;
                }
            }

            long t0 = SystemClock.uptimeMillis();
            OutputStream os = new FileOutputStream(file);
            os.write(Data);
            os.flush();
            os.close();
            long t1 = SystemClock.uptimeMillis();

            Log.d(TAG, String.format("Write data(%d) %d bytes as %s in %.3f seconds;%s",type,
                    Data.length, file, (t1 - t0) * 0.001,filename));
        } catch (IOException e) {
            Log.e(TAG, "Error creating new file: ", e);
        }
    }


    public Api2Camera(CameraInfoCache mCIF,CamTestReprot mCamReport)
    {
        mCamTestReport = mCamReport;
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
                Log.v(TAG, "ImageReader initialization done.");
            }
        });
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

    private void CamOpsReq()
    {
        Log.v(TAG, "REQ Cam Op Lock!");
        try {
            mSemaphore.acquire();
        } catch (InterruptedException e) {
            Log.e(TAG,"Sem acquire ERROR!");
        }
    }

    private void CamOpsFinish()
    {
        Log.v(TAG, "Last Cam cmd is finish!");
        mSemaphore.release();
    }

    public void openCamera()
    {
        Log.d(TAG, "STARTUP_REQUIREMENT opening camera " + mCamInfo.getCameraId());
        CamOpsReq();
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                CameraTime.t_open_start = SystemClock.elapsedRealtime();
                try {
                    mCameraManager.openCamera(mCamInfo.getCameraId(), mCameraStateCallback, null);
                } catch (CameraAccessException e) {
                    Log.e(TAG, "Unable to openCamera().");
                }
            }
        });
    }

    public void closeCamera()
    {
        Log.d(TAG, "Closing camera " + mCamInfo.getCameraId());
        CamOpsReq();
        mOpsHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mCameraDevice != null) {
                    if(mCurrentCaptureSession != null) {
                        try {
                            mCurrentCaptureSession.abortCaptures();
                        } catch (CameraAccessException e) {
                            Log.e(TAG, "Could not abortCaptures().");
                        }
                        mCurrentCaptureSession = null;
                    }
                    mCameraDevice.close();
                }
                CamOpsFinish();
            }
        });
        Log.d(TAG, "Done Closing camera " + mCamInfo.getCameraId());
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
        Log.d(TAG, "Start CamStartVideoPreview..");
        CamOpsReq();
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

        Log.d(TAG, "Start Configuring Video CaptureSession..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(3);

        outputSurfaces.add(mPreviewSurface);
        outputSurfaces.add(mMediaRecorder.getSurface());

        try {
            mCameraDevice.createCaptureSession(outputSurfaces, mVideoSessionStateCallback, null);
            Log.v(TAG, "  Call to startVideoCaptureSession complete.");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error configuring ISP.");
        }
    }

    private void VideoPreviewCaptureRequest()
    {
        CameraTime.t_burst = SystemClock.elapsedRealtime();
        Log.d(TAG, "VideoPreviewCaptureRequest...");
        try {
            mFirstFrameArrived = false;
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            b1.addTarget(mPreviewSurface);

            Surface recorderSurface = mMediaRecorder.getSurface();
            b1.addTarget(recorderSurface);

            mCurrentCaptureSession.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    private void CamStartPreview()
    {
        Log.d(TAG, "Start CamStartPreview..");
        CamOpsReq();
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

        Log.d(TAG, "Start Configuring CaptureSession..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(3);

        outputSurfaces.add(mPreviewSurface);

        try {
                mCameraDevice.createCaptureSession(outputSurfaces, mSessionStateCallback, null);
                Log.v(TAG, "  Call to createCaptureSession complete.");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error configuring ISP.");
        }
    }

    private void PreviewCaptureRequest()
    {
        CameraTime.t_burst = SystemClock.elapsedRealtime();
        Log.d(TAG, "PreviewCaptureRequest...");
        try {
            mFirstFrameArrived = false;
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            b1.addTarget(mPreviewSurface);
            mCurrentCaptureSession.setRepeatingRequest(b1.build(), mCaptureCallback, mOpsHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    private void JpegCaptureRequest()
    {
        CameraTime.t_burst = SystemClock.elapsedRealtime();
        Log.d(TAG, "JpegCaptureRequest...");
        try {
            CaptureRequest.Builder b1 = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            b1.addTarget(mJpegImageReader.getSurface());
            mCurrentCaptureSession.capture(b1.build(), mJpegCaptureCallback, mOpsHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
    }

    public void takePicture()
    {
        Log.v(TAG, "takePicture..");
        CamOpsReq();
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

        Log.v(TAG, "Start Configuring JpegPictureCaptureSession..");
        List<Surface> outputSurfaces = new ArrayList<Surface>(3);

        outputSurfaces.add(mJpegImageReader.getSurface());

        try {
            mCameraDevice.createCaptureSession(outputSurfaces, mJpegSessionStateCallback, null);
            Log.v(TAG, "Call to JpegPictureCaptureSession complete.");
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error configuring ISP.");
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
            Log.e(TAG, "Could not access camera for issuePreviewCaptureRequest.");
        }
*/
        mYuvLastReceivedImage = null;
        Log.v(TAG, "  Reprocessing request submitted.");
    }

    public void startRecording()
    {
        try {
            // UI
            mIsRecordingVideo = true;

            // Start recording
            Log.v(TAG, "  startRecording...");
            mMediaRecorder.start();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    public void stopRecording()
    {
        // UI
        mIsRecordingVideo = false;
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        Log.v(TAG, "  stopRecording!!!");
    }

    public Boolean OpsIsFinish()
    {
        CamOpsReq();
        Log.v(TAG, "Op is Finish!");
        CamOpsFinish();
        return true;
    }
}
