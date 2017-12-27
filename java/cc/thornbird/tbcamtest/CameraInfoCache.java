package cc.thornbird.tbcamtest;

import android.content.Context;

import android.hardware.Camera;
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
import android.util.Log;
import android.util.Size;
import android.util.SizeF;

import android.view.SurfaceView;
import android.media.MediaRecorder;

import java.io.File;


/**
 * Created by thornbird on 2017/12/15.
 */

public class CameraInfoCache {
    private static final String TAG = "TBCamTest_CAMINFO";

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
    }

    public CameraInfoCache(Context context,SurfaceView mSurface)
    {
        mContext = context;
        mPreviewSurface = mSurface;

        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        try {
            mCameralist = mCameraManager.getCameraIdList();
        } catch (Exception e) {
            Log.e(TAG, "ERROR: Could not get camera ID list / no camera information is available: " + e);
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

                Integer facing = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing == CameraCharacteristics.LENS_FACING_BACK)
                    mCurrentCamInfo = mCamInfo[Integer.parseInt(id)];
            }
        } catch (Exception e) {
            Log.e(TAG, "ERROR: Could not getCameraCharacteristics: " + e);
            return;
        }

        mVideoFile = new File(mContext.getExternalFilesDir(null), "video.mp4");
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
                    Log.v(TAG,"YUV support");
                    mCam.mYuvPicSupport = true;
                    mCam.mLargestYuvSize = returnLargestSize(map.getOutputSizes(formats[i]));
                }
                if (formats[i] == ImageFormat.JPEG) {
                    Log.v(TAG,"JPEG support");
                    mCam.mJpegPicSupport = true;
                    mCam.mLargestJpegSize = returnLargestSize(map.getOutputSizes(formats[i]));
                }
                if (formats[i] == ImageFormat.RAW10 || formats[i] == ImageFormat.RAW_SENSOR) { // TODO: Add RAW12
                    Size size = returnLargestSize(map.getOutputSizes(formats[i]));
                    long stall = map.getOutputStallDuration(formats[i], size);
                    if (stall < lowestStall) {
                        Log.v(TAG,"RAW support");
                        mCam.mRawPicSupport = true;
                        mCam.mRawFormat = formats[i];
                        mCam.mRawSize = size;
                        lowestStall = stall;
                    }
                }
                if (formats[i] == ImageFormat.DEPTH_POINT_CLOUD) {
                    Log.d(TAG,"Depth support");
                    mCam.mDepthSupport = true;
                    Size size = returnLargestSize(map.getOutputSizes(formats[i]));
                    mCam.mDepthCloudSize = size;
                }
            }

        mCam.mVideoSizes = map.getOutputSizes(MediaRecorder.class);
        mCam.mVideoSize = chooseVideoSize(mCam.mVideoSizes);
    }

    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
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
            Log.e(TAG,"Error:No Available CameraId. Set to 0 !!!");
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
        Log.v(TAG,"Save Video FilePath:"+mVideoFile.getAbsolutePath());
        return mVideoFile.getAbsolutePath();
    }
}
