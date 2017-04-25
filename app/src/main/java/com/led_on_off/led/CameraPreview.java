package com.led_on_off.led;

import android.content.Context;
import android.hardware.Camera;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.util.List;

import static android.content.ContentValues.TAG;
import static com.led_on_off.led.ledControl.decodeYUV420SP;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    private SurfaceHolder mHolder;
    private Camera mCamera;
    float mDist = 0;
    private int[] pixels;
    ledControl v;
    Camera.PreviewCallback cb = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            //transforms NV21 pixel data into RGB pixels pick it up
            int width = camera.getParameters().getPreviewSize().width;
            int height = camera.getParameters().getPreviewSize().height;
            decodeYUV420SP(pixels, data, width,  height);
            int topBar = multipleRowAverage(ledControl.currentTopY,20,height,width);
            int botBar = multipleRowAverage(ledControl.currentBotY,20,height,width);
            //Outuput the value of the top left pixel in the preview to LogCat
            boolean top = false;
            if(isBlack(topBar,80)) {
                Log.i("Pixels", "Top Bar is black");
                top = true;
            }
            boolean bot = false;
            if(isBlack(botBar,80)) {
                Log.i("Pixels", "Bottom Bar is black");
                bot = true;
            }
            v.barsChanged(top,bot);

        }
    };

    public CameraPreview(Context context, Camera camera, ledControl view) {
        super(context);
        v = view;
        mCamera = camera;
        mCamera.setPreviewCallback(cb);

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        pixels = new int[mCamera.getParameters().getPreviewSize().width * mCamera.getParameters().getPreviewSize().height];
        try {
            mCamera.setPreviewDisplay(holder);
            mCamera.setPreviewCallback(cb);
            mCamera.startPreview();
        } catch (IOException e) {
            Log.d(TAG, "Error setting camera preview: " + e.getMessage());
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // empty. Take care of releasing the Camera preview in your activity.
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.
        pixels = new int[mCamera.getParameters().getPreviewSize().width * mCamera.getParameters().getPreviewSize().height];
        if (mHolder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        // start preview with new settings
        try {
            mCamera.setPreviewDisplay(mHolder);
            mCamera.setPreviewCallback(cb);
            mCamera.startPreview();

        } catch (Exception e){
            Log.d(TAG, "Error starting camera preview: " + e.getMessage());
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get the pointer ID
        Camera.Parameters params = mCamera.getParameters();
        int action = event.getAction();

        if (event.getPointerCount() > 1) {
            // handle multi-touch events
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                mDist = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE
                    && params.isZoomSupported()) {
                mCamera.cancelAutoFocus();
                handleZoom(event, params);
            }
        } else {
            // handle single touch events
            if (action == MotionEvent.ACTION_UP) {
                handleFocus(event, params);
            }
        }
        return true;
    }

    //Method from Ketai project! Not mine! See below...
    void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {

        final int frameSize = width * height;

        for (int j = 0, yp = 0; j < height; j++) {
            int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
            for (int i = 0; i < width; i++, yp++) {
                int y = (0xff & ((int) yuv420sp[yp])) - 16;
                if (y < 0)
                    y = 0;
                if ((i & 1) == 0) {
                    v = (0xff & yuv420sp[uvp++]) - 128;
                    u = (0xff & yuv420sp[uvp++]) - 128;
                }

                int y1192 = 1192 * y;
                int r = (y1192 + 1634 * v);
                int g = (y1192 - 833 * v - 400 * u);
                int b = (y1192 + 2066 * u);
                if (r < 0)
                    r = 0;
                else if (r > 262143)
                    r = 262143;
                if (g < 0)
                    g = 0;
                else if (g > 262143)
                    g = 262143;
                if (b < 0)
                    b = 0;
                else if (b > 262143)
                    b = 262143;

                rgb[yp] = 0x00000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
            }
        }
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > mDist) {
            // zoom in
            if (zoom < maxZoom)
                zoom++;
        } else if (newDist < mDist) {
            // zoom out
            if (zoom > 0)
                zoom--;
        }
        mDist = newDist;
        params.setZoom(zoom);
        mCamera.setParameters(params);
    }

    public void handleFocus(MotionEvent event, Camera.Parameters params) {
        int pointerId = event.getPointerId(0);
        int pointerIndex = event.findPointerIndex(pointerId);
        // Get the pointer's current position
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null
                && supportedFocusModes
                .contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            mCamera.autoFocus(new Camera.AutoFocusCallback() {
                @Override
                public void onAutoFocus(boolean b, Camera camera) {
                    // currently set to auto-focus on single touch
                }
            });
        }
    }

    /** Determine the space between the first two fingers */
    private float getFingerSpacing(MotionEvent event) {
        // ...
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float)Math.sqrt(x * x + y * y);
    }

    private int rowAverage(int rownum, int height, int width){
        int r = 0;
        int g = 0;
        int b = 0;
        for(int i = 0; i < height; ++i){
            int rgb = pixels[rownum+i*width];
            r += ((rgb >> 16)& 0x000000ff);
            g += ((rgb >> 8)& 0x000000ff);
            b += ((rgb & 0x000000ff));
        }
        r = r/height;
        g = g/height;
        b = b/height;
        int result = ((((r << 16)&0x00ff0000)|(g << 8)& 0x0000ff00|(b & 0x000000ff))& 0x00ffffff);
        return result;
    }

    private int multipleRowAverage(int rowNum,int length, int height, int width){
        int r = 0;
        int g = 0;
        int b = 0;
        for(int i = 0; i < length; ++i){
            int rgb = rowAverage(rowNum+i,height,width);
            r += ((rgb >> 16)& 0x000000ff);
            g += ((rgb >> 8)& 0x000000ff);
            b += ((rgb & 0x000000ff));
        }
        r = r/length;
        g = g/length;
        b = b/length;
        int result = ((((r << 16)&0x00ff0000)|(g << 8)& 0x0000ff00|(b & 0x000000ff))& 0x00ffffff);
        return result;
    }

    private boolean isBlack(int color,int threshold){
        int r = ((color >> 16)& 0x000000ff);
        int g = ((color >> 8)& 0x000000ff);
        int b = ((color & 0x000000ff));
        if(r < threshold && g < threshold && b < threshold){
            return true;
        }
        else{
            return false;
        }
    }
}
