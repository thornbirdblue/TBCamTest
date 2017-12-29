package cc.thornbird.tbcamtest;

import android.content.Context;

import android.hardware.Camera;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;

import android.hardware.Camera;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Size;
import android.util.SizeF;

import android.view.SurfaceView;
import android.media.MediaRecorder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;


/**
 * Created by thornbird on 2017/12/15.
 */

public class CameraInfoCache {
    private static final String TAG = "TBCamTest_CAMINFO";
    private static final String CamPicSaveDir = "/sdcard/DCIM/Camera/";

    private String mDevicehardware = Build.HARDWARE;
    private String mSystemModel = Build.MODEL;
    private String mPhoneSerial = Build.SERIAL;

    private int mCamNum;

    private Context mContext;
    private CameraManager mCameraManager;
    private String[] mCameralist;

    private final static int CamMaxNum = 6;
    private CameraInfo[] mCamInfo;
    private CameraInfo mCurrentCamInfo;

    private SurfaceView mPreviewSurface;
    private File mVideoFile;


    class CameraInfo {
        private String mCameraId = null;
        public CameraCharacteristics mCameraCharacteristics;
        public Boolean mJpegPicSupport = false;
        public Boolean mYuvPicSupport = false;
        public Boolean mRawPicSupport = false;
        public Boolean mDepthSupport = false;
        public Size mLargestYuvSize;
        public Size mLargestJpegSize;
        public Size mRawSize;

        public Integer mRawFormat;
        public Size mDepthCloudSize = null;

        public Size[] mVideoSizes;
        public Size mVideoSize;

        public int  mHardwareLeve;

        public Integer mMaxInputStreams;
        public Integer mMaxOutputProc;
        public Integer mMaxOutputProcStalling;
        public Integer mMaxOutputRaw;
        public Integer mMaxOutputStreams;

        public Boolean mYuvReprocSupport = false;
        public Boolean mPrivateReprocSupport = false;
        public Boolean mRawReprocSupport = false;
    }

    public CameraInfoCache(Context context,SurfaceView mSurface)
    {
        mContext = context;
        mPreviewSurface = mSurface;

        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameralist = mCameraManager.getCameraIdList();
        } catch (Exception e) {
            CamLogger.e(TAG, "ERROR: Could not get camera ID list / no camera information is available: " + e);
            return;
        }
        mCamNum = mCameralist.length;

        mCamInfo = new CameraInfo[CamMaxNum];
        for(int i=0;i<CamMaxNum;i++)
            mCamInfo[i] = new CameraInfo();

        try {
            for(String id : mCameralist) {
                CameraCharacteristics mCameraCharacteristics = mCameraManager.getCameraCharacteristics(id);

                mCamInfo[Integer.parseInt(id)].mCameraId = id;
                mCamInfo[Integer.parseInt(id)].mCameraCharacteristics = mCameraCharacteristics;

                getPictureSizeList(mCamInfo[Integer.parseInt(id)],mCameraCharacteristics);

                getStreamInfoList(mCamInfo[Integer.parseInt(id)],mCameraCharacteristics);

                Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing == CameraCharacteristics.LENS_FACING_BACK)
                    mCurrentCamInfo = mCamInfo[Integer.parseInt(id)];
            }
        } catch (Exception e) {
            CamLogger.e(TAG, "ERROR: Could not getCameraCharacteristics: " + e);
            return;
        }

        mVideoFile = new File(CamPicSaveDir, "video.mp4");
    }
    public CameraInfoCache(CameraInfoCache mCIF){
        this.mCamNum = mCIF.mCamNum;
        this.mContext = mCIF.mContext;
        this.mCameraManager = mCIF.mCameraManager;
        this.mCameralist = mCIF.mCameralist;
        this.mCamInfo = mCIF.mCamInfo;

        this.mPreviewSurface = mCIF.mPreviewSurface;
    }

    public void printToTextView(TextView mTextView)
    {
        StringBuffer sb = new StringBuffer();
        sb.append("硬件名称： "+ mDevicehardware);
        sb.append("\n版本： "+ mSystemModel);
        sb.append("\n硬件序列号： "+ mPhoneSerial);
        sb.append("\n");
        sb.append("\nCamera数量： "+ mCamNum);
        sb.append("\n");
        printPicSizeToStringBuffer(sb);

        mTextView.setText(sb);
    }

    private Size returnLargestSize(Size[] sizes) {
        Size largestSize = null;
        int area = 0;
        for (int j = 0; j < sizes.length; j++) {
            if (sizes[j].getHeight() * sizes[j].getWidth() > area) {
                area = sizes[j].getHeight() * sizes[j].getWidth();
                largestSize = sizes[j];
            }
        }
        return largestSize;
    }

    private void getPictureSizeList(CameraInfo mCam,CameraCharacteristics mCC)
    {
            if (mCC == null)
                return;

            // Store YUV_420_888, JPEG, Raw info
            StreamConfigurationMap map = mCC.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] formats = map.getOutputFormats();
            long lowestStall = Long.MAX_VALUE;
            for (int i = 0; i < formats.length; i++) {
                if (formats[i] == ImageFormat.YUV_420_888) {
                    CamLogger.v(TAG,"YUV support");
                    mCam.mYuvPicSupport = true;
                    mCam.mLargestYuvSize = returnLargestSize(map.getOutputSizes(formats[i]));
                }
                if (formats[i] == ImageFormat.JPEG) {
                    CamLogger.v(TAG,"JPEG support");
                    mCam.mJpegPicSupport = true;
                    mCam.mLargestJpegSize = returnLargestSize(map.getOutputSizes(formats[i]));
                }
                if (formats[i] == ImageFormat.RAW10 || formats[i] == ImageFormat.RAW_SENSOR) { // TODO: Add RAW12
                    Size size = returnLargestSize(map.getOutputSizes(formats[i]));
                    long stall = map.getOutputStallDuration(formats[i], size);
                    if (stall < lowestStall) {
                        CamLogger.v(TAG,"RAW support");
                        mCam.mRawPicSupport = true;
                        mCam.mRawFormat = formats[i];
                        mCam.mRawSize = size;
                        lowestStall = stall;
                    }
                }
                if (formats[i] == ImageFormat.DEPTH_POINT_CLOUD) {
                    CamLogger.d(TAG,"Depth support");
                    mCam.mDepthSupport = true;
                    Size size = returnLargestSize(map.getOutputSizes(formats[i]));
                    mCam.mDepthCloudSize = size;
                }
            }

        mCam.mVideoSizes = map.getOutputSizes(MediaRecorder.class);
        mCam.mVideoSize = chooseVideoSize(mCam.mVideoSizes);
    }

    private void getStreamInfoList(CameraInfo mCam,CameraCharacteristics mCC)
    {
        if (mCC == null)
            return;

        mCam.mHardwareLeve = mCC.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

        int[] caps = mCC.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        for (int c: caps) {
            if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING)
                mCam.mYuvReprocSupport=true;
            else if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)
                mCam.mPrivateReprocSupport=true;
            else if (c == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
                mCam.mRawReprocSupport=true;
        }

        mCam.mMaxInputStreams = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_INPUT_STREAMS);

        mCam.mMaxOutputProc = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC );
        mCam.mMaxOutputProcStalling = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_PROC_STALLING );
        mCam.mMaxOutputRaw = mCC.get(CameraCharacteristics.REQUEST_MAX_NUM_OUTPUT_RAW);
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        CamLogger.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    private void printPicSizeToStringBuffer(StringBuffer Sb)
    {
        for(int id=0;id<mCamNum;id++) {
            Sb.append("\nCamera " + id);

            if (mCamInfo[id].mJpegPicSupport)
                Sb.append("\nJpeg Max Size： " + mCamInfo[id].mLargestJpegSize.toString());

            if (mCamInfo[id].mYuvPicSupport)
                Sb.append("\nYUV Max Size： " + mCamInfo[id].mLargestYuvSize.toString());

            if (mCamInfo[id].mRawPicSupport)
                Sb.append("\nRAW Max Size： " + mCamInfo[id].mRawSize.toString() + " RAW format: " + mCamInfo[id].mRawFormat);

            if (mCamInfo[id].mDepthSupport)
                Sb.append("\nDepth Max Size： " + mCamInfo[id].mDepthCloudSize.toString());

            Sb.append("\nHardware Level： " + mCamInfo[id].mHardwareLeve);
            if(mCamInfo[id].mYuvReprocSupport)
                 Sb.append("\nYuvReprocess Support! ");
            if(mCamInfo[id].mPrivateReprocSupport)
                Sb.append("\nPrivateReprocess Support! ");

            Sb.append("\nMaxInputStreams： " + mCamInfo[id].mMaxInputStreams);
            Sb.append("\nMaxOutputProc： " + mCamInfo[id].mMaxOutputProc);
            Sb.append("\nMaxOutputProcStalling： " + mCamInfo[id].mMaxOutputProcStalling);
            Sb.append("\nMaxOutputRaw： " + mCamInfo[id].mMaxOutputRaw);

            Sb.append("\n");
        }
        Sb.append("\n");
    }

    public Context getContext() {
        return mContext;
    }

    public CameraManager getCameraManger() {
        return mCameraManager;
    }

    public SurfaceView getPreviewSurface() {
        return mPreviewSurface;
    }

    public String getCameraId() {
        if(mCurrentCamInfo.mCameraId == null) {
            CamLogger.e(TAG,"Error:No Available CameraId. Set to 0 !!!");
            mCurrentCamInfo.mCameraId = "0";
        }
        return mCurrentCamInfo.mCameraId;
    }

    public void setPreviewVisibility()
    {
        mPreviewSurface.setVisibility(View.VISIBLE);
    }
    public void setPreviewInVisibility()
    {
        mPreviewSurface.setVisibility(View.INVISIBLE);
    }

    public Size getJpegStreamSize() {
        return mCurrentCamInfo.mLargestJpegSize;
    }

    public Size getYuvStreamSize() {
        return mCurrentCamInfo.mLargestYuvSize;
    }

    public Size getRawStreamSize() {
        return mCurrentCamInfo.mRawSize;
    }

    public Integer getRawStreamFormat() {
        return mCurrentCamInfo.mRawFormat;
    }

    public Size getVideoStreamSize() {
        return mCurrentCamInfo.mVideoSize;
    }

    public String getVideoFilePath()
    {
        CamLogger.v(TAG,"Save Video FilePath:"+mVideoFile.getAbsolutePath());
        return mVideoFile.getAbsolutePath();
    }

    public void saveFile(byte[] Data,int w,int h,int type){
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
                    CamLogger.w(TAG,"unknow file type");
            }

            filename = String.format("%sTBCam_%dx%d_%d.%s",CamPicSaveDir,w,h,System.currentTimeMillis(),filetype);
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

            CamLogger.d(TAG, String.format("Write data(%d) %d bytes as %s in %.3f seconds;%s",type,
                    Data.length, file, (t1 - t0) * 0.001,filename));
        } catch (IOException e) {
            CamLogger.e(TAG, "Error creating new file: ", e);
        }
    }
}
