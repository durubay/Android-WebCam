package com.faptastic.webcam;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class BackgroundPhotoService extends Service {
    public final String TAG = "WebcamBgPhotoService";

    private LinearLayout mOverlay = null;
    private SurfaceView mSurfaceView;
    private Camera mCamera;

    // Configuration stuff
    // Camera ID
    int cameraId;
    Integer photoWidth;
    Integer photoHeight;
    String uploadURL;
    Integer photoFrequency;
    Integer photoDaylightThreshold;
    private Timer captureTimer;

    public BackgroundPhotoService() {

    } // BackgroundPhotoService


    @Override
    public void onCreate()
    {
        Log.v(TAG, "BackgroundPhotoService -> onCreate()");

        // Preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(BackgroundPhotoService.this);
        String cameraIdString = preferences.getString("settings_camera",      null);
        String photoSizeString = preferences.getString("settings_photo_size", null);
        uploadURL = preferences.getString("settings_photo_url", "https://localhost");
        // if failed, it means settings is broken.
        assert(cameraIdString != null && photoSizeString != null);
        int xIndex = photoSizeString.indexOf("x");

        // if failed, it means settings is broken.
        assert(xIndex > 0);

        try {
            cameraId = Integer.parseInt(cameraIdString);

            photoWidth = Integer.parseInt(photoSizeString.substring(0, xIndex - 1));
            photoHeight = Integer.parseInt(photoSizeString.substring(xIndex + 2));

            photoFrequency = Integer.parseInt(preferences.getString("settings_photo_frequency", "15")); // evey 15 minutes otherwise
            photoDaylightThreshold = Integer.parseInt(preferences.getString("settings_photo_light_threshold", "50")); // evey 15 minutes otherwise

        } catch (NumberFormatException e) {
            Log.e(TAG, "Photo Capture settings broken");
            Toast.makeText(BackgroundPhotoService.this, "Photo Capture settings broken", Toast.LENGTH_SHORT).show();

            stopSelf();
            return;
        }


        // Now create the hidden surfaceview to keep the previewview happy  / camera happy
        SurfaceHolder.Callback callback = new SurfaceHolder.Callback()
        {
            public void surfaceCreated(SurfaceHolder holder) {
                Log.v(TAG, "BackgroundPhotoService -> surfaceCreated()");

                mCamera = Camera.open(cameraId);
                if (mCamera == null) {
                    Log.v(TAG, "Can't open camera" + cameraId);
                    
                    Toast.makeText(BackgroundPhotoService.this, getString(R.string.can_not_open_camera),
                            Toast.LENGTH_SHORT).show();
                    stopSelf();

                    return;
                }

                setCameraParams();
                
                try {
                    mCamera.setPreviewDisplay(holder);
                } catch (IOException e) {
                    Log.v(TAG, "Preview SurfaceHolder is not available");
                    
                    Toast.makeText(BackgroundPhotoService.this, "Preview SurfaceHolder is not available",
                            Toast.LENGTH_SHORT).show();
                    stopSelf();
                    
                    return;
                }

                // Lets Upload
                // Can't do this here as Camera operations are Async as well, and therefore
                // picture file may not exist at time of executing this AsyncCode Upload
                //UploadAsyncTask runner = new UploadAsyncTask();
                //runner.execute(filename, uploadURL);

              //  mCamera.stopPreview(); // dont' do this here!

                // WE HAVE TO CONFIGURE THIS STUFF HERE AS surfacecreation is asyncronus as well
                // Do stuff
                // Create a timer
                captureTimer = new Timer("Capture Timer");

                // Take Photo and upload
                //Log.i(TAG, "Performing one-off capture and upload to start with. Setting timer to execute every " + photoFrequency.toString() + " minutes.");
                mCamera.startPreview();
                //takePictureAndUpload(uploadURL, photoWidth, photoHeight);

                // Start the photoCapture timer task on the
                String currentMinuteStr  = new SimpleDateFormat("mm").format(new Date());
                Integer startDelay = 0;

                try {

                    Integer currentMinute = Integer.parseInt(currentMinuteStr); // evey 15 minutes otherwise

                    Log.i(TAG, "Current minute is: " + currentMinute.toString());
                    if ( currentMinute != 0) {
                        startDelay = (60-currentMinute)*1000*60;
                        Log.i(TAG, "Delaying starting until next hour, which will be in " + (startDelay/1000) + " seconds.");
                    }

                } catch (NumberFormatException e) { }


                captureTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.i(TAG, "Performing scheduled timer capture and upload every " + photoFrequency.toString() + " minutes.");
                        // Take Photo and upload

                   //     if (isBetween7AMand7PM()) { // force this on for now -> NO LONGER USED, based on brightness
                        //mCamera.startPreview();
                        try {
                            //Thread.sleep(3000);
                            takePictureAndUpload(uploadURL, photoWidth, photoHeight);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                 //       }
                    }
                }, startDelay, (photoFrequency*60*1000));


                // Periodic log output for testing
                captureTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.i(TAG, "FYI: Background photo capture task is still alive...");
                    }
                }, 0, 10000);


            } // end surfaceCreated

            public void surfaceChanged(SurfaceHolder holder, int format, int width,
                    int height) {
                Log.v(TAG, "surfaceChanged()");
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
                Log.v(TAG, "surfaceDestroyed()");
            }
        };
        
        createOverlay(); // create the hidden overlay
        SurfaceHolder surfaceHolder = mSurfaceView.getHolder();
        surfaceHolder.addCallback(callback);
        
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();

        
    }

    /*
    private boolean isBetween7AMand7PM()
    {
        try {
            String string1 = "07:00:00";
            Date time1 = new SimpleDateFormat("HH:mm:ss").parse(string1);
            Calendar calendar1 = Calendar.getInstance();
            calendar1.setTime(time1);
            calendar1.add(Calendar.DATE, 1);

            String string2 = "19:00:00";
            Date time2 = new SimpleDateFormat("HH:mm:ss").parse(string2);
            Calendar calendar2 = Calendar.getInstance();
            calendar2.setTime(time2);
            calendar2.add(Calendar.DATE, 1);

            String string3  = new SimpleDateFormat("HH:mm:ss").format(new Date());
            Date d = new SimpleDateFormat("HH:mm:ss").parse(string3);
            Calendar calendar3 = Calendar.getInstance();
            calendar3.setTime(d);
            calendar3.add(Calendar.DATE, 1);

            Log.i(TAG, "Current time is: " + string3.toString());

            Date x = calendar3.getTime();
            if (x.after(calendar1.getTime()) && x.before(calendar2.getTime())) {
                //checkes whether the current time is between 14:49:00 and 20:11:13.
                Log.i(TAG, "Time is between 7AM and 7PM");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.w(TAG, "Time is NOT between 7AM and 7PM");
        return false;
    }
    */


    private String getNextPhotoFileName()
    {
        //make a new file directory inside the "sdcard" folder
        File mediaStorageDir = new File(Environment.getExternalStorageDirectory(), "OldWebcam");
        Log.i("Save Photo", "Media storage directory is: " + mediaStorageDir.toString());


        //if this "JCGCamera folder does not exist
        if ( !mediaStorageDir.exists() ) {
            //if you cannot make this folder return
            if (!mediaStorageDir.mkdirs()) {
                Log.w("Save Photo", "Couldn't create directory: " + mediaStorageDir.toString());
            }
        }

        // Cleanup old files
        if (mediaStorageDir.isDirectory()) {
            for(File file: mediaStorageDir.listFiles())
                if (!file.isDirectory()) {
                    file.delete();
                    Log.e("Save Photo", "Cleaning up: " + file.toString());
                }
        }

        //take the current timeStamp
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
        Log.i("Save Photo", "" + mediaFile);
        return mediaFile.toString();

    } // get picture file name


    private void takePictureAndUpload(final String uploadURL, Integer photoWidth, Integer photoHeight) throws InterruptedException {

        final String filename = getNextPhotoFileName();

        // Camera picture taken callback
        final Camera.PictureCallback callback = new Camera.PictureCallback() {

            private String mPictureFileName = filename;
            private String mUploadURL       = uploadURL;

            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                Log.i(TAG, "Saving a bitmap to file");
                Bitmap picture = BitmapFactory.decodeByteArray(data, 0, data.length);

                // Write output
                try {
                    FileOutputStream out = new FileOutputStream(mPictureFileName);
                    //picture.compress(Bitmap.CompressFormat.JPEG, 90, out);
                    //picture.recycle();
                    out.write(data);
                    out.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                //File root = Environment.getExternalStorageDirectory();
                //Bitmap picture = BitmapFactory.decodeFile("/storage/sdcard0/DCIM/IMG_20200127_001724.jpg");

                // Process Picture, determine if day or night
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(picture, 640, 480, true);

                int redColors = 0;
                int greenColors = 0;
                int blueColors = 0;
                int pixelCount = 0;

                for (int y = 0; y < scaledBitmap.getHeight(); y++)
                {
                    for (int x = 0; x < scaledBitmap.getWidth(); x++)
                    {
                        int c = scaledBitmap.getPixel(x, y);
                        pixelCount++;
                        redColors += Color.red(c);
                        greenColors += Color.green(c);
                        blueColors += Color.blue(c);
                    }
                }

                // calculate average of bitmap r,g,b values
                int red = (redColors/pixelCount);
                int green = (greenColors/pixelCount);
                int blue = (blueColors/pixelCount);

                Log.i(TAG, "Red light avg: " + red);
                Log.i(TAG, "Green light avg: " + red);
                Log.i(TAG, "Blue light avg: " + red);

                Log.i(TAG, "Photo capture brightness threshold is: " + photoDaylightThreshold.toString());

                if (red < photoDaylightThreshold && green < photoDaylightThreshold && blue < photoDaylightThreshold )
                {
                    Log.w(TAG, "Daylight of capture was below threshold. Skipping photo save and/or upload.");
                }
                else {

                    // Lets Upload
                    Log.i(TAG, "Uploading Picture.");
                    UploadAsyncTask runner = new UploadAsyncTask();
                    runner.execute(mPictureFileName, mUploadURL);

                }

                //mCamera.startPreview();
                //Thread.sleep(1000);
                Log.i(TAG, "Stopping Preview");
                //mCamera.stopPreview(); // We STOP the preview until the next picture is required

            } // end onPictureTaken

        };

        // Capture autofocus lock (prior to picture taken) callback
        Camera.AutoFocusCallback autofocus_callback = new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                Log.i(TAG, "Autofocus success");
                try {
                    Thread.sleep(2000); // Give the camera two seconds to get exposure right
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mCamera.takePicture(null, null, callback);
            }
        };



        Log.i(TAG, "Taking Picture.");
        //Thread.sleep(1000);
        mCamera.autoFocus(autofocus_callback);


    }

    private void setCameraParams()
    {
        // Camera configuration, need to make sure we set it just before capture
        Parameters parameters = mCamera.getParameters();
        parameters.setPictureSize(photoWidth, photoHeight);
        parameters.setFocusMode(Parameters.FOCUS_MODE_AUTO); // Probably want this for a webcam looking out a window
        parameters.setFlashMode(Parameters.FLASH_MODE_OFF); // Don't really wany this
        mCamera.setParameters(parameters);

    }




    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        
        // We want BackgroundService.this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        // mNM.cancel(NOTIFICATION);
        
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
        }
        
        destroyOverlay();

        captureTimer.cancel();

    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void showNotification() {
        // In BackgroundService.this sample, we'll use the same text for the ticker and the expanded notification
        // CharSequence text = getText(R.string.service_started);
        CharSequence text = "Performing timed Photo Capture";

        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.ic_stat_webcam, text, System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects BackgroundService.this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        // Set the info for the views that show in the notification panel.
        // notification.setLatestEventInfo(this, getText(R.string.app_name), text, contentIntent);

        // Send the notification.
     //   startForeground( R.string.service_started, notification);
      //  showNotification();

    }
    
    public String getIpAddr() {
    	   WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
    	   WifiInfo wifiInfo = wifiManager.getConnectionInfo();
    	   int ip = wifiInfo.getIpAddress();

    	   String ipString = String.format(
    			   "%d.%d.%d.%d",
    			   (ip & 0xff),
    			   (ip >> 8 & 0xff),
    			   (ip >> 16 & 0xff),
    			   (ip >> 24 & 0xff));

        Log.v(TAG, ipString);

    	   return ipString;
    	}
    
    /**
     * Create a HIDDEN surface view overlay (for the camera's preview surface).
     * Not possible to take a photo without an active preview service per: https://developer.android.com/reference/android/hardware/Camera
     */
    private void createOverlay() {
        assert (mOverlay == null);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(4, 4,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,  // technically automatically set by FLAG_NOT_FOCUSABLE
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.BOTTOM;

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        mOverlay = (LinearLayout) inflater.inflate(R.layout.background, null);
        mSurfaceView = (SurfaceView) mOverlay.findViewById(R.id.backgroundSurfaceview);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.addView(mOverlay, params);
    }
    
    private void destroyOverlay() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.removeView(mOverlay);
    }
}
