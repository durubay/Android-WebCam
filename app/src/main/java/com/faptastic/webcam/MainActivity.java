package com.faptastic.webcam;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {
    public final String TAG = "Webcam";

    // JPEG Video Service
    private boolean mIsmJPEGServiceRunning = false;
    private Button mBackgroundmJPEGButton;
    private Button mForegroundmJPEGButton;

    // Period photo upload
    private boolean mIsPhotoServiceRunning = false;
    private Button mBackgroundPhotoButton;
    
    SharedPreferences mSharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mBackgroundmJPEGButton = (Button) findViewById(R.id.backgroundButton);
        mForegroundmJPEGButton = (Button) findViewById(R.id.foregroundButton);
        mBackgroundPhotoButton = (Button) findViewById(R.id.backgroundPhotoButton);
        
        if (!initialize()) {
            Toast.makeText(this, "Can not initialize parameters", Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    public void onResume() {
        super.onResume();

        mIsmJPEGServiceRunning = ismJPEGServiceRunning();
        updateBackgroundmJPEGServiceButton(mIsmJPEGServiceRunning);

        mIsPhotoServiceRunning = isPhotoServiceRunning();
        updateBackgroundPhotoServiceButton(mIsPhotoServiceRunning);

    }
    
    @Override
    public void onStop() {
        super.onStop();
        
        if (mIsmJPEGServiceRunning) {
            finish();
        }
    }
    
    public void onButtonClick(View view) {
        switch (view.getId()) {
        case R.id.settingsButton:
            startActivity(new Intent(this , SettingsActivity.class));
            break;
        case R.id.foregroundButton:
            if (mIsPhotoServiceRunning)
                break;

            if (mIsmJPEGServiceRunning) {
                stopService(new Intent(this, BackgroundmJPEGService.class));
                mIsmJPEGServiceRunning = false;
                updateBackgroundmJPEGServiceButton(false);
            }
            startActivity(new Intent(this , ForegroundmJPEGActivity.class));
            break;
        case R.id.backgroundButton:
            if (mIsPhotoServiceRunning)
                break;

            if (!mIsmJPEGServiceRunning) {
                //doBindService();
                startService(new Intent(this, BackgroundmJPEGService.class));
                mIsmJPEGServiceRunning = true;
            } else {
                //doUnbindService();
                stopService(new Intent(this, BackgroundmJPEGService.class));
                mIsmJPEGServiceRunning = false;
            }
            updateBackgroundmJPEGServiceButton(mIsmJPEGServiceRunning);
            break;

            case R.id.backgroundPhotoButton:
                if (mIsmJPEGServiceRunning)
                    break;

                Log.i(TAG, "Background Photo Button Clicked");
                if (!mIsPhotoServiceRunning) {
                    //doBindService();
                    startService(new Intent(this, BackgroundPhotoService.class));
                    mIsPhotoServiceRunning = true;
                } else {
                    //doUnbindService();
                    stopService(new Intent(this, BackgroundPhotoService.class));
                    mIsPhotoServiceRunning = false;
                }
                updateBackgroundPhotoServiceButton(mIsPhotoServiceRunning);
                break;

        }
    }
    
    private boolean ismJPEGServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ((BackgroundmJPEGService.class.getName()).equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPhotoServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if ((BackgroundPhotoService.class.getName()).equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    private void updateBackgroundmJPEGServiceButton(boolean state) {
        if (state) {
            mBackgroundmJPEGButton.setText(R.string.stop_running);
        } else {
            mBackgroundmJPEGButton.setText(R.string.run_background);
        }
    }

    private void updateBackgroundPhotoServiceButton(boolean state) {
        if (state) {
            mBackgroundPhotoButton.setText(R.string.stop_running);
        } else {
            mBackgroundPhotoButton.setText(R.string.run_background_timed_capture);
        }
    }
    
    private boolean initialize() {
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        
       boolean firstRun = !mSharedPreferences.contains("settings_photo_url");
       if (firstRun) {
           Log.v(TAG, "First run");
           
           SharedPreferences.Editor editor = mSharedPreferences.edit();
           
           int cameraNumber = Camera.getNumberOfCameras();
           Log.v(TAG, "Camera number: " + cameraNumber);
           
           /*
            * Get camera name set 
            */
           TreeSet<String> cameraNameSet = new TreeSet<String>();
           if (cameraNumber == 1) {
               cameraNameSet.add("back");
           } else if (cameraNumber == 2) {
               cameraNameSet.add("back");
               cameraNameSet.add("front");
           } else if (cameraNumber > 2) {           // rarely happen
               for (int id = 0; id < cameraNumber; id++) {
                   cameraNameSet.add(String.valueOf(id));
               }
           } else {                                 // no camera available
               Log.v(TAG, "No camera available");
               Toast.makeText(this, "No camera available", Toast.LENGTH_SHORT).show();
               
               return false;
           }

           /* 
            * Get camera id set
            */
           String[] cameraIds = new String[cameraNumber];
           TreeSet<String> cameraIdSet = new TreeSet<String>();
           for (int id = 0; id < cameraNumber; id++) {
               cameraIdSet.add(String.valueOf(id));
           }
           
           /*
            * Save camera name set and id set
            */
           editor.putStringSet("camera_name_set", cameraNameSet);
           editor.putStringSet("camera_id_set", cameraIdSet);
           
           /*
            * Get and save camera parameters
            */
           for (int id = 0; id < cameraNumber; id++) {
               Camera camera = Camera.open(id);
               if (camera == null) {
                   String msg = "Camera " + id + " is not available";
                   Log.v(TAG, msg);
                   Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                   
                   return false;
               }
               
               Parameters parameters = camera.getParameters();
               
               /*
                * Get and save preview / mJPEG stream resolution sizes
                */
               List<Size> sizes = parameters.getSupportedPreviewSizes();
               
               TreeSet<String> sizeSet = new TreeSet<String>(new Comparator<String>() {
                   @Override
                   public int compare(String s1, String s2) {
                       int spaceIndex1 = s1.indexOf(" ");
                       int spaceIndex2 = s2.indexOf(" ");
                       int width1 = Integer.parseInt(s1.substring(0, spaceIndex1));
                       int width2 = Integer.parseInt(s2.substring(0, spaceIndex2));
                       
                       return width2 - width1;
                   }
               });
               for (Size size : sizes) {
                   sizeSet.add(size.width + " x " + size.height);
               }
               editor.putStringSet("preview_sizes_" + id, sizeSet);

               Log.v(TAG, "Stream Resolutions: ");
               Log.v(TAG, sizeSet.toString());

               /*
                * Set default preview size, use camera 0
                */
               if (id == 0) {
                   Log.v(TAG, "Set default preview size");

                   Size defaultSize = parameters.getPreviewSize();
                   editor.putString("settings_size", defaultSize.width + " x " + defaultSize.height);
               }


               /*
                * Get and save CAPTURE sizes
                */
               sizes = parameters.getSupportedPictureSizes();
               sizeSet = new TreeSet<String>(new Comparator<String>() {
                   @Override
                   public int compare(String s1, String s2) {
                       int spaceIndex1 = s1.indexOf(" ");
                       int spaceIndex2 = s2.indexOf(" ");
                       int width1 = Integer.parseInt(s1.substring(0, spaceIndex1));
                       int width2 = Integer.parseInt(s2.substring(0, spaceIndex2));

                       return width2 - width1;
                   }
               });
               for (Size size : sizes) {
                   sizeSet.add(size.width + " x " + size.height);
               }
               Log.v(TAG, "Photo Resolutions: ");
               editor.putStringSet("photo_sizes_" + id, sizeSet);

               Log.v(TAG, sizeSet.toString());

               /*
                * Set default preview size, use camera 0
                */
               if (id == 0) {
                   Log.v(TAG, "Set default picture size");

                   Size defaultSize = parameters.getPictureSize();
                   editor.putString("settings_photo_size", defaultSize.width + " x " + defaultSize.height);
               }

               /*
                * Get and save preview FPS range
                */
               List<int[]> ranges = parameters.getSupportedPreviewFpsRange();
               TreeSet<String> rangeSet = new TreeSet<String>();
               for (int[] range : ranges) {
                   rangeSet.add(range[0] + " ~ " + range[1]);
               }
               editor.putStringSet("preview_ranges_" + id, rangeSet);
               
               if (id == 0) {
                   Log.v(TAG, "Set default fps range");
                   
                   int[] defaultRange = new int[2];
                   parameters.getPreviewFpsRange(defaultRange);
                   editor.putString("settings_range", defaultRange[0] + " ~ " + defaultRange[1]);
               }
               
               camera.release();
               
           }
           
           editor.putString("settings_camera", "0");
           editor.commit();
       }
       
       return true;
    }
}
