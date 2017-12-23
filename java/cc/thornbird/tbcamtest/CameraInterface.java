package cc.thornbird.tbcamtest;

import android.util.Size;
import android.view.Surface;

/**
 * Created by thornbird on 2017/12/21.
 */

public interface CameraInterface {
    void openCamera();

    void closeCamera();

    void startPreview(Surface surface);
    void startPreview(Surface surface,boolean ZslMode);

    void takePicture();

    void startRecording();
    void stopRecording();

    Boolean OpsIsFinish();
}
