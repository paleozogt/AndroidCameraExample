/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.paleozogt.camexample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.List;

// ----------------------------------------------------------------------

public class CameraExample extends Activity {
    private CameraPreview mPreview;
    Camera mCamera;
    int numberOfCameras;
    int cameraCurrentlyLocked;

    int mPreviewWidth, mPreviewHeight;
    boolean mRecordingHint;

    // The first rear facing camera
    int defaultCameraId;

    protected TextView _cameraSizeView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_example);

        Intent intent= getIntent();
        mPreviewWidth= intent.getIntExtra("previewWidth", -1);
        mPreviewHeight= intent.getIntExtra("previewHeight", -1);
        mRecordingHint= intent.getBooleanExtra("recordingHint", false);

        _cameraSizeView= (TextView)findViewById(R.id.camera_size);

        // Create a RelativeLayout container that will hold a SurfaceView,
        // and set it as a view on our activity
        mPreview = (CameraPreview)findViewById(R.id.camera_preview);

        // Find the total number of cameras available
        numberOfCameras = Camera.getNumberOfCameras();

        // Find the ID of the default camera
        CameraInfo cameraInfo = new CameraInfo();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                defaultCameraId = i;
            }
        }
    }

    protected void relaunch() {
        finish();
        startActivity(new Intent(this, this.getClass())
                .putExtra("previewWidth", mPreviewWidth)
                .putExtra("previewHeight", mPreviewHeight)
                .putExtra("recordingHint", mRecordingHint)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Open the default i.e. the first rear facing camera.
        mCamera = openCamera(defaultCameraId);
        cameraCurrentlyLocked = defaultCameraId;
        mPreview.setCamera(mCamera, mPreviewWidth, mPreviewHeight);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
            mPreview.setCamera(null);
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate our menu which can gather user input for switching camera
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.camera_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.switch_cam:
            // check for availability of multiple cameras
            if (numberOfCameras == 1) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(this.getString(R.string.camera_alert))
                       .setNeutralButton("Close", null);
                AlertDialog alert = builder.create();
                alert.show();
                return true;
            }

            // OK, we have multiple cameras.
            // Release this camera -> cameraCurrentlyLocked
            if (mCamera != null) {
                mCamera.stopPreview();
                mPreview.setCamera(null);
                mCamera.release();
                mCamera = null;
            }

            // Acquire the next camera and request Preview to reconfigure
            // parameters.
            mCamera = openCamera((cameraCurrentlyLocked + 1) % numberOfCameras);
            cameraCurrentlyLocked = (cameraCurrentlyLocked + 1)
                    % numberOfCameras;
            mPreview.switchCamera(mCamera);

            // Start the preview
            mCamera.startPreview();
            return true;
        case R.id.toggle_recording_hint:
            mRecordingHint= !mRecordingHint;
            relaunch();
            return true;
        case R.id.switch_resolution:
            AlertDialog.Builder builder= new AlertDialog.Builder(CameraExample.this);
            final List<Size> sizes= mPreview.getSupportedCameraSizes();
            final String[] labels= sizesToLabels(sizes);
            builder.setTitle(R.string.choose_camera_size);
            builder.setItems(labels, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Size size= sizes.get(which);
                    mPreviewWidth= size.width;
                    mPreviewHeight= size.height;
                    relaunch();
                }
            });
            builder.create().show();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    protected Camera openCamera(int camIdx) {
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        Camera.getCameraInfo(camIdx, cameraInfo);
        Camera camera= Camera.open(camIdx);

        int degrees= getRotationDegrees(this);
        int displayOrientation = (cameraInfo.orientation + degrees) % 360;
        displayOrientation = (360 - displayOrientation) % 360; // compensate the mirror

        camera.setDisplayOrientation(displayOrientation);

        Camera.Parameters params= camera.getParameters();
        params.setRecordingHint(mRecordingHint);
        camera.setParameters(params);

        // TODO: this is hackery
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                Size size= mPreview.getPreviewSize();
                if (size == null) {
                    new Handler().post(this);
                } else {
                    _cameraSizeView.setText(sizeToString(size));
                }
            }
        });

        return camera;
    }

    protected static int getRotationDegrees(Context ctx) {
        WindowManager wm= (WindowManager)ctx.getSystemService(Context.WINDOW_SERVICE);
        int rotation = wm.getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }
        return degrees;
    }


    String[] sizesToLabels(List<Size> sizes) {
        final String[] labels= new String[sizes.size()];
        for (int idx= 0; idx < sizes.size(); idx++) {
            Size size= sizes.get(idx);
            labels[idx]= sizeToString(size);
        }
        return labels;
    }

    protected static String sizeToString(Size size) {
        String ratio= String.format("%.2f", size.width / (float)size.height);
        return Integer.toString(size.width) + "x" + Integer.toString(size.height) + " (" + ratio + ") ";
    }
}

// ----------------------------------------------------------------------

