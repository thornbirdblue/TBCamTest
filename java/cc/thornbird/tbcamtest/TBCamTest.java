package cc.thornbird.tbcamtest;

import android.app.Activity;
import android.graphics.Camera;
import android.os.Bundle;
import android.support.annotation.Nullable;

import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import android.util.Log;

/**
 * Created by 10910661 on 2017/12/15.
 */

public class TBCamTest extends Activity {
    private static final String TAG = "TBCamTest";

    private TextView mInfoLab;
    private CameraInfoCache mCamInfo;
    private Button mBaseTestButton;
    private Button mStopTestButton;
    private SurfaceView mSurface;

    private  CamTestMode mCamTestMode;

        @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mInfoLab = (TextView) findViewById(R.id.InfoLab);
        mSurface = (SurfaceView) findViewById(R.id.preview_view);

        mBaseTestButton = (Button) findViewById(R.id.base_test);
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
    }

    private void doBaseTest()
    {
        setButtonDisable();
        Log.d(TAG,"Do Base Test!!!");

        mCamTestMode = new CamTestMode(CamTestMode.TM_BaseTest_Mode,mCamInfo);

        mCamTestMode.run();
    }
    private void setButtonDisable()
    {
        mBaseTestButton.setEnabled(false);
    }
    private void setButtonEnable()
    {
        mBaseTestButton.setEnabled(true);
    }

    private void doStopTest()
    {
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
    protected void onStop(){
        super.onStop();
        mCamTestMode.stop();
        mCamInfo.setPreviewInVisibility();
    }
}
