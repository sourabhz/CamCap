package com.capcam.www.capcam;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

@TargetApi(21)
public class MainActivity extends AppCompatActivity {
    private static final String TAG="FADSFAS";
    //9
    private static final int REQUEST_CAMERA_PERMISSION_RESULT=0; //FOR CALLBACK NOT REQUEST
    //12
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT=1;
    //16
    private static final int STATE_PREVIEW =0;
    private static final int STATE_WAIT_LOCK =1;
    //Another method for holding states
    private int mCaptureState = STATE_PREVIEW;
    //5
    private String mCameraId;
    //8
    private Size mPreviewSize;
    //13
    private int mTotalRotation;
    private Size mVideoSize;
    //16
    private Size mImageSize;
    private ImageReader mImageReader;
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
            ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                      //17
                      mBackgroundHandler.post(new ImageSaver(reader.acquireLatestImage())); //passing runnable
                }
            };

    //17(Awesome saving)
    private class ImageSaver implements Runnable{

        private final Image mImage;

        public ImageSaver(Image image){
            mImage=image;
        }

        @Override
        public void run() {

            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();  //on the first plane //hold all image data
            byte[] bytes = new byte[byteBuffer.remaining()];   //byBuffer for proper size of byteBuffer
            byteBuffer.get(bytes);  //bytes array hold all data

            //next is put those bytes into file in camerastorage
            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(mImageFileName);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {           //21 Best Place for Mediastore
                mImage.close();
                //21
                Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                //NOW i have to provide uri location to mediastore
                mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mImageFileName)));
                sendBroadcast(mediaStoreUpdateIntent);
                if(fileOutputStream != null){
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    } // we have to call it we do it in onImageAvailable();

    private MediaRecorder mMediaRecorder;
    //15
    private Chronometer mChronometer;
    //16******
    private CameraCaptureSession mPreviewCaptureSession;
    //For Capturing images in Preview Mode ...
    private CameraCaptureSession.CaptureCallback mPreviewCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {   //2 times or States called 1 in Preview and 2 actually tryin to capture by pressing the button(we're are interested in this )
               private void process(CaptureResult captureResult){
                   switch(mCaptureState){
                       case STATE_PREVIEW:
                           //DO NOTHING
                           break;
                       case STATE_WAIT_LOCK:
                           mCaptureState = STATE_PREVIEW;//autofocusstate
                           Integer afState= captureResult.get(CaptureResult.CONTROL_AF_STATE);
                           if(afState==CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){
                               Toast.makeText(getApplicationContext(),"AF Locked",Toast.LENGTH_SHORT).show();
                               startStillCaptureRequest();   //coz its already in the background thread
                           }
                           break;
                   }
               }
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                }
            };
    //18 taking photo while recording
    private CameraCaptureSession mRecordCaptureSession;
    //For Capturing images in Preview Mode ...
    private CameraCaptureSession.CaptureCallback mRecordCaptureCallback = new
            CameraCaptureSession.CaptureCallback() {   //2 times or States called 1 in Preview and 2 actually tryin to capture by pressing the button(we're are interested in this )
                private void process(CaptureResult captureResult){
                    switch(mCaptureState){
                        case STATE_PREVIEW:
                            //DO NOTHING
                            break;
                        case STATE_WAIT_LOCK:
                            mCaptureState = STATE_PREVIEW;//autofocusstate
                            Integer afState= captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if(afState==CaptureRequest.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED){
                                Toast.makeText(getApplicationContext(),"AF Locked",Toast.LENGTH_SHORT).show();
                                startStillCaptureRequest();   //coz its already in the background thread
                            }
                            break;
                    }
                }
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    process(result);
                }
            };
    //10
    private CaptureRequest.Builder mCaptureRequestBuilder;  //Starting Preview
    //11
    private ImageButton mRecordImageButton;
    //16
    private ImageButton mImageCaptureButton;
    private boolean mIsRecording=false;

    //20
    private boolean mIsTimeLapse =false;

    //17
    private File mImageFolder;
    private String mImageFileName;

    //12
    private File mVideoFolder;
    private String mVideoFileName;
    //7
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static { //Jo bhi orientation hoga use real life degree wale orientation m convert karenge
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    //8 setting_preview_size_dimensions
    public static class CompareSizeByArea implements Comparator<Size> {    //All resoultions are represented by Sizes

        @Override
        public int compare(Size lhs, Size rhs) {              //we are feeding in the minimum values to get resolution in the end
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() / (long)
                    rhs.getWidth() * rhs.getHeight());
        }
    }

    //3
    private TextureView mtextureView;       //That black screen
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            setupCamera(width, height);
            connectCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };///Needed to listening whether TextureView is available or not
    //4
    private CameraDevice mCameraDevice;

    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {   //Listener for Camera device
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            //14 (flushing thing when it asks permission for external storage everything flushes out so you have to recreate it)
            if(mIsRecording){
                try {
                    createFileName();
                   } catch (IOException e) {
                    e.printStackTrace();
                }
                startRecord();
                //15
                mChronometer.setBase(SystemClock.elapsedRealtime());
                mChronometer.setVisibility(View.VISIBLE);
                mChronometer.start();
            }else {
                startPreview();
            }
           // Toast.makeText(getApplicationContext(),
              //      "Camera connection made !",Toast.LENGTH_SHORT).show();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            mCameraDevice = null;
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            mCameraDevice = null;
        }
    };
    //6
    private HandlerThread mBackgroundHandlerThread;  // Things needed to create Background thread
    private Handler mBackgroundHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //12
        createVideoFolder();
        //17
        createImageFolder();
        //13
        mMediaRecorder= new MediaRecorder();

        mChronometer=(Chronometer)findViewById(R.id.chronometer);
        mtextureView = (TextureView) findViewById(R.id.textureView);     //Wiring the textureView
        mRecordImageButton =(ImageButton)findViewById(R.id.videoImageButton2);
        //16
        mImageCaptureButton=(ImageButton)findViewById(R.id.cameraImageButton);
        mImageCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lockFocus();
            }
        });
        mRecordImageButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mIsRecording || mIsTimeLapse){
                    mIsRecording=false;
                    mIsTimeLapse=false;
                    mRecordImageButton.setImageResource(R.mipmap.cam_online);
                    //14
                    mMediaRecorder.stop();
                    //21 Good Place to notify Media store that we've taken a video
                    Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    //NOW i have to provide uri location to media store
                    mediaStoreUpdateIntent.setData(Uri.fromFile(new File(mVideoFileName)));
                    sendBroadcast(mediaStoreUpdateIntent);
                    //15
                    mChronometer.stop();
                    mChronometer.setVisibility(View.INVISIBLE);
                    mMediaRecorder.reset();
                    startPreview();
                }
                else{
                    //20
                    mIsRecording=true;
                    mRecordImageButton.setImageResource(R.mipmap.camera_busy);
                    checkWriteStoragePermission();
                }
            }
        });
        //20
        mRecordImageButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mIsTimeLapse=true;
                mRecordImageButton.setImageResource(R.mipmap.btn_timelapse);
                checkWriteStoragePermission();   //whether we are recording or not
                return true;
            }
        });
    }


    //2
    public void onWindowFocusChanged(boolean hasFocus) {  //You need Full Screen Go For its a Sticky(You know that UI thing) one !!
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    //3
    protected void onResume() {
        super.onResume();
        //6
        startBackgroundThread();

        if (mtextureView.isAvailable()) {
            setupCamera(mtextureView.getWidth(), mtextureView.getHeight());
            connectCamera();
        } else {
            mtextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    //9

    @Override  //runtime permissions for marshmallow
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CAMERA_PERMISSION_RESULT){
            if(grantResults[0]!= PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getApplicationContext(),"CapCam will not run without Camera services",Toast.LENGTH_SHORT).show();
            }
            //19
            if(grantResults[1]!= PackageManager.PERMISSION_GRANTED){
                Toast.makeText(getApplicationContext(),"CapCam will not have on record",Toast.LENGTH_SHORT).show();
            }
        }
        //12
        if(requestCode==REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT){
            if(grantResults[0]==PackageManager.PERMISSION_GRANTED){
                mIsRecording=true;
                mRecordImageButton.setImageResource(R.mipmap.camera_busy);
                try {
                    createFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Toast.makeText(this,"PERMISSION successfully granted!",Toast.LENGTH_SHORT).show();
            }
            else{
                Toast.makeText(this, "App needs to Save Video to Run !",Toast.LENGTH_SHORT).show();
            }
        }
    }

    //4
    protected void onPause() {
        closeCamera();
        //6
        stopBackgroundThread();
        super.onPause();
    }

    //4
    public void closeCamera() {
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    //5
    //Camera Setup for generating Camera Id
    public void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                //8 map we'll take it from cameraCharacteristics it contains all the Camera resolution stuff
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                //7     getting width height for landscape only
                int rotationWidth = width;
                int rotationHeight = height;
                int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
                mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;
                if (swapRotation) {
                    rotationHeight = width;
                    rotationWidth = height;
                }
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotationWidth, rotationHeight); //8 that's the resolution
                //13
                mVideoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotationWidth, rotationHeight);
                //16
                mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotationWidth, rotationHeight);
                mImageReader = ImageReader.newInstance(mImageSize.getWidth(),mImageSize.getHeight(),ImageFormat.JPEG,1);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //9 connecting the camera
    private void connectCamera(){
        CameraManager cameraManager= (CameraManager) getSystemService(CAMERA_SERVICE);  //cameraManager initialising
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M)          //For newer versions
            {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);      //CameraManager
            }
            else{
                if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){    // You denied last time but we need this thing
                    Toast.makeText(this, "CapCam requires access to Camera",Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},REQUEST_CAMERA_PERMISSION_RESULT);
                //19 adding audio to video

            }
            }
        else {
            cameraManager.openCamera(mCameraId, mCameraDeviceStateCallback, mBackgroundHandler);}
        }catch(CameraAccessException e){
            e.printStackTrace();
        }
    }
    //14 Setup
    private void startRecord(){
        try {if(mIsRecording) {
            setupMediaRecorder();
        }
            else if(mIsTimeLapse){   //20 TimeLapse
            setupTimeLapse();
            }
            SurfaceTexture surfaceTexture = mtextureView.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface recordSurface = mMediaRecorder.getSurface();
            //Capture builder bana bencho
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);
            // Create Camera Capture Session
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface,mImageReader.getSurface()),//setting up a surface now 3 surface
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            //18
                            mRecordCaptureSession=session;
                            try {
                                session.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e){
                                e.printStackTrace();
                            }
                            }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {

                        }
                    },null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //10
    private void startPreview(){// texture view se surface lene ka
        SurfaceTexture surfaceTexture = mtextureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);  // capturerequestbuilder ki create request krne ka
            mCaptureRequestBuilder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,mImageReader.getSurface()),     //capture  session banane ka
                    new CameraCaptureSession.StateCallback() {                   //callback
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            try {
                                //16
                                mPreviewCaptureSession=session;
                                mPreviewCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),null,mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                       Toast.makeText(getApplicationContext(),"Unable to setup camera preview",Toast.LENGTH_SHORT).show();
                        }
                    },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //17
    private void startStillCaptureRequest(){
        try {//18
            if(mIsRecording){
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            }
            else {
                mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            }
            mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
            //For orientation problem    //Its a good Idea !! No....
            mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mTotalRotation);

            //camera session callback
            CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                    CameraCaptureSession.CaptureCallback() {
                        @Override
                        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                            super.onCaptureStarted(session, request, timestamp, frameNumber);
                            try {
                                createImageFileName();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    };
            if(mIsRecording){//18
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            }
            else {
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);
            }
            } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //6
    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("CamCapture");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }           //That's all you need to start a Background Thread

    //6
    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //7
    public static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return ((sensorOrientation + deviceOrientation + 360) % 360);
    }

    //8 Its gonna take array of sizes(choices) and width and height
    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<Size>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&          //3 things aspect ratio  and Height +Width provided
                    option.getWidth() >= width && option.getHeight() >= height) {  // for preview or our textureview
                bigEnough.add(option);
            }
        }
        if (bigEnough.size() > 0)
            return Collections.min(bigEnough, new CompareSizeByArea());
        else
            return choices[0];
    }

    //12
    private void createVideoFolder()
    {
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        mVideoFolder = new File(movieFile, "CapCam");
        if(!mVideoFolder.exists()){
            mVideoFolder.mkdirs();
        }
    }
    //12
    private File createFileName() throws IOException{
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "VIDEO_" + timestamp + "_";
        File videoFile= File.createTempFile(prepend,".mp4",mVideoFolder);
        mVideoFileName= videoFile.getAbsolutePath();
        return videoFile;
    }
    //17 Creating Image Folder
    private void createImageFolder()
    {
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(imageFile, "CapCam");
        if(!mImageFolder.exists()){
            mImageFolder.mkdirs();
        }
    }
    //17
    private File createImageFileName() throws IOException{
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "IMAGE_" + timestamp + "_";
        File imageFile= File.createTempFile(prepend,".jpg",mImageFolder);
        mImageFileName= imageFile.getAbsolutePath();
        return imageFile;
    }

    //12 {For MARSHMALLOW "-" Runtime}
    private void checkWriteStoragePermission(){
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED){

                try {
                    createFileName();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //14
                startRecord();
                mMediaRecorder.start();
                //15
                mChronometer.setBase(SystemClock.elapsedRealtime());
                mChronometer.setVisibility(View.VISIBLE);
                mChronometer.start();
            }
            else {
                if(shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    Toast.makeText(this, "app needs to be able to save videos",Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }

        }
        else
        {
            try {
                createFileName();
            } catch (IOException e) {
                e.printStackTrace();
            }
            //14
            startRecord();
            mMediaRecorder.start();
            //15
            mChronometer.setBase(SystemClock.elapsedRealtime());
            mChronometer.setVisibility(View.VISIBLE);
            mChronometer.start();
        }
    }
    //13 (Setting up MediaRecorder) //2 thing VideoSize and totalrotation before this
    private void setupMediaRecorder() throws IOException{
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        //19
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setOutputFile(mVideoFileName);
        mMediaRecorder.setVideoEncodingBitRate(1000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(),mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        //19
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }
    //20 TimeLapse
    private void setupTimeLapse() throws IOException{
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH));
        mMediaRecorder.setOutputFile(mVideoFileName);
        //
        mMediaRecorder.setCaptureRate(2);    //walking
        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }
//16
    private void lockFocus(){
        mCaptureState=STATE_WAIT_LOCK;
        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,CaptureRequest.CONTROL_AF_TRIGGER_START); //Auto FOCUS to lock
        try {
            if(mIsRecording){//18
                mRecordCaptureSession.capture(mCaptureRequestBuilder.build(), mRecordCaptureCallback, mBackgroundHandler);
            }
                else{
                mPreviewCaptureSession.capture(mCaptureRequestBuilder.build(), mPreviewCaptureCallback, mBackgroundHandler);
            }
            } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


}
