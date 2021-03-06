package kr.ac.ssu.cse.jahn.textsnapper.ui;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.media.AudioAttributes;
import android.media.ImageReader;
import android.media.SoundPool;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.leptonica.android.WriteFile;
import com.squareup.picasso.MemoryPolicy;
import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;

import kr.ac.ssu.cse.jahn.textsnapper.R;
import kr.ac.ssu.cse.jahn.textsnapper.ocr.IOCRService;
import kr.ac.ssu.cse.jahn.textsnapper.ocr.IOCRServiceCallback;
import kr.ac.ssu.cse.jahn.textsnapper.ocr.OCRProcessor;
import kr.ac.ssu.cse.jahn.textsnapper.ocr.OCRService;
import kr.ac.ssu.cse.jahn.textsnapper.util.PrefUtils;
import kr.ac.ssu.cse.jahn.textsnapper.util.TranslateHelper;
import kr.ac.ssu.cse.jahn.textsnapper.util.Utils;

import static android.content.ContentValues.TAG;
import static android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN;
import static android.view.WindowManager.LayoutParams.TYPE_PHONE;
import static kr.ac.ssu.cse.jahn.textsnapper.util.Utils.EDIT_PATH;
import static kr.ac.ssu.cse.jahn.textsnapper.util.Utils.capture;
import static kr.ac.ssu.cse.jahn.textsnapper.util.Utils.createVirtualDisplay;

public class FloatingService extends Service {

    private static final int FOREGROUND_ID = 7816;
    private static final int REQUEST_PENDING = 9048;
    private static boolean isServiceActive;
    private static boolean isBarActive;
    private static boolean isEng;
    private static boolean canDrawBar;
    private static boolean isFixed;
    private static boolean isHidden;
    private static boolean _cropmode;
    private static boolean isResultPopup;

    protected FileObserver mObserver;
    final static String ACTION_SCREENSHOT = FloatingService.class.getName()+".screenshot.captured";
    final static String ACTION_CROP = FloatingService.class.getName()+".cropped";
    private boolean mReceiverRegistered;
    protected MediaProjectionManager mProjectionManager;
    protected MediaProjection mProjection;
    protected ImageReader mImageReader;

    private SoundPool mSoundPool;
    private int soundID = 0;
    private boolean soundLoaded = false;

    private static WindowManager windowManager;
    private static RelativeLayout removeHead, floatingHead;
    private static RelativeLayout floatingBar;
    private static CropView mCropView;
    private ImageView removeImage, floatingImage;
    private ImageView screenshotImage, cropImage;
    private static ImageView languageImage;
    private Point windowSize;

    protected IOCRService mBinder = null;
    protected Pix mFinalPix;
    protected String mResultString;
    protected Uri uriForOCR;
    protected Bitmap mResultBitmap;
    private boolean mBinded = false;

    // 접근 금지!!
    private static FloatingService thisService;

    private ServiceConnection mConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            Log.e(TAG, "Service Connected");
            mBinder = IOCRService.Stub.asInterface(service);
            mBinded = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            Log.e(TAG, "Service disconnected");
            mBinder = null;
            mBinded = false;
        }
    };

    IOCRServiceCallback mCallback = new IOCRServiceCallback()
    {
        @Override
        public void basicTypes(int anInt, long aLong, boolean aBoolean, float aFloat, double aDouble, String aString) throws RemoteException
        {

        }

        @Override
        public void sendResult(Message msg) throws RemoteException
        {
            switch (msg.what)
            {
            case OCRProcessor.MESSAGE_FINAL_IMAGE:
                long nativePix = (long) msg.obj;
                if (nativePix != 0)
                {
                    mFinalPix = new Pix(nativePix);
                    mResultBitmap = WriteFile.writeBitmap(mFinalPix);
                }
                break;
            case OCRProcessor.MESSAGE_UTF8_TEXT:
                mResultString = (String) msg.obj;
                Log.e(TAG,"스트링"+mResultString);
                break;
            case OCRProcessor.MESSAGE_END:
                attachTop();
                showResult();
                break;
            }
        }

        @Override
        public IBinder asBinder()
        {
            return null;
        }
    };

    protected void startOCR()
    {
        Intent source = new Intent();
        Log.e(TAG,"Uri: "+uriForOCR);
        source.setData(uriForOCR);
        source.putExtra("lang", PrefUtils.getLanguage(this));
        //main action
        try
        {
            mBinder.setCallback(mCallback);
            mBinder.startOCR(source);
        }
        catch (RemoteException e)
        {
            Log.e(TAG, "RemoteException");
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate() {
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        super.onCreate();
    }

    private void handleStart()
    {
        /**
         * OCR서비스 바인딩
         */
        bindService(new Intent(this, OCRService.class), mConnection, BIND_AUTO_CREATE);

        /**
         * WindowManager로 Floating Head 관리
         * create virtual display 호출을 위해 onStartCommand 로 이동
         */
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        /**
         * boolean 변수 관리
         */
        thisService = this;
        isBarActive = false;
        isServiceActive = true;
        canDrawBar = true;
        isFixed = PrefUtils.isFixed(getApplicationContext());
        isHidden = false;
        isResultPopup = false;

        /**
         * Remove Head inflate 부분
         * TYPE_PHONE Flag가 deprecated라고 하여 삭제할 생각은 XXXXXX
         * TYPE_PHONE Flag를 전달해야 이벤트를 처리가능하게 Overlay 가능
         */
        removeHead = (RelativeLayout) inflater.inflate(R.layout.activity_remove, null);
        WindowManager.LayoutParams removeParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PRIORITY_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        removeParams.gravity = Gravity.TOP | Gravity.LEFT;
        // removeHead는 호출 시에만 보여야 하기 때문에 기본 Gone!
        // 하지만 ImageView는 화면에 그려진 후에 width, height가 업데이트 되므로
        // 최초는 INVISIBLE로 화면에 그려야 width, height 정보를 얻어올 수 있다.
        removeHead.setVisibility(View.INVISIBLE);
        removeImage = (ImageView) removeHead.findViewById(R.id.removeImage);
        windowManager.addView(removeHead, removeParams);

        /**
         * Floating Head inflate 부분
         */
        floatingHead = (RelativeLayout) inflater.inflate(R.layout.activity_floating, null);
        floatingImage = (ImageView) floatingHead.findViewById(R.id.floatingImage);

        windowSize = new Point();
        windowManager.getDefaultDisplay().getSize(windowSize);
        WindowManager.LayoutParams floatingParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PRIORITY_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        floatingParams.gravity = Gravity.TOP | Gravity.LEFT;
        /**
         * Floating Button의 초기 위치 설정 코드
         * 이후 사양이 변경되면 이 곳을 수정
         */
        floatingParams.x = windowSize.x - (int) (floatingImage.getLayoutParams().width * 0.75);
        floatingParams.y = (int) (windowSize.y * 0.75);
        windowManager.addView(floatingHead, floatingParams);

        floatingHead.setOnTouchListener(new View.OnTouchListener()
        {
            long startTime = 0;
            long endTime = 0;
            boolean isLongClick = false;
            boolean isOnRemoveHead = false;
            int initX;
            int initY;
            int marginX;
            int marginY;
            int removeImageWidth;
            int removeImageHeight;

            //Handler
            Handler longHandler = new Handler();
            Runnable longRunnable = new Runnable()
            {
                @Override
                public void run()
                {
                    isLongClick = true;
                    removeHead.setVisibility(View.VISIBLE);
                    showFloatingRemove();
                }
            };

            /**
             * Floating Button Touch Event
             */
            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                WindowManager.LayoutParams newFloatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();

                int leftMax = -floatingImage.getWidth() / 2;
                int rightMax = windowSize.x - floatingImage.getWidth() / 2;
                int topMax = 0;
                int bottomMax = windowSize.y - floatingImage.getHeight();
                /**
                 * 지속적으로 현재 위치를 업데이트
                 * RawX, Y를 받아올 것!
                 * getX, getY를 그냥 받아오면 상대좌표라 계속 흔들림
                 */
                int currentX = (int) event.getRawX();
                int currentY = (int) event.getRawY();
                boolean removeHandlerPosted = false;

                // 이동되는 좌표
                int afterX;
                int afterY;
                if (!_cropmode)
                {
                    // floatingHead를 이동할 수 있으면
                    if (!isFixed)
                    {
                        switch (event.getAction())
                        {
                        // 롱클릭
                        case MotionEvent.ACTION_DOWN:
                            removeImageWidth = removeImage.getLayoutParams().width;
                            removeImageHeight = removeImage.getLayoutParams().height;
                            startTime = System.currentTimeMillis();
                            // 삭제하는 이미지가 얼마나 오랫동안 누르고 있어야 등장할 지 결정
                            longHandler.postDelayed(longRunnable, 300);
                            removeHandlerPosted = true;

                            /**
                             * Floating Button을 움직일 때 기준이 되는 위치
                             */
                            initX = currentX;
                            initY = currentY;

                            marginX = newFloatingParams.x;
                            marginY = newFloatingParams.y;

                            break;
                        // 이동
                        case MotionEvent.ACTION_MOVE:
                            int dx = currentX - initX;
                            int dy = currentY - initY;

                            // 만약 floatingBar가 띄워져 있다면 다시 집어넣는다.
                            if (isBarActive)
                            {
                                showFloatingBar();
                                if (!removeHandlerPosted)
                                {
                                    longHandler.postDelayed(longRunnable, 300);
                                    removeHandlerPosted = true;
                                }
                            }

                            afterX = marginX + dx;
                            afterY = marginY + dy;

                            if (afterX < leftMax)
                                afterX = leftMax;
                            if (afterX > rightMax)
                                afterX = rightMax;
                            if (afterY < topMax)
                                afterY = topMax;
                            if (afterY > bottomMax)
                                afterY = bottomMax;

                            // 단순히 움직일 뿐만 아니라 삭제할 수 있는 롱클릭 이벤트일 경우
                            if (isLongClick)
                            {
                                // removeHead의 위치를 수동으로 적어줘야 함. 수정 xxxxx
                                int removeLeftBound = windowSize.x / 2 - (int) (removeImageWidth * 1.5);
                                int removeRightBound = windowSize.x / 2 + (int) (removeImageWidth * 1.5);
                                int removeTopBound = windowSize.y - (int) (removeImageHeight * 1.5);

                                if ((currentX >= removeLeftBound && currentX <= removeRightBound)
                                        && currentY >= removeTopBound)
                                {
                                    isOnRemoveHead = true;

                                    int removeX = (int) ((windowSize.x - (removeImageHeight * 1.5)) / 2);
                                    int removeY = (int) (windowSize.y - ((removeImageWidth * 1.5) + getStatusBarHeight()));

                                    Log.v("DEBUG!", "RemoveX : " + removeImageHeight + " RemoveY : " + removeImageWidth);

                                    if (removeImage.getLayoutParams().height == removeImageHeight)
                                    {
                                        removeImage.getLayoutParams().height = (int) (removeImageHeight * 1.5);
                                        removeImage.getLayoutParams().width = (int) (removeImageWidth * 1.5);

                                        WindowManager.LayoutParams newRemoveParams = (WindowManager.LayoutParams) removeHead.getLayoutParams();
                                        newRemoveParams.x = removeX;
                                        newRemoveParams.y = removeY;

                                        windowManager.updateViewLayout(removeHead, newRemoveParams);
                                    }

                                    newFloatingParams.x = removeX + (Math.abs(removeHead.getWidth() - floatingHead.getWidth())) / 2;
                                    newFloatingParams.y = removeY + (Math.abs(removeHead.getHeight() - floatingHead.getHeight())) / 2;

                                    windowManager.updateViewLayout(floatingHead, newFloatingParams);
                                    break;
                                } else
                                {
                                    isOnRemoveHead = false;
                                    removeImage.getLayoutParams().height = removeImageHeight;
                                    removeImage.getLayoutParams().width = removeImageWidth;

                                    WindowManager.LayoutParams newRemoveParams = (WindowManager.LayoutParams) removeHead.getLayoutParams();
                                    int removeX = (windowSize.x - removeHead.getWidth()) / 2;
                                    int removeY = windowSize.y - (removeHead.getHeight() + getStatusBarHeight());

                                    newRemoveParams.x = removeX;
                                    newRemoveParams.y = removeY;

                                    windowManager.updateViewLayout(removeHead, newRemoveParams);
                                }
                            }

                            newFloatingParams.x = afterX;
                            newFloatingParams.y = afterY;

                            windowManager.updateViewLayout(floatingHead, newFloatingParams);
                            break;
                        case MotionEvent.ACTION_UP:
                            isLongClick = false;
                            removeHead.setVisibility(View.GONE);
                            removeImage.getLayoutParams().height = removeImageHeight;
                            removeImage.getLayoutParams().width = removeImageWidth;
                            longHandler.removeCallbacks(longRunnable);

                            if (isOnRemoveHead)
                            {
                                stopService(new Intent(FloatingService.this, FloatingService.class));
                                isOnRemoveHead = false;
                                break;
                            }

                            int diffX = currentX - initX;
                            int diffY = currentY - initY;

                            // X, Y 이동값이 적은 경우는 FloatingBar를 띄우는 액션으로 본다
                            if (Math.abs(diffX) < 5 && Math.abs(diffY) < 5)
                            {
                                endTime = System.currentTimeMillis();
                                // 물론 클릭했던 시간이 적은 경우에만
                                if ((endTime - startTime) < 500)
                                {
                                    showFloatingBar();
                                }
                            }

                            /**
                             * 개발중인 기능 Test Code


                             attachTop();
                             showResult();
                             */


                            afterY = marginY + diffY;

                            if (afterY < topMax)
                                afterY = topMax;
                            if (afterY > bottomMax)
                                afterY = bottomMax;

                            newFloatingParams.y = afterY;


                            // 만약 X 이동값이 큰 경우, 벽에 붙인다.
                            if (Math.abs(diffX) >= 5)
                            {
                                attachSide(currentX);
                            }

                            isOnRemoveHead = false;

                            break;
                        }
                        return true;
                    }
                    // Floating Head를 이동할 수 없으면
                    else
                    {
                        switch (event.getAction())
                        {
                        case MotionEvent.ACTION_DOWN:
                            removeImageWidth = removeImage.getLayoutParams().width;
                            removeImageHeight = removeImage.getLayoutParams().height;
                            break;
                        case MotionEvent.ACTION_UP:
                            showFloatingBar();
                            break;
                        }
                        return true;
                    }
                }
                else
                    return false;
            }
        });
        floatingHead.setOnClickListener(new View.OnClickListener()
        {
            /**
             * 크롭완료후 클릭시
             */
            @Override
            public void onClick(View v)
            {
                Log.e(TAG,"클릭됨");
                if (_cropmode)
                {
                    final RectF cropRect = mCropView.getCurrentRect();
                    mCropView.collapse();
                    mSoundPool.play(soundID,1,1,0,0,1.0f);
                    toggleHide();
                    Handler handler = new Handler();
                    Runnable shot = new Runnable() {
                        @Override
                        public void run() {
                            File captured = Utils.saveScreenShot(capture(mImageReader, windowManager.getDefaultDisplay()),null);
                            Intent broadcastIntent = new Intent(ACTION_CROP);
                            broadcastIntent.putExtra("path", Uri.fromFile(captured).getPath());
                            broadcastIntent.putExtra("croprect", cropRect);
                            sendBroadcast(broadcastIntent);
                        }
                    };
                    handler.postDelayed(shot, 150);
                    Runnable restore = new Runnable() {
                        @Override
                        public void run() {
                            toggleHide();
                        }
                    };
                    handler.postDelayed(restore, 100);
                    //showFloatingBar();
                }
            }
        });
    }


    /**
     * 상태 표시줄의 높이를 반환
     */
    private int getStatusBarHeight() {
        int statusBarHeight = (int) Math.ceil(25 * getApplicationContext().getResources().getDisplayMetrics().density);
        return statusBarHeight;
    }

    private double bounceValue(long step, long scale){
        double value = scale * java.lang.Math.exp(-0.055 * step) * java.lang.Math.cos(0.08 * step);
        return value;
    }

    private void floatingHeadToLeft(final int currentX){
        final int afterX = windowSize.x - currentX;
        new CountDownTimer(500, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
            public void onTick(long t) {
                long step = (500 - t)/5;
                mParams.x = -(int)(floatingImage.getLayoutParams().width * 0.25) - (int)(double)bounceValue(step, afterX);
                windowManager.updateViewLayout(floatingHead, mParams);
            }
            public void onFinish() {
                mParams.x = -(int)(floatingImage.getLayoutParams().width * 0.25);
                windowManager.updateViewLayout(floatingHead, mParams);
            }
        }.start();
    }
    private void floatingHeadToRight(final int currentX){
        new CountDownTimer(500, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
            public void onTick(long t) {
                long step = (500 - t)/5;
                mParams.x = windowSize.x - (int)(floatingImage.getLayoutParams().width * 0.75) + (int)(double)bounceValue(step, currentX);
                windowManager.updateViewLayout(floatingHead, mParams);
            }
            public void onFinish() {
                mParams.x = windowSize.x - (int)(floatingImage.getLayoutParams().width * 0.75);
                windowManager.updateViewLayout(floatingHead, mParams);
            }
        }.start();
    }
    private void attachSide(int currentX) {
        if (currentX <= windowSize.x / 2) {
            floatingHeadToLeft(currentX);
        } else {
            floatingHeadToRight(currentX);
        }
    }

    /**
     * 호출시 상단으로 Floating Button 이동
     */
    private void attachTop() {
        if(isBarActive) {
            showFloatingBar();
        }
        new CountDownTimer(100, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
            public void onTick(long t) {
                int step = mParams.y / 20;
                mParams.y -= step;
                windowManager.updateViewLayout(floatingHead, mParams);
            }
            public void onFinish() {
                mParams.y = 0;
                windowManager.updateViewLayout(floatingHead, mParams);
            }
        }.start();
    }

    private void showResult() {
        if(!isResultPopup) {
            /**
             * 최초 Result PopUp Params 설정
             */
            WindowManager.LayoutParams resultParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    //WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                            WindowManager.LayoutParams.FLAG_DIM_BEHIND |
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            resultParams.dimAmount = 0.5f;
            resultParams.gravity = Gravity.TOP | Gravity.LEFT;

            LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

            /**
             * Inflate & FindView
             */
            final LinearLayout resultLayout = (LinearLayout) inflater.inflate(R.layout.result_pop_up, null);
            LinearLayout cancelArea = (LinearLayout) resultLayout.findViewById(R.id.cancelArea);
            final ImageView finalImage = (ImageView) resultLayout.findViewById(R.id.finalimage);
            Picasso.with(getApplicationContext()).load(uriForOCR).into(finalImage);

            ImageView save = (ImageView) resultLayout.findViewById(R.id.save);
            final ImageView translate = (ImageView) resultLayout.findViewById(R.id.translate);
            ImageView cancel = (ImageView) resultLayout.findViewById(R.id.cancel);
            final EditText editText = (EditText) resultLayout.findViewById(R.id.mText);
            editText.setText(mResultString);

            /**
             * View 위치 설정 및 추가
             */
            resultParams.x = 0;
            resultParams.y = 0;
            windowManager.addView(resultLayout, resultParams);
            isResultPopup = true;

            /**
             * Event Listener 설정
             */
            View.OnClickListener cancelEventListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    /**
                     * Cancel Area 또는 Cancel 버튼 눌렀을 때의 행동
                     */
                    windowManager.removeView(resultLayout);
                    isResultPopup = false;
                }
            };
            cancelArea.setOnClickListener(cancelEventListener);
            cancel.setOnClickListener(cancelEventListener);
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    /**
                     * 저장버튼 눌렀을 때의 행동
                     */
                    String text = editText.getText().toString();

                    //String path = Utils.DATA_PATH;
                    String path = Utils.getRealPathFromUri(getApplicationContext(), uriForOCR);
                    path = Utils.convertPathToTxt(path);

                    BufferedReader br = null;
                    BufferedWriter bw = null;
                    try {
                        br = new BufferedReader(new StringReader(text));
                        bw = new BufferedWriter(new FileWriter(path));
                        String buf;

                        while( (buf = br.readLine()) != null ) {
                            bw.write(buf);
                            bw.newLine();
                        }
                        bw.flush();
                        Toast.makeText(getApplicationContext(), "저장 성공!", Toast.LENGTH_LONG).show();
                        windowManager.removeView(resultLayout);
                        isResultPopup = false;
                    }catch(IOException e) {
                        Toast.makeText(getApplicationContext(), "저장 실패", Toast.LENGTH_LONG).show();
                        Log.d("DEBUG9", e.toString());
                    }finally{
                        Utils.close(br);
                        Utils.close(bw);
                    }
                }
            });

            translate.setOnClickListener(new View.OnClickListener() {
                boolean isTranslated = false;
                String originalText = editText.getText().toString();
                /**
                 * 번역버튼 눌렀을 때의 행동
                 */
                @Override
                public void onClick(View v) {
                    String currentText = editText.getText().toString();
                    Log.e(TAG, currentText);
                    if(!isTranslated) {
                        boolean isEng = PrefUtils.isEng(getApplicationContext());
                        final TranslateHelper translateHelper = TranslateHelper.getInstance(isEng, currentText);
                        originalText = currentText;
                        Runnable translateRunnable = new Runnable() {
                            @Override
                            public void run() {
                                String text = translateHelper.getResultText();
                                Log.e(TAG, text);
                                editText.setText(text);
                                translate.setImageResource(R.drawable.undo);
                                isTranslated = true;
                            }
                        };
                        translateHelper.setTranslateRunnable(translateRunnable);
                        translateHelper.start();
                    } else {
                        editText.setText(originalText);
                        translate.setImageResource(R.drawable.translate);
                        isTranslated = false;
                    }
                }
            });
            save.setOnTouchListener(Utils.imageTouchEventListener);
            translate.setOnTouchListener(Utils.imageTouchEventListener);
            cancel.setOnTouchListener(Utils.imageTouchEventListener);
        }
    }

    /**
     * ocrImageChange로, PrefFragment에서 사용하기 위함
     */
    protected static void setLanguageImage(String curLang) {
        if(curLang.equals("한국어")) {
            languageImage.setImageResource(R.drawable.kor);
        } else if(curLang.equals("English")) {
            languageImage.setImageResource(R.drawable.eng);
        }
    }

    /**
     * Long Click을 진행했을 때
     * Floating Button을 삭제할 수 있는 Remove Head를 보여줌
     */
    private void showFloatingRemove(){
        WindowManager.LayoutParams removeParams = (WindowManager.LayoutParams) removeHead.getLayoutParams();
        int x = (windowSize.x - removeHead.getWidth()) / 2;
        int y = windowSize.y - (removeHead.getHeight() + getStatusBarHeight());

        removeParams.x = x;
        removeParams.y = y;

        windowManager.updateViewLayout(removeHead, removeParams);
    }
    /**
     * Click을 진행했을 때
     * Floating Bar를 보여줌
     */
    private void showFloatingBar() {
        // floatingBar를 그릴 수 있는 상태이면
        if (canDrawBar&&!_cropmode) {
            // 만약 floatingBar를 집어넣어야 하는 상황이면
            if (floatingBar != null && isBarActive) {
                canDrawBar = false;
                WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();;
                // floatingHead가 왼쪽 벽에 붙어있는 경우 애니메이션
                if (floatingParams.x < windowSize.x / 2) {
                    new CountDownTimer(500, 5) {
                        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                        final int initialX = floatingParams.x;
                        WindowManager.LayoutParams barParams = (WindowManager.LayoutParams) floatingBar.getLayoutParams();
                        WindowManager.LayoutParams mParams = barParams;
                        int gap = -(screenshotImage.getLayoutParams().width * 4) - initialX;
                        public void onTick(long t) {
                            long step = (550 - t) / 7;
                            int accValue = (int)(gap / step);

                            mParams.x = barParams.x + accValue;
                            mParams.y = floatingParams.y;
                            windowManager.updateViewLayout(floatingBar, mParams);
                        }

                        public void onFinish() {
                            mParams.x = initialX - screenshotImage.getLayoutParams().width * 4;
                            mParams.y = floatingParams.y;
                            windowManager.updateViewLayout(floatingBar, mParams);
                            windowManager.removeView(floatingBar);
                            isBarActive = false;
                            canDrawBar = true;
                        }
                    }.start();
                }
                // floatingHead가 오른쪽 벽에 붙어있는 경우 애니메이션
                else {
                    new CountDownTimer(500, 5) {
                        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                        final int initialX = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);
                        WindowManager.LayoutParams barParams = (WindowManager.LayoutParams) floatingBar.getLayoutParams();
                        WindowManager.LayoutParams mParams = barParams;

                        public void onTick(long t) {
                            int step = (550 - (int) t) / 7;
                            int gap = windowSize.x - initialX;
                            int accValue = gap / step;

                            mParams.x = barParams.x + accValue;
                            mParams.y = floatingParams.y;
                            windowManager.updateViewLayout(floatingBar, mParams);
                        }

                        public void onFinish() {
                            mParams.x = windowSize.x;
                            mParams.y = floatingParams.y;
                            windowManager.updateViewLayout(floatingBar, mParams);
                            windowManager.removeView(floatingBar);
                            isBarActive = false;
                            canDrawBar = true;
                        }
                    }.start();
                }
                floatingImage.setImageResource(R.drawable.floating_image);
            }
            // 만약 floatingBar를 꺼내야 하는 상황이면
            else {
                canDrawBar = false;
                LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                // floatingHead가 왼쪽 벽에 붙어있는 경우
                if (floatingParams.x < windowSize.x / 2) {
                    floatingBar = (RelativeLayout) inflater.inflate(R.layout.activity_floatingbar_left, null);

                    screenshotImage = (ImageView) floatingBar.findViewById(R.id.floatingScreentshotLeft);
                    cropImage = (ImageView) floatingBar.findViewById(R.id.floatingCropLeft);
                    languageImage = (ImageView) floatingBar.findViewById(R.id.floatingLanguageLeft);
                    floatingImage.setImageResource(R.drawable.floating_fold_left);
                }
                // floatingHead가 오른쪽 벽에 붙어있는 경우
                else {
                    floatingBar = (RelativeLayout) inflater.inflate(R.layout.activity_floatingbar_right, null);

                    screenshotImage = (ImageView) floatingBar.findViewById(R.id.floatingScreentshotRight);
                    cropImage = (ImageView) floatingBar.findViewById(R.id.floatingCropRight);
                    languageImage = (ImageView) floatingBar.findViewById(R.id.floatingLanguageRight);
                    floatingImage.setImageResource(R.drawable.floating_fold_right);
                }

                /**
                 * Bar Layout을 펼쳤을 때
                 * Ocr Language 버튼 환경설정과 연동
                 */
                isEng = PrefUtils.isEng(getApplicationContext());

                if (isEng) {
                    languageImage.setImageResource(R.drawable.eng);
                } else {
                    languageImage.setImageResource(R.drawable.kor);
                }

                screenshotImage.setOnTouchListener(Utils.imageTouchEventListener);
                screenshotImage.setOnClickListener(imageClickEventListener);
                cropImage.setOnTouchListener(Utils.imageTouchEventListener);
                cropImage.setOnClickListener(imageClickEventListener);
                languageImage.setOnTouchListener(Utils.imageTouchEventListener);

                /**
                 * Language Image를 클릭했을 때
                 * 환경설정에 적용
                 * ==> 환경설정을 변경하였을 때
                 *     Bar Layout과 연동하는 코드는
                 *     Method화하여 PrefFragment에서 처리함. 참고
                 */
                languageImage.setOnClickListener(new ImageView.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = pref.edit();
                        if (isEng) {
                            editor.putString("ocrSelect", "한국어");
                            isEng = false;
                            languageImage.setImageResource(R.drawable.kor);
                        } else {
                            editor.putString("ocrSelect", "English");
                            isEng = true;
                            languageImage.setImageResource(R.drawable.eng);
                        }
                        editor.commit();
                    }
                });

                WindowManager.LayoutParams barParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        TYPE_PHONE,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        PixelFormat.TRANSLUCENT);
                barParams.gravity = Gravity.TOP | Gravity.LEFT;

                /**
                 * Bar Layout 위치 설정
                 */
                // 왼쪽 벽에 붙어있는 경우
                if (floatingParams.x < windowSize.x / 2) {
                    barParams.x = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);
                    //barParams.x = floatingParams.x + (int)(floatingImage.getWidth() * 0.25);
                    barParams.y = floatingParams.y;
                }
                // 오른쪽 벽에 붙어있는 경우
                else {
                    barParams.x = floatingParams.x + (int) (screenshotImage.getLayoutParams().width * 3.65);
                    //barParams.x = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);
                    barParams.y = floatingParams.y;
                }
                windowManager.addView(floatingBar, barParams);

                // floatingHead가 왼쪽 벽에 붙어있는 경우 애니메이션
                if (floatingParams.x < windowSize.x / 2) {
                    canDrawBar = false;
                    isFixed = true;
                    new CountDownTimer(500, 5) {
                        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                        WindowManager.LayoutParams barParams = (WindowManager.LayoutParams) floatingBar.getLayoutParams();
                        WindowManager.LayoutParams mParams = barParams;

                        public void onTick(long t) {
                            int step = (500 - (int) t) / 7;
                            int gap = (int) (floatingImage.getWidth() * 0.25) + (int) (screenshotImage.getLayoutParams().width * 3.65);
                            int accValue = gap / step;
                            mParams.x = barParams.x + accValue;
                            mParams.y = floatingParams.y;
                            if (mParams.x > floatingParams.x + (int) (floatingImage.getWidth() * 0.25))
                                mParams.x = floatingParams.x + (int) (floatingImage.getWidth() * 0.25);

                            windowManager.updateViewLayout(floatingBar, mParams);
                        }

                        public void onFinish() {
                            mParams.x = floatingParams.x + (int) (floatingImage.getWidth() * 0.25);
                            mParams.y = floatingParams.y;
                            windowManager.updateViewLayout(floatingBar, mParams);
                            canDrawBar = true;
                            isBarActive = true;
                            isFixed = PrefUtils.isFixed(getApplicationContext());
                        }
                    }.start();
                }

                // floatingHead가 오른쪽 벽에 붙어있는 경우 애니메이션
                else {
                    canDrawBar = false;
                    isFixed = true;
                    new CountDownTimer(500, 5) {
                        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                        WindowManager.LayoutParams barParams = (WindowManager.LayoutParams) floatingBar.getLayoutParams();
                        WindowManager.LayoutParams mParams = barParams;

                        public void onTick(long t) {
                            int step = (500 - (int) t) / 7;
                            int gap = -2 * (int) (screenshotImage.getLayoutParams().width * 3.65);
                            int accValue = gap / step;

                            mParams.x = barParams.x + accValue;
                            mParams.y = floatingParams.y;
                            if (mParams.x < floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65))
                                mParams.x = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);

                            windowManager.updateViewLayout(floatingBar, mParams);
                        }

                        public void onFinish() {
                            mParams.x = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);
                            mParams.y = floatingParams.y;
                            windowManager.updateViewLayout(floatingBar, mParams);
                            canDrawBar = true;
                            isBarActive = true;
                            isFixed = PrefUtils.isFixed(getApplicationContext());
                        }
                    }.start();
                }
            }
        }
    }

    /**
     * drawer의 버튼을 눌렀을 때의 동작
     */
    View.OnClickListener imageClickEventListener = new View.OnClickListener()
    {
        @Override
        public void onClick(View v)
        {
            Log.e(TAG, "clicked");
            switch(v.getId())
            {
            case R.id.floatingScreentshotLeft:
            case R.id.floatingScreentshotRight:
                Log.e(TAG, "ss taken");
                mSoundPool.play(soundID,1,1,0,0,1.0f);
                toggleHide();
                Handler handler = new Handler();
                Runnable shot = new Runnable() {
                    @Override
                    public void run() {
                        File captured = Utils.saveScreenShot(capture(mImageReader, windowManager.getDefaultDisplay()),null);
                        Intent brodcastIntent = new Intent(ACTION_SCREENSHOT);
                        brodcastIntent.putExtra("path",Uri.fromFile(captured).getPath());
                        sendBroadcast(brodcastIntent);
                    }
                };
                handler.postDelayed(shot, 100);
                Runnable restore = new Runnable() {
                    @Override
                    public void run() {
                        toggleHide();
                        showFloatingBar();
                    }
                };
                handler.postDelayed(restore, 100);
                break;
            case R.id.floatingCropLeft:
            case R.id.floatingCropRight:
                if (!_cropmode)
                {
                    showFloatingBar();
                    _cropmode = true;
                    mCropView = new CropView(thisService, windowSize.x, windowSize.y, windowManager);
                    WindowManager.LayoutParams cropParams = new WindowManager.LayoutParams(
                            windowSize.x,
                            windowSize.y,
                            TYPE_PHONE,
                            FLAG_FULLSCREEN,
                            PixelFormat.RGBA_8888);
                    windowManager.addView(mCropView,cropParams);
                }
                break;
            }
        }
    };

    /**
     * Pending Intent를 이용해서 App이 꺼져도
     * Floating Button이 죽지않도록 Notification을 이용한다.
     */
    private PendingIntent createPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        // 마지막 인자 Flag == 0
        return PendingIntent.getActivity(this, REQUEST_PENDING, intent, 0);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
 	private Notification createNotification(PendingIntent intent) {
        return new Notification.Builder(this)
                .setContentTitle(getText(R.string.app_name))
                /**
                 * setSmallIcon은 앱 이미지가 결정되면 그때 반드시 수정해야 함!
                 */
                .setSmallIcon(R.mipmap.floating_image)
                .setContentIntent(intent)
                .build();
    }

    /**
     * 스크린샷편집후 또는 크롭완료를 감지하는 파일옵저버
     */
    private void setFileObserver()
    {
        Handler fileHandler = new Handler(Looper.getMainLooper())
        {
            @Override
            public void handleMessage(Message msg)
            {
                String path = EDIT_PATH+msg.getData().getString("path");
                Log.e(TAG, path);
                int state = msg.getData().getInt("state");
                if (!MainActivity.isForeground)
                {
                    Log.e(TAG, "Floating Button Functions");
                    uriForOCR = Uri.fromFile(new File(path));
                    startOCR();
                }
                else
                    Log.e(TAG, "Mainactivity Functions");
                Log.e(TAG, "파일생성 감지: "+path);
            }
        };
        mObserver = new ScreenshotObserver(Utils.EDIT_PATH, fileHandler);
        mObserver.startWatching();
        Log.e(TAG,"FileObserver started watching");
    }

    /**
     * 스크린샷, 크롭 파일 에 대한 브로드 캐스드를 수신할 동적 브로드캐스트 리시버
     */
    private BroadcastReceiver fileCreatedReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, final Intent intent)
        {
            if (mReceiverRegistered)
            {
                /**
                 * 스크린샷
                 */
                if (intent.getAction().equalsIgnoreCase(ACTION_SCREENSHOT))
                {
                    String path = intent.getStringExtra("path");
                    Log.e(TAG, "onReceive: " + "Screenshot Path =  "+path);
                    Utils.startEditor(path, null);
                }
                /**
                 * 크롭
                 */
                else if (intent.getAction().equalsIgnoreCase(ACTION_CROP))
                {
                    new Thread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            Bitmap origin = null;
                            RectF cropRect = intent.getParcelableExtra("croprect");
                            try
                            {
                                Thread.sleep(50);
                            } catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                            try
                            {
                                origin = Picasso
                                        .with(getApplicationContext())
                                        .load(Uri.fromFile(new File(intent.getStringExtra("path"))))
                                        .memoryPolicy(MemoryPolicy.NO_CACHE, MemoryPolicy.NO_STORE)
                                        .get();
                            } catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                            if (origin!=null)
                            {
                                int h = MainActivity.statusbarHeight;
                                Bitmap cropped = Bitmap.createBitmap(origin, (int)cropRect.left+5, ((int)cropRect.top)+h-3, (int)cropRect.width(), ((int)cropRect.height()));
                                Log.e(TAG,cropRect.toString());
                                Utils.saveScreenShot(cropped, EDIT_PATH);
                            }
                            else
                                Log.e(TAG, "Cannot read original Bitmap");
                        }
                    }).start();

                }
            }
        }
    };

    private synchronized void unRegisterfileCreatedReceiver()
    {
        if (mReceiverRegistered)
        {
            Log.e(TAG, "unRegisterfileCreatedReceiver " + fileCreatedReceiver);
            unregisterReceiver(fileCreatedReceiver);
            mReceiverRegistered = false;
        }
    }

    private synchronized void registerfileCreatedReceiver()
    {
        if (!mReceiverRegistered)
        {
            Log.e(TAG, "registerfileCreatedReceiver " + fileCreatedReceiver);
            final IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_SCREENSHOT);
            intentFilter.addAction(ACTION_CROP);
            registerReceiver(fileCreatedReceiver,intentFilter);
            mReceiverRegistered = true;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /**
         * startId == Service.START_STICKEY일 경우가 올바른 호출
         * 올바른 호출내에서 PendingIntent를 이용하여
         * Notification을 만들고, 서비스를 시작하며,
         * 서비스 관련 처리를 하여야 서비스가 종료되었을 때
         * 관리할 수 있다.
         */
        if (startId == Service.START_STICKY) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            PendingIntent pendingIntent = createPendingIntent();
            Notification notification = createNotification(pendingIntent);
            // Notification 시작
            startForeground(FOREGROUND_ID, notification);
            //파일옵저버 시작
            setFileObserver();
            //브로드캐스트 리시버 등록
            registerfileCreatedReceiver();
            //미디어프로젝션 권한요청 및 초기화
            final Intent pIntent = intent.getParcelableExtra("projection");
            final int resultCode = pIntent.getIntExtra("resultcode",0);
            mProjection = mProjectionManager.getMediaProjection(resultCode, pIntent);
            mImageReader = createVirtualDisplay(mProjection, getResources().getDisplayMetrics(), windowManager.getDefaultDisplay());
            //사운드풀 생성 및 오디오리소스 로딩
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            mSoundPool = new SoundPool.Builder().setAudioAttributes(audioAttributes).setMaxStreams(8).build();
            mSoundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                @Override
                public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                    soundLoaded = true;
                }
            });
            new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    soundID = mSoundPool.load(getBaseContext(),R.raw.shutter,1);
                }
            }).start();
            //UI 처리 시작
            handleStart();
            return super.onStartCommand(intent, flags, startId);
        } else {
                return Service.START_NOT_STICKY;
        }
    }

    /**
     * Bound Service가 아니므로 비워둔다.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);

        if(windowManager == null)
            return;

        /**
         * windowSize 재설정
         */
        windowManager.getDefaultDisplay().getSize(windowSize);

        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();

        attachSide(floatingParams.x);

        // Bar 제거
        if(isBarActive) {
            windowManager.removeView(floatingBar);
            floatingImage.setImageResource(R.drawable.floating_fold);
            isBarActive = false;
        }

        switch(config.orientation) {
            //
            case Configuration.ORIENTATION_LANDSCAPE:
                int position = floatingHead.getHeight() + getStatusBarHeight();
                if(floatingParams.y + position > windowSize.y) {
                    floatingParams.y = windowSize.y - position;
                    windowManager.updateViewLayout(floatingHead, floatingParams);
                }
                break;
            //
            case Configuration.ORIENTATION_PORTRAIT:
                break;
        }
    }
    /**
     * 생명주기 Destory 당시 붙였던 view들을 제거
     * + 리소스 해제작업
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceActive = false;
        if(floatingHead != null) {
            windowManager.removeView(floatingHead);
        }
        if(removeHead != null) {
            windowManager.removeView(removeHead);
        }
        if(isBarActive) {
            windowManager.removeView(floatingBar);
        }
        /**
         * SoundPool 할당 해제
         */
        mSoundPool.release();
        mSoundPool = null;
        soundID = 0;
        // MediaProjection 정지
        mProjection.stop();
        mImageReader.close();
        // 파일옵저버 정지
        mObserver.stopWatching();
        //브로드캐스트 리시버 등록해제
        unRegisterfileCreatedReceiver();
        //OCR서비스 언바인드
        unbindService(mConnection);

    }

    public static boolean isServiceActive() {
        return isServiceActive;
    }
    public static boolean isBarActive() { return isBarActive; }

    /**
     * 주의!!!!!!
     * PrefFragment에서 서비스를 종료시키기 위한 방법으로 이용되는 코드이므로,
     * 절대로 다른 곳에서 호출하지 말 것
     * 위험한 코드임
     */
    protected static Intent getCurrentFloatingService() {
        return new Intent(thisService, FloatingService.class);
    }

    /**
     * Crop Activity에서 호출바람
     * 주 : 1회 호출시 toggleHide, 2회 호출시 다시 show
     */
    protected static void toggleHide() {
        if(isHidden) {
            floatingHead.setVisibility(View.VISIBLE);
            windowManager.updateViewLayout(floatingHead, floatingHead.getLayoutParams());
            if(isBarActive) {
                floatingBar.setVisibility(View.VISIBLE);
                windowManager.updateViewLayout(floatingBar, floatingBar.getLayoutParams());
            }
            isHidden = false;
        } else {
            isHidden = true;
            floatingHead.setVisibility(View.GONE);
            windowManager.updateViewLayout(floatingHead, floatingHead.getLayoutParams());
            if(isBarActive) {
                floatingBar.setVisibility(View.GONE);
                windowManager.updateViewLayout(floatingBar, floatingBar.getLayoutParams());
            }
        }
    }

    protected static void setIsFixed(boolean option) {
        isFixed = option;
    }

    public static void set_cropmode(boolean _cropmode)
    {
        FloatingService._cropmode = _cropmode;
        if (!_cropmode)
            mCropView = null;
    }
}
