package kr.ac.ssu.cse.jahn.textsnapper.ocr;

import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.googlecode.leptonica.android.Pix;

import kr.ac.ssu.cse.jahn.textsnapper.R;

public class OCRService extends Service
{
    public final static String EXTRA_NATIVE_PIX = "pix_pointer";
    private final static String TAG = OCRService.class.getSimpleName();

    private Bitmap image;
    private String datappath="";
    private String lang = "eng";
    private boolean mReceiverRegistered;
    private Uri photoUri;
    private OCRProcessor mProcessor;
    private Messenger mMessenger;
    private IOCRServiceCallback mCallback = null;
    private AsyncTask<Void, Void, ImageLoadAsyncTask.LoadResult> mImageLoadTask;
    private int mAccuracy;

    IOCRService.Stub mBinder = new IOCRService.Stub()
    {
        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException
        {

        }
        @Override
        public void setCallback(IOCRServiceCallback callback)
        {
            mCallback = callback;
        }
        @Override
        public void startOCR(final Intent intent) throws RemoteException
        {
            if(intent==null)
            {
                Log.e(TAG,"intent is null");
                return;
            }
            lang = intent.getStringExtra("lang");
            new Thread()
            {
                @Override
                public void run()
                {
                    processCommand(intent);
                }
            }.start();
        }
    };

    @Override
    public void onCreate()
    {
        mMessenger = new Messenger(new ProgressHandler(this));
        mProcessor = new OCRProcessor(mMessenger);
    }

    private void processCommand(Intent intent)
    {
        Log.e(TAG, "processCommand()");

        photoUri = intent.getData();
        if (photoUri==null)
            Log.e(TAG,"can't get parcelable from intent");
        if (mImageLoadTask!=null)
            mImageLoadTask.cancel(true);
        //CROP에서 왔으면 skip. 아니면 skipcrop=false
        final boolean skipCrop = intent.getSerializableExtra("imagesource")==ImageSource.CROP;

        registerImageLoadReceiver();
        mImageLoadTask = new ImageLoadAsyncTask(this, skipCrop, photoUri);
        mImageLoadTask.execute();
    }
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (mReceiverRegistered)
            {
                Log.e(TAG, "onReceive " + OCRService.this);
                if (intent.getAction().equalsIgnoreCase(ImageLoadAsyncTask.ACTION_IMAGE_LOADED))
                {
                    unRegisterImageLoadReceiver();
                    final long nativePix = intent.getLongExtra(ImageLoadAsyncTask.EXTRA_PIX, 0);
                    final int statusNumber = intent.getIntExtra(ImageLoadAsyncTask.EXTRA_STATUS, ImageLoadAsyncTask.PixLoadStatus.SUCCESS.ordinal());
                    final boolean skipCrop = intent.getBooleanExtra(ImageLoadAsyncTask.EXTRA_SKIP_CROP, false);
                    startOCRProcessor(nativePix, ImageLoadAsyncTask.PixLoadStatus.values()[statusNumber], skipCrop);
                }
            }
        }
    };

    private synchronized void unRegisterImageLoadReceiver()
    {
        if (mReceiverRegistered)
        {
            Log.e(TAG, "unRegisterImageLoadReceiver " + mMessageReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
            mReceiverRegistered = false;
        }
    }
    
    private synchronized void registerImageLoadReceiver()
    {
        if (!mReceiverRegistered)
        {
            Log.e(TAG, "registerImageLoadReceiver " + mMessageReceiver);
            final IntentFilter intentFilter = new IntentFilter(ImageLoadAsyncTask.ACTION_IMAGE_LOADED);
            intentFilter.addAction(ImageLoadAsyncTask.ACTION_IMAGE_LOADING_START);
            LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, intentFilter);
            mReceiverRegistered = true;
        }
    }

    private void startOCRProcessor(long nativePix, ImageLoadAsyncTask.PixLoadStatus pixLoadStatus, boolean skipCrop)
    {
        if (pixLoadStatus == ImageLoadAsyncTask.PixLoadStatus.SUCCESS)
        {
            if (skipCrop)
            {
                Log.e("TAG", "Crop skipped");
                final Pix pix = new Pix(nativePix);
                mProcessor.doOCR(getApplicationContext(), lang, pix);
            }
            else
            {
                Log.e("TAG", "To CropActivity");
                Intent actionIntent = new Intent(this, CropActivity.class);
                actionIntent.putExtra(EXTRA_NATIVE_PIX, nativePix);
                //결과 확인 불가하므로 액티비티 에서 종료시 브로드캐스트 송신
                startActivity(actionIntent);
            }
        }
        else
            showFileError(pixLoadStatus);
    }
    void showFileError(ImageLoadAsyncTask.PixLoadStatus status) {
        showFileError(status, null);
    }
    protected void showFileError(ImageLoadAsyncTask.PixLoadStatus second, DialogInterface.OnClickListener positiveListener) {
        int textId;
        switch (second) {
        case IMAGE_NOT_32_BIT:
            textId = R.string.image_not_32_bit;
            break;
        case IMAGE_FORMAT_UNSUPPORTED:
            textId = R.string.image_format_unsupported;
            break;
        case IMAGE_COULD_NOT_BE_READ:
            textId = R.string.image_could_not_be_read;
            break;
        case IMAGE_DOES_NOT_EXIST:
            textId = R.string.image_does_not_exist;
            break;
        case IO_ERROR:
            textId = R.string.gallery_io_error;
            break;
        case CAMERA_APP_NOT_FOUND:
            textId = R.string.camera_app_not_found;
            break;
        case MEDIA_STORE_RETURNED_NULL:
            textId = R.string.media_store_returned_null;
            break;
        case CAMERA_APP_ERROR:
            textId = R.string.camera_app_error;
            break;
        case CAMERA_NO_IMAGE_RETURNED:
            textId = R.string.camera_no_image_returned;
            break;
        default:
            textId = R.string.error_could_not_take_photo;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setTitle(R.string.error_title);
        final TextView textview = new TextView(this);
        textview.setText(textId);
        alert.setView(textview);
        alert.setPositiveButton(android.R.string.ok, positiveListener);
        alert.show();
    }

    private void preprocess(Bitmap bitmap)
    {

    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.e(TAG, "OCR Service binded");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Log.e(TAG, "OCR Service unbinded");
        return super.onUnbind(intent);
    }

    public class ProgressHandler extends Handler
    {
        private OCRService mOCRService;
        private String hocrString;
        private String utf8String;
        //private long layoutPix;
        private int mPreviewWith;
        private int mPreviewHeight;
        private boolean mHasStartedOcr = false;
        private Pix mFinalPix;

        public ProgressHandler(OCRService root)
        {
            mOCRService = root;
        }

        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            case OCRProcessor.MESSAGE_EXPLANATION_TEXT:
                Toast.makeText(getApplicationContext(), getText(msg.arg1), Toast.LENGTH_LONG).show();
                break;
            case OCRProcessor.MESSAGE_TESSERACT_PROGRESS:
                if (!mHasStartedOcr)
                    mHasStartedOcr = true;
                int percent = msg.arg1;
                Bundle data = msg.getData();
            //진행도 데이터 처리
                break;
            case OCRProcessor.MESSAGE_PREVIEW_IMAGE:
                mPreviewHeight = ((Bitmap) msg.obj).getHeight();
                mPreviewWith = ((Bitmap) msg.obj).getWidth();
                break;
            case OCRProcessor.MESSAGE_FINAL_IMAGE:
                long nativePix = (long) msg.obj;
                if (nativePix != 0)
                    mFinalPix = new Pix(nativePix);
                break;
            case OCRProcessor.MESSAGE_HOCR_TEXT:
                this.hocrString = (String) msg.obj;
                mAccuracy = msg.arg1;
                break;
            case OCRProcessor.MESSAGE_UTF8_TEXT:
                this.utf8String = (String) msg.obj;
                //Toast.makeText(OCRService.this, this.utf8String, Toast.LENGTH_SHORT).show();
                if (mCallback!=null)
                {
                    try
                    {
                        mCallback.sendResult(msg);
                    } catch (RemoteException e)
                    {
                        e.printStackTrace();
                    }
                }
                break;
            case OCRProcessor.MESSAGE_END:
                Toast.makeText(OCRService.this, "MSG_END", Toast.LENGTH_SHORT).show();
                break;
            case OCRProcessor.MESSAGE_ERROR:
                Toast.makeText(getApplicationContext(), getText(msg.arg1), Toast.LENGTH_LONG).show();
                break;
            }
        }
    }
}
