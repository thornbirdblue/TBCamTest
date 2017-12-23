package cc.thornbird.tbcamtest;

import android.util.Size;
import android.view.Surface;

/**
 * Created by thornbird on 2017/12/21.
 */

public interface CameraInterface {
    Boolean openCamera();

    Boolean closeCamera();

    Boolean startPreview(Surface surface);
    Boolean startPreview(Surface surface,boolean ZslMode);

    Boolean takePicture();

    void startRecording();
    void stopRecording();
}
