package com.tks.rapidfirecamera;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.RotateAnimation;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainFragment extends Fragment {
    private FragmentActivity mActivity;
    private MainViewModel mViewModel;
    private final Semaphore mCameraOpenCloseSemaphore = new Semaphore(1);
    static ContentResolver mResolver;
    static final SimpleDateFormat mDf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US);
    private ImageReader mImageReader4TakePicture;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mRequestforPreview;

    /* ??????????????????=f??????, ??????????????????, ???????????????-???, ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????? */
    /* ??????????????????=f??????, ??????????????????, ???????????????-???, ???????????????????????????????????? */

    public static MainFragment newInstance() {
        return new MainFragment();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    /*****************
     * onViewCreated()
     *****************/
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        /* ?????????????????? */
        mActivity    = getActivity();
        if(mActivity == null) throw new RuntimeException("Error occurred!! illigal state in this app. activity is null!!");
        mTextureView = view.findViewById(R.id.tvw_preview);
        mViewModel   = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        mResolver    = mActivity.getApplicationContext().getContentResolver();
        mDf.setTimeZone(TimeZone.getDefault());

        /* ?????????????????????ID?????????????????????Camera?????????????????????????????????????????????????????? */
        CameraManager manager = (CameraManager)mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for(String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                /* ????????????????????????????????? */
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if(facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;

                /* streamConfig map?????????????????????????????? */
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if(map == null)
                    continue;

                /* ????????????????????????????????? */
                Boolean flashavailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                /* Camera??????????????????????????? */
                int sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                mViewModel.setCameraId(cameraId);
                mViewModel.setSupportedCameraSizes(map.getOutputSizes(SurfaceTexture.class));
                mViewModel.setFlashSupported((flashavailable == null) ? false : flashavailable);
                mViewModel.setSensorOrientation(sensorOrientation);
                break;
            }
        }
        catch(CameraAccessException e) {
            Log.d("aaaaa", e.toString());
            throw new RuntimeException("Error!! Camera is illigal state!!");
        }

        /* ????????????????????????????????? */
        mViewModel.setOnChageRotationListner().observe(getViewLifecycleOwner(), rotfromto -> {
            /* Config???????????? */
            RotateAnimation rotanim_s = new RotateAnimation(rotfromto.first, rotfromto.second, view.findViewById(R.id.btn_setting).getPivotX(), view.findViewById(R.id.btn_setting).getPivotY());
            rotanim_s.setDuration(250);
            rotanim_s.setFillAfter(true);
            view.findViewById(R.id.btn_setting).startAnimation(rotanim_s);
            view.findViewById(R.id.btn_settingaaa).startAnimation(rotanim_s);
            /* ???????????????????????? */
            RotateAnimation rotanim_l = new RotateAnimation(rotfromto.first, rotfromto.second, view.findViewById(R.id.btn_shutter).getPivotX(), view.findViewById(R.id.btn_shutter).getPivotY());
            rotanim_l.setDuration(250);
            rotanim_l.setFillAfter(true);
            view.findViewById(R.id.btn_shutter).startAnimation(rotanim_l);
        });

        /* ??????????????????????????? */
        view.findViewById(R.id.ll_config).setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @NonNull @Override
            public WindowInsets onApplyWindowInsets(@NonNull View v, @NonNull WindowInsets insets) {
                v.setTranslationY(insets.getInsets(WindowInsets.Type.statusBars()).top + 1);
                return insets;
            }
        });

        /* ??????????????????????????????????????? */
        view.findViewById(R.id.btn_setting).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mActivity.getSupportFragmentManager().beginTransaction().replace(R.id.container, ConfigFragment.newInstance()).commit();
            }
        });

        /* Shutter????????????????????????????????? */
        view.findViewById(R.id.btn_shutter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    /*******************************************************************************************************
     * Handler?????????,TextureView????????????????????????
     * onResume() -> [onSurfaceTextureAvailable()] -> openCamera() CameraManager::openCamera() -> onOpened()
     *******************************************************************************************************/
    private TextureView mTextureView;
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private CameraDevice mCameraDevice;

    /* onResume() -> [onSurfaceTextureAvailable()] -> openCamera() CameraManager::openCamera() -> onOpened() */
    /* ?????????                                                                                                  */
    @Override
    public void onResume() {
        super.onResume();

        /* start Handler */
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        if(mTextureView.isAvailable()) {
            openCamera(mViewModel.getCameraId());
        }
        else {
            mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                    openCamera(mViewModel.getCameraId());
                }
                @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}
                @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
                @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) { return false; }
            });
        }
    }

    /* onResume() -> [onSurfaceTextureAvailable()] -> openCamera() -> CameraManager::openCamera() -> onOpened() */
    /*                                                ??? ??????                                                   */
    private void openCamera(String cameraid) {
        /* ??????(?????????????????????)?????? */
        Size pictureSize = mViewModel.getTakePictureSize();
        mImageReader4TakePicture = ImageReader.newInstance(pictureSize.getWidth(), pictureSize.getHeight(), ImageFormat.JPEG, /*maxImages*/10);
        mImageReader4TakePicture.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                /* ImageReader????????????????????????????????? */
                Image image = reader.acquireNextImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                image.close();
                /* ??????????????????????????????????????? */
                mBackgroundHandler.post(new ImageSaver(mViewModel.getSaveFullPath(), mViewModel.getSaveRelativePath(), bytes));
            }
        }, mBackgroundHandler);

        /* Open Camera */
        CameraManager manager = (CameraManager)mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            if( !mCameraOpenCloseSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS))
                throw new RuntimeException("Time out waiting to lock camera opening.");

            /* ?????????????????? -> ?????????????????????????????????!!(CameraManager::openCamera()???????????????????????????????????????) */
            if(ActivityCompat.checkSelfPermission(mActivity, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                new Throwable().printStackTrace();
                ErrorDialog.newInstance(getString(R.string.request_permission)).show(getChildFragmentManager(), "Error!!");
            }
            manager.openCamera(cameraid, mDeviceStateCallback, mBackgroundHandler);
        }
        catch(InterruptedException | CameraAccessException e) {
            /* ???????????????????????????????????????????????? */
            throw new RuntimeException(e);
        }
    }

    /***************************************************************************************************************************************************
     * Preview?????????????????????
     * CameraDevice.StateCallback::onOpened() -> createCameraPreviewSession() -> StateCallback::onConfigured() -> CaptureCallback::onCaptureProgressed()
     ***************************************************************************************************************************************************/
    private final CameraDevice.StateCallback mDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            /* This method is called when the camera is opened.  We start camera preview here. */
            mCameraOpenCloseSemaphore.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseSemaphore.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseSemaphore.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    /* CameraDevice.StateCallback::onOpened() -> createCameraPreviewSession() -> StateCallback::onConfigured() -> CaptureCallback::onCaptureProgressed() */
    /*                                            ?????????                                                                                                   */
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private void createCameraPreviewSession() {
        /***********************************
         * TextureView?????????????????????????????? */
        boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        Size textureViewSize = (isLandscape) ? new Size(mTextureView.getMeasuredWidth(), mTextureView.getMeasuredHeight()) : new Size(mTextureView.getMeasuredHeight(), mTextureView.getMeasuredWidth());
        /* ??????????????????????????????Supported?????????s????????????Preview????????????????????? */
        Size previewSize = getSuitablePreviewSize(mViewModel.getSupportedCameraSizes(), new Size(textureViewSize.getWidth(), textureViewSize.getHeight()));
        /* TextureView???Preview?????????????????????????????????????????? */
        float textureViewAdpect= ((float)textureViewSize.getWidth())/textureViewSize.getHeight();
        float previewAdpect   = ((float)previewSize.getWidth())/previewSize.getHeight();
        Log.d("aaaaa", String.format(Locale.JAPAN, "aaaaa(220)onResume() TextureView??????????????????????????????(%s)[%f] Preview??????????????????????????????(%s)[%f]", textureViewSize, textureViewAdpect, previewSize, previewAdpect));
        /* TextureView???Preview????????????????????????????????????????????? */
        Matrix matrix = new Matrix();
        matrix.setScale(textureViewAdpect/previewAdpect, 1);  /* sony???OK */
        mTextureView.setTransform(matrix);

        /**************************
         * ????????????Preview??????????????? */
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        /* ??????????????????????????????????????????????????????Preview???????????????????????? */
        texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

        /* SurfaceTexture -> Surface */
        Surface surfaceFromTextureView = new Surface(texture);

        /* We set up a CaptureRequest.Builder with the output Surface. */
        try {
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surfaceFromTextureView);

            /* ??????????????????????????? */
            SessionConfiguration sessionConfiguration = new SessionConfiguration(
                                            SessionConfiguration.SESSION_REGULAR,
                                            Arrays.asList(new OutputConfiguration(surfaceFromTextureView), new OutputConfiguration(mImageReader4TakePicture.getSurface())),
                                            Runnable::run,
                                            mCaptureSessionStateCallback);

            /* ??????????????????????????? CameraCaptureSession????????? */
            mCameraDevice.createCaptureSession(sessionConfiguration);
        }
        catch(CameraAccessException e) {
            /* ???????????????????????????????????????????????? */
            throw new RuntimeException(e);
        }
    }

    /*********************************************************************************
     * ??????Preview???????????????
     * ?????????baseSize?????????????????????????????????????????????Camera????????????????????????????????????????????????
     **********************************************************************************/
    private Size getSuitablePreviewSize(Size[] supportedCameraSizes, Size baseSize) {
        /* base????????????????????? */
        double baseArea  = ((double)baseSize.getWidth())*baseSize.getHeight();
        double baseAspect= ((double)baseSize.getWidth())/baseSize.getHeight();
        for(Size s : supportedCameraSizes)
            Log.d("aaaaa", String.format("aaaaa getSuitablePreviewSize() base=%s SupportedCameraSize=%s", baseSize, s));

        /* ??????????????????????????????????????? */
        Size maxPreviewSzie= Arrays.stream(supportedCameraSizes).max((o1, o2) -> {return o1.getWidth()*o1.getHeight() - o2.getWidth()*o2.getHeight();}).get();
        Size maxAspectSize = Arrays.stream(supportedCameraSizes).max((o1, o2) -> {return Double.compare(((double)o1.getWidth())/o1.getHeight(), ((double)o2.getWidth())/o2.getHeight());}).get();
        double maxAspect = ((double)maxAspectSize.getWidth()) / maxAspectSize.getHeight();

        Size suitableCameraPreviewSize = Arrays.stream(supportedCameraSizes).min((o1, o2) -> {
            /* ??????????????? */
            double baseAreaNorm= baseArea / (((double)maxPreviewSzie.getWidth())*maxPreviewSzie.getHeight());
            double o1AreaNorm  = (((double)o1.getWidth())*o1.getHeight()) / (((double)maxPreviewSzie.getWidth())*maxPreviewSzie.getHeight());
            double o2AreaNorm  = (((double)o2.getWidth())*o2.getHeight()) / (((double)maxPreviewSzie.getWidth())*maxPreviewSzie.getHeight());

            /* ??????????????????????????? */
            double baseAspectNorm= baseAspect / maxAspect;
            double o1AspectNorm  = (((double)o1.getWidth())/o1.getHeight()) / maxAspect;
            double o2AspectNorm  = (((double)o2.getWidth())/o2.getHeight()) / maxAspect;

            /* o1 */
            double o1AreaDiff     = o1AreaNorm   - baseAreaNorm;                        /* ???????????? */
            double o1AspectDiff   = o1AspectNorm - baseAspectNorm;                      /* ???????????????????????? */
            double o1MoreLargeDiff= (o1.getWidth()*o1.getHeight()== baseArea) ? 0.0 :
                                    (o1.getWidth()*o1.getHeight() < baseArea) ? 0.2 : 0.1;/* ???????????????????????????????????????????????????(?????????????????????) */
            /* o2 */
            double o2AreaDiff     = o2AreaNorm   - baseAreaNorm;                        /* ???????????? */
            double o2AspectDiff   = o2AspectNorm - baseAspectNorm;                      /* ???????????????????????? */
            double o2MoreLargeDiff= (o2.getWidth()*o2.getHeight()== baseArea) ? 0.0 :
                                    (o2.getWidth()*o2.getHeight() < baseArea) ? 0.2 : 0.1;/* ???????????????????????????????????????????????????(?????????????????????) */
            /* ?????????????????? */
            double o1Feature = Math.abs(o1AreaDiff) + Math.abs(o1AspectDiff) + Math.abs(o1MoreLargeDiff);
            double o2Feature = Math.abs(o2AreaDiff) + Math.abs(o2AspectDiff) + Math.abs(o2MoreLargeDiff);

            return Double.compare(o1Feature, o2Feature);
        }).get();

        Log.d("aaaaa",  String.format("aaaaa ????????????????????????233 (%d x %d[%f])", suitableCameraPreviewSize.getWidth(), suitableCameraPreviewSize.getHeight(), ((double)suitableCameraPreviewSize.getWidth())/suitableCameraPreviewSize.getHeight()) );
        return suitableCameraPreviewSize;
    }

    /* CameraDevice.StateCallback::onOpened() -> createCameraPreviewSession() -> StateCallback::onConfigured() -> CaptureCallback::onCaptureProgressed() */
    /*                                                                           ?????????                                                                                                              */
    CameraCaptureSession.StateCallback mCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            /* The camera is already closed */
            if (mCameraDevice ==null) return;

            /* When the session is ready, we start displaying the preview. */
            mCaptureSession = session;
            try {
                /* Auto focus should be continuous for camera preview. */
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                /* Flash is automatically enabled when necessary. */
                if(mViewModel.getFlashSupported())
                    mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                /* Finally, we start displaying the camera preview. */
                mRequestforPreview = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mRequestforPreview, mCaptureCallback, mBackgroundHandler);
            }
            catch (CameraAccessException e) {
                /* ???????????????????????????????????????????????? */
                throw new RuntimeException(e);
            }
        }

        @Override public void onConfigureFailed(@NonNull CameraCaptureSession session) { /* ???????????????????????????????????????????????? */ throw new RuntimeException(session.toString()); }
    };

    /* CameraDevice.StateCallback::onOpened() -> createCameraPreviewSession() -> StateCallback::onConfigured() -> CaptureCallback::onCaptureProgressed() */
    /*                                                                                                            ?????????                                  */
    private int mState = STATE_PREVIEW;
    private static final int STATE_PREVIEW                = 0;
    private static final int STATE_WAITING_LOCK           = 1;
    private static final int STATE_WAITING_PRECAPTURE     = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN          = 4;
    private final CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }

        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: /* ???????????????????????? ???????????????????????? */
                    break;
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    /* ??????????????????????????????????????????????????? */
                    if(afState == null) {
                        captureStillPicture();
                    }
                    /* ???????????????????????????????????????(?????????????????????????????????) */
                    else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        /* ?????????AE???????????????????????????????????????????????????null(???????????????????????????null??????????????????)???????????????OK.?????? */
                        if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        }
                        /* AE??????????????????AE???????????? */
                        else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    /* AE???????????????????????????????????????????????????????????????null(???????????????????????????null??????????????????)???????????????OK. ??????onCaptureProgressed()????????? */
                    if(aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                // CONTROL_AE_STATE can be null on some devices
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    /* AE????????????????????????????????????null(???????????????????????????null??????????????????)????????? */
                    if(aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }
    };

    /********************************************************************************************************************************************************************************************
     * ??????(???????????????ON)???????????????
     *             takePicture()                    CaptureCallback::onCaptureProgressed()               CaptureCallback::onCaptureProgressed()       CaptureCallback::onCaptureProgressed()
     *               -> lockFocus()                   -> runPrecaptureSequence()
     * STATE_PREVIEW(0) --------> STATE_WAITING_LOCK(1) ------------------------> STATE_WAITING_PRECAPTURE(2) -----------> STATE_WAITING_NON_PRECAPTURE(3) --------------> STATE_PICTURE_TAKEN(4)
     *              ???????????????????????????AotoFocus??????              AF????????????             AE??????                       AE??????????????????????????????????????????????????????????????????????????????AE????????????2       ??????
     ********************************************************************************************************************************************************************************************/
    private void takePicture() {
        lockFocus();
    }

    private void lockFocus() {
        try {
            /* This is how to tell the camera to lock focus. */
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            /* Tell #mCaptureCallback to wait for the lock. */
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * ???????????????????????????????????????Capture????????????????????????
     * AutoFocus????????????????????????????????????????????????????????????
     */
    private void runPrecaptureSequence() {
        try{
            /* This is how to tell the camera to trigger. */
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            /* Tell #mCaptureCallback to wait for the pre-capture sequence to be set. */
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????????????????
     * AF???AE?????????????????????????????????????????????(AF/AE????????????????????????????????????OK)
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {lockFocus()}.
     */
    private void captureStillPicture() {
        try{
            /* This is the CaptureRequest.Builder that we use to take a picture. */
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader4TakePicture.getSurface());

            /* Use the same AE and AF modes as the preview. */
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if(mViewModel.getFlashSupported())
                captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            /* ?????????????????????????????????  */
            int rotation          = mViewModel.getOrientation();
            int sensorOrientation = mViewModel.getSensorOrientation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, (MainViewModel.ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360);

            CameraCaptureSession.CaptureCallback lCaptureCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), lCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * ?????????????????????(Unlock the focus)
     * ????????????????????????????????????????????????????????????????????????
     * This method should be called when still image capture sequence is finished.
     */
    private void unlockFocus() {
        try {
            /* Reset the auto-focus trigger */
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            if(mViewModel.getFlashSupported())
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            /* After this, the camera will go back to the normal state of preview. */
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mRequestforPreview, mCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*******************************
     * Handler??????, ??????????????????????????????
     * onPause() -> closeCamera()
     *******************************/
    @Override
    public void onPause() {
        super.onPause();

        /* stop Camera */
        closeCamera();

        /* stop Handler */
        mBackgroundThread.quitSafely();
        try {
            mBackgroundHandler.getLooper().getThread().join();
            mBackgroundHandler = null;
        }
        catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*****************
     * Close Camera */
    private void closeCamera() {
        try {
            mCameraOpenCloseSemaphore.acquire();
            if(mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if(mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if(mImageReader4TakePicture != null) {
                mImageReader4TakePicture.close();
                mImageReader4TakePicture = null;
            }
        }
        catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        }
        finally {
            mCameraOpenCloseSemaphore.release();
        }
    }

    /**
     * ImageSaver class
     * Saves a JPEG {@link Image} into the specified {@link ContentResolver}.
     */
    private static class ImageSaver implements Runnable {
        private final byte[] mBytes;
        private final String mDirFullPath;
        private final String mDirRelativePath;
        ImageSaver(String savefullDir, String saveRelativeDir, byte[] bytes) {
            mDirFullPath    = savefullDir;
            mDirRelativePath= saveRelativeDir;
            mBytes          = bytes;
        }

        @Override
        public void run() {
            String filename = String.format("%s.jpg", mDf.format(new Date()));

            ContentValues values = new ContentValues();
            /* ??????????????? */
            values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
            /* MIME????????? */
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            /* ???????????? */
            values.put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000);
            /* ??????????????????????????????????????????????????????????????? */
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            /* ?????????????????? */
            values.put(MediaStore.Images.Media.RELATIVE_PATH, mDirRelativePath);
            /* ContentResolver????????? */
            Uri dstUri = mResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);


            /* ContentResolver????????????????????????????????? */
            OutputStream outstream = null;
            try {
                outstream = mResolver.openOutputStream(dstUri);
                outstream.write(mBytes);
            }
            catch(IOException e) {
                e.printStackTrace();
            }
            finally {
            /* ???????????? */
                values.clear();
                /*???????????????????????????????????? */
                values.put(MediaStore.Images.Media.IS_PENDING, 0);
                mResolver.update(dstUri, values, null, null);
                try {
                    if(outstream != null)
                        outstream.close();
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**************************************
     * Utils
     * ************************************/
    public static class ErrorDialog extends DialogFragment {
        private static final String ARG_MESSAGE = "message";
        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            final Activity activity = getActivity();
            if(activity == null) throw new RuntimeException("illegal state!! activity is null!!");
            android.os.Bundle bundle = getArguments();
            if(bundle == null) throw new RuntimeException("illegal state!! bundle is null!!");

            return new AlertDialog.Builder(activity)
                    .setMessage(bundle.getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }
    }
}