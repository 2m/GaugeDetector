package lt.dvim.gaugedetector;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Pair;
import android.widget.TextView;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private CameraBridgeViewBase mOpenCVCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        //TextView tv = (TextView) findViewById(R.id.sample_text);
        //tv.setText(stringFromJNI());

        mOpenCVCamera = (CameraBridgeViewBase) findViewById(R.id.opencvcamera_view);
        mOpenCVCamera.setVisibility(CameraBridgeViewBase.VISIBLE);
        mOpenCVCamera.enableView();
        mOpenCVCamera.setCvCameraViewListener(this);

    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    @Override
    public void onCameraViewStarted(int width, int height) {

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        Mat im = inputFrame.rgba();

        final Pair<Mat, Circle> gauge = calibrateGauge(im);
        final Mat value = getCurrentValue(gauge.first, gauge.second);

        return value;
    }

    /**
     * This function should be run using a test image in order to calibrate the range available to the dial as well as the
     * units.  It works by first finding the center point and radius of the gauge.  Then it draws lines at hard coded intervals
     * (separation) in degrees.  It then prompts the user to enter position in degrees of the lowest possible value of the gauge,
     * as well as the starting value (which is probably zero in most cases but it won't assume that).  It will then ask for the
     * position in degrees of the largest possible value of the gauge. Finally, it will ask for the units.  This assumes that
     * the gauge is linear (as most probably are).
     * It will return the min value with angle in degrees (as a tuple), the max value with angle in degrees (as a tuple),
     * and the units (as a string).
     */
    private Pair<Mat, Circle> calibrateGauge(Mat img) {
        final int height = img.height();
        final int width = img.width();

        final Mat gray = new Mat();
        Imgproc.cvtColor(img, gray, Imgproc.COLOR_RGBA2GRAY);

        // detect circles
        // restricting the search from 35-48% of the possible radii gives fairly good results across different samples.  Remember that
        // these are pixel values which correspond to the possible radii search range.
        final Mat circles = new Mat();
        Imgproc.HoughCircles(gray, circles, Imgproc.HOUGH_GRADIENT, 1, 20, 100, 50, (int)(height*0.35), (int)(height*0.48));

        // average found circles, found it to be more accurate than trying to tune HoughCircles parameters to get just the right one
        Circle c = avgCircles(circles, circles.cols());

        // draw center and circle
        Imgproc.circle(img, new Point(c.x, c.y), c.r, new Scalar(0, 0, 255), 3, Imgproc.LINE_AA, 0);
        Imgproc.circle(img, new Point(c.x, c.y), 2, new Scalar(0, 255, 0), 3, Imgproc.LINE_AA, 0);

        return new Pair<>(img, c);
    }

    private Mat getCurrentValue(Mat img, Circle c) {

        final Mat hsv = new Mat();
        Imgproc.cvtColor(img, hsv, Imgproc.COLOR_RGB2HSV);

        final Scalar lowerRange = new Scalar(15, 30, 70);
        final Scalar upperRange = new Scalar(35, 50, 110);

        final Mat mask = new Mat();
        Core.inRange(hsv, lowerRange, upperRange, mask);

        final double thresh = 175.0;
        final double maxValue = 255.0;

        // apply thresholding which helps for finding lines
        final Mat dst = new Mat();
        Imgproc.threshold(mask, dst, thresh, maxValue, Imgproc.THRESH_BINARY_INV);

        // found Hough Lines generally performs better without Canny / blurring, though there were a couple exceptions where it would only work with Canny / blurring
        //Imgproc.medianBlur(dst, dst, 5);
        //Imgproc.Canny(dst, dst, 50, 150);
        //Imgproc.GaussianBlur(dst, dst, new Size(5, 5), 0);

        final int minLineLength = 30;
        final int maxLineGap = 0;

        final Mat lines = new Mat();
        Imgproc.HoughLinesP(dst, lines, 3, Math.PI / 180, 100, minLineLength, maxLineGap);

        for (int i = 0; i < lines.cols(); i++) {
            double line[] = lines.get(0, i);
            Imgproc.line(img, new Point(line[0], line[1]), new Point(line[2], line[3]), new Scalar(0, 255, 0), 2);
        }

        return img;
    }

    private Circle avgCircles(Mat circles, int b) {
        int avgX = 0;
        int avgY = 0;
        int avgR = 0;

        for (int i = 0; i < b; i++) {
            double circle[] = circles.get(0, i);
            avgX += circle[0];
            avgY += circle[1];
            avgR += circle[2];

            avgX = avgX/b;
            avgY = avgY/b;
            avgR = avgR/b;
        }

        return new Circle(avgX, avgY, avgR);
    }

    private class Circle {
        int x;
        int y;
        int r;

        public Circle(int x, int y, int r) {
            this.x = x;
            this.y = y;
            this.r = r;
        }
    }
}
