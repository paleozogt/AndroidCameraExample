package org.paleozogt.camexample;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.util.Log;

public class ImageSaver {
    protected static final String TAG = ImageSaver.class.getSimpleName();

    protected int _frameId= 0;
    protected String _fileNameFormat;
    protected int _orientation;

    public ImageSaver(String fileNameFormat, int orientation) {
        _fileNameFormat= fileNameFormat;
        _orientation= orientation;
    }

    protected void saveImage(final byte[] data, final Camera camera) {
        final String fileName= String.format(_fileNameFormat, _frameId++);
        Log.d(TAG, "saving " + fileName);

        new AsyncTask<Void, Void, Exception>() {
            @Override
            protected Exception doInBackground(Void... params) {
                OutputStream imageStream= null;
                try {
                    imageStream= new FileOutputStream(fileName);

                    Bitmap image= bitmapFromImage(data, camera);
                    Bitmap correctedImage= correctForRotation(image);
                    correctedImage.compress(Bitmap.CompressFormat.JPEG, 100, imageStream);
                } catch (IOException e) {
                    Log.e(TAG, e.toString());
                    return e;
                } finally {
                    try {
                        imageStream.close();
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                        return e;
                    }
                }
                return null;
            }

            @Override
            protected void onPostExecute(Exception result) {
                Log.d(TAG, "processed " + fileName);
            }

        }.execute();
    }

    Bitmap correctForRotation(Bitmap bmp) {
        Matrix rotationMatrix= new Matrix();
        rotationMatrix.postRotate(_orientation);
        return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), rotationMatrix, true);
    }

    protected ByteArrayOutputStream _jpgStream= new ByteArrayOutputStream();

    Bitmap bitmapFromImage(byte[] data, Camera camera) {
        YuvImage yuv= yuvFromImage(data, camera);
        Rect imageRect= new Rect(0, 0, yuv.getWidth(), yuv.getHeight());

        _jpgStream.reset();
        yuv.compressToJpeg(imageRect, 100, _jpgStream);

        byte[] jpgBytes= _jpgStream.toByteArray();
        return BitmapFactory.decodeByteArray(jpgBytes, 0, jpgBytes.length);
    }

    YuvImage yuvFromImage(byte[] data, Camera camera) {
        Camera.Parameters params= camera.getParameters();
        Size imageSize= params.getPreviewSize();
        int format= params.getPreviewFormat();
        return new YuvImage(data, format, imageSize.width, imageSize.height, null);
    }
}
