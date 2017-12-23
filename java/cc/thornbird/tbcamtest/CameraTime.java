package cc.thornbird.tbcamtest;

/**
 * Created by 10910661 on 2017/12/21.
 */

public class CameraTime {
    // Got control in onCreate()
    public static long t0;
    // Sent open() to camera.
    public static long t_open_start;
    // Open from camera done.
    public static long t_open_end;
    // Told camera to configure capture session.
    public static long t_session_go;
    // Told session to do repeating request.
    public static long t_burst;
}
