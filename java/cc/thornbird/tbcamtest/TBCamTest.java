package cc.thornbird.tbcamtest;

import android.app.Activity;
import android.graphics.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;

import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.os.Handler;

/**
 * Created by thornbird on 2017/12/15.
 */

public class TBCamTest extends Activity implements CamTestMode.CamTestCallBack {
    private static final String TAG = "TBCamTest";

    private TextView mInfoLab;
    private CameraInfoCache mCamInfo;
    private SurfaceView mSurface;

    private  CamTestMode mCamTestMode = null;

    private Handler mhandler=null;

    private Button mBaseTestButton;
    private Button mBaseFuncTestButton;
    private Button mFeatTestButton;
    private Button mAutoTestButton;
    private Button mPerfTestButton;
    private Button mStressTestButton;
    private Button mStopTestButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mInfoLab = (TextView) findViewById(R.id.InfoLab);
        mSurface = (SurfaceView) findViewById(R.id.preview_view);

        mBaseTestButton = (Button) findViewById(R.id.base_test);
        mBaseFuncTestButton = (Button) findViewById(R.id.base_func_test);
        mFeatTestButton = (Button) findViewById(R.id.feature_test);
        mAutoTestButton = (Button) findViewById(R.id.auto_test);
        mPerfTestButton = (Button) findViewById(R.id.perf_test);
        mStressTestButton = (Button) findViewById(R.id.stree_test);
        mStopTestButton = (Button) findViewById(R.id.stop_test);

        mBaseTestButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view)
                {
                    doBaseTest();
                }
        });
        mStopTestButton.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View view)
                {
                    doStopTest();
                }
        });

        mhandler = new Handler();
    }

    private void doBaseTest()
    {
        setButtonDisable();
        CamLogger.d(TAG,"Do Base Test!!!");

        mCamTestMode = new CamTestMode(CamTestMode.TM_BaseTest_Mode,mCamInfo,this);

        mCamTestMode.run();
    }
    private void setButtonDisable()
    {
        mBaseTestButton.setEnabled(false);
        mBaseFuncTestButton.setEnabled(false);
        mFeatTestButton.setEnabled(false);
        mAutoTestButton.setEnabled(false);
        mPerfTestButton.setEnabled(false);
        mStressTestButton.setEnabled(false);
    }
    private void setButtonEnable()
    {
        mBaseTestButton.setEnabled(true);
        mBaseFuncTestButton.setEnabled(true);
        mFeatTestButton.setEnabled(true);
        mAutoTestButton.setEnabled(true);
        mPerfTestButton.setEnabled(true);
        mStressTestButton.setEnabled(true);
    }

    private void doStopTest()
    {
        if(mCamTestMode != null)
            mCamTestMode.stop();
        setButtonEnable();
    }

    @Override
    protected void onStart(){
        super.onStart();
        mCamInfo = new CameraInfoCache(this,mSurface);
        mCamInfo.printToTextView(mInfoLab);
        setButtonEnable();
    }
    @Override
    protected void onPause(){
        super.onPause();
        CamLogger.d(TAG,"onPause");
        if(mCamTestMode != null)
            mCamTestMode.stop();
        mCamInfo.setPreviewInVisibility();
    }

    public Boolean CamTestIsFinish()
    {
        mhandler.post(new Runnable() {
            @Override
            public void run() {
                setButtonEnable();
            }
        });
        return true;
    }
}
