package kr.ac.ssu.cse.jahn.textsnapper.ui;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import kr.ac.ssu.cse.jahn.textsnapper.R;

import static android.content.ContentValues.TAG;

public class FloatingService extends Service {

    private static final int FOREGROUND_ID = 7816;
    private static final int REQUEST_PENDING = 9048;
    private static boolean isServiceActive;
    private static boolean isBarActive;
    private static boolean isEng;
    private static boolean canDraw;
    private boolean isLeft = false;

    private WindowManager windowManager;
    private RelativeLayout removeHead, floatingHead;
    private RelativeLayout floatingBar;
    private ImageView removeImage, floatingImage;
    private ImageView screenshotImage, cropImage, languageImage;
    private Point windowSize;

    @Override
    public void onCreate() {

        super.onCreate();
    }

    private void handleStart() {
        /**
         * WindowManager로 Floating Head 관리
         */
        LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        windowManager = (WindowManager)getSystemService(WINDOW_SERVICE);
        isBarActive = false;
        isServiceActive = true;
        canDraw = true;
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
        removeImage = (ImageView)removeHead.findViewById(R.id.removeImage);
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
        floatingParams.x = windowSize.x - (int)(floatingImage.getLayoutParams().width * 0.75);
        floatingParams.y = (int)(windowSize.y * 0.75);
        windowManager.addView(floatingHead, floatingParams);

        floatingHead.setOnTouchListener(new View.OnTouchListener() {
            long startTime = 0;
            long endTime = 0;
            boolean isLongClick = false;
            boolean isOnRemoveHead = false;
            int removeX = 0;
            int removeY = 0;
            int initX;
            int initY;
            int marginX;
            int marginY;
            int removeImageWidth;
            int removeImageHeight;
            //Handler
            Handler longHandler = new Handler();
            Runnable longRunnable = new Runnable() {
                @Override
                public void run() {
                    isLongClick = true;
                    removeHead.setVisibility(View.VISIBLE);
                    showFloatingRemove();
                }
            };
            /**
             * Floating Button Touch Event
             */
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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

                // 이동되는 좌표
                int afterX;
                int afterY;
                // Bar가 떠있거나 고정했을 경우 또는 애니메이션 진행중일 경우 움직이지 않는다.
                if (isBarActive == false && canDraw == true) {
                    switch (event.getAction()) {
                        // 롱클릭
                        case MotionEvent.ACTION_DOWN:
                            removeImageWidth = removeImage.getLayoutParams().width;
                            removeImageHeight = removeImage.getLayoutParams().height;
                            startTime = System.currentTimeMillis();
                            // 삭제하는 이미지가 얼마나 오랫동안 누르고 있어야 등장할 지 결정
                            longHandler.postDelayed(longRunnable, 500);

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
                            if (isLongClick) {
                                // removeHead의 위치를 수동으로 적어줘야 함. 수정 xxxxx
                                int removeLeftBound = windowSize.x / 2 - (int) (removeImageWidth * 1.5);
                                int removeRightBound = windowSize.x / 2 + (int) (removeImageWidth * 1.5);
                                int removeTopBound = windowSize.y - (int) (removeImageHeight * 1.5);

                                if ((currentX >= removeLeftBound && currentX <= removeRightBound)
                                        && currentY >= removeTopBound) {
                                    isOnRemoveHead = true;

                                    int removeX = (int) ((windowSize.x - (removeImageHeight * 1.5)) / 2);
                                    int removeY = (int) (windowSize.y - ((removeImageWidth * 1.5) + getStatusBarHeight()));

                                    Log.v("DEBUG!", "RemoveX : " + removeImageHeight + " RemoveY : " + removeImageWidth);

                                    if (removeImage.getLayoutParams().height == removeImageHeight) {
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
                                } else {
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

                            if (isOnRemoveHead) {
                                stopService(new Intent(FloatingService.this, FloatingService.class));
                                isOnRemoveHead = false;
                                break;
                            }

                            int diffX = currentX - initX;
                            int diffY = currentY - initY;
                            // X, Y 이동값이 적은 경우는 FloatingBar를 띄우는 액션으로 본다
                            if (Math.abs(diffX) < 5 && Math.abs(diffY) < 5) {
                                endTime = System.currentTimeMillis();
                                // 물론 클릭했던 시간이 적은 경우에만
                                if ((endTime - startTime) < 500) {
                                    showFloatingBar();
                                }
                            }
                            afterY = marginY + diffY;

                            newFloatingParams.y = afterY;
                            // 만약 X 이동값이 큰 경우, 벽에 붙인다.
                            if(Math.abs(diffX) >= 5){
                                attachSide(currentX);
                             }

                            isOnRemoveHead = false;

                            break;
                    }
                    return true;
                } else {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            removeImageWidth = removeImage.getLayoutParams().width;
                            removeImageHeight = removeImage.getLayoutParams().height;
                            startTime = System.currentTimeMillis();
                            break;
                        case MotionEvent.ACTION_UP:
                            endTime = System.currentTimeMillis();
                            if ((endTime - startTime) < 500) {
                                // click 했을 때 리스트 띄우는 코드
                                showFloatingBar();
                            }
                            break;
                    }
                    return true;
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

    private void moveToLeft(final int currentX){
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
    private  void moveToRight(final int currentX){
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
            isLeft = true;
            moveToLeft(currentX);
        } else {
            isLeft = false;
            moveToRight(currentX);
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

        Log.v("DEBUG", "EXECUTED! x : " + removeParams.x + " y : " + removeParams.y);
        windowManager.updateViewLayout(removeHead, removeParams);
    }
    /**
     * Click을 진행했을 때
     * Floating Bar를 보여줌
     */
    private void showFloatingBar() {
        if(canDraw == true) {
            if (floatingBar != null && isBarActive) {
                windowManager.removeView(floatingBar);
                floatingImage.setImageResource(R.drawable.floating_image);
                isBarActive = false;
            } else {
                LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
                WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                // 왼쪽 벽에 붙어있는 경우
                if (floatingParams.x < windowSize.x / 2) {
                    floatingBar = (RelativeLayout) inflater.inflate(R.layout.activity_floatingbar_left, null);

                    screenshotImage = (ImageView) floatingBar.findViewById(R.id.floatingScreentshotLeft);
                    cropImage = (ImageView) floatingBar.findViewById(R.id.floatingCropLeft);
                    languageImage = (ImageView) floatingBar.findViewById(R.id.floatingLanguageLeft);
                    floatingImage.setImageResource(R.drawable.floating_fold_left);
                }
                // 오른쪽 벽에 붙어있는 경우
                else {
                    floatingBar = (RelativeLayout) inflater.inflate(R.layout.activity_floatingbar_right, null);

                    screenshotImage = (ImageView) floatingBar.findViewById(R.id.floatingScreentshotRight);
                    cropImage = (ImageView) floatingBar.findViewById(R.id.floatingCropRight);
                    languageImage = (ImageView) floatingBar.findViewById(R.id.floatingLanguageRight);
                    floatingImage.setImageResource(R.drawable.floating_fold_right);
                }
                /**
                 * Bar Layout Ocr Language 버튼 환경설정과 연동
                 */
                SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                String ocrLanguage = pref.getString("ocrSelect", "English");
                if (ocrLanguage.equals("English")) {
                    languageImage.setImageResource(R.drawable.eng);
                    isEng = true;
                } else if (ocrLanguage.equals("한국어")) {
                    languageImage.setImageResource(R.drawable.kor);
                    isEng = false;
                }

                screenshotImage.setOnTouchListener(imageTouchEventListener);
                screenshotImage.setOnClickListener(imageClickEventListener);
                cropImage.setOnTouchListener(imageTouchEventListener);
                cropImage.setOnClickListener(imageClickEventListener);
                languageImage.setOnTouchListener(imageTouchEventListener);

                languageImage.setOnClickListener(new ImageView.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                        SharedPreferences.Editor editor = pref.edit();
                        if (isEng) {
                            /**
                             * putString 부분 설정 연동 버그를 고쳐야함. 주의할 것.
                             */
                            editor.putString("ocrSelect", "한국어");
                            isEng = false;
                            languageImage.setImageResource(R.drawable.kor);
                        } else {
                            editor.putString("ocrSelect", "English");
                            isEng = true;
                            languageImage.setImageResource(R.drawable.eng);
                        }
                    }
                });

                WindowManager.LayoutParams barParams = new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_PHONE,
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

                // 왼쪽 벽에 붙어있는 경우 애니메이션
                if (floatingParams.x < windowSize.x / 2) {
                    canDraw = false;
                    new CountDownTimer(500, 5) {
                        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                        WindowManager.LayoutParams barParams = (WindowManager.LayoutParams) floatingBar.getLayoutParams();
                        WindowManager.LayoutParams mParams = barParams;

                        public void onTick(long t) {
                            int step = (500 - (int) t) / 7;
                            int gap = (int) (floatingImage.getWidth() * 0.25) + (int) (screenshotImage.getLayoutParams().width * 3.65);
                            int accValue = gap / step;

                            mParams.x = barParams.x + accValue;

                            if (mParams.x > floatingParams.x + (int) (floatingImage.getWidth() * 0.25))
                                mParams.x = floatingParams.x + (int) (floatingImage.getWidth() * 0.25);

                            windowManager.updateViewLayout(floatingBar, mParams);
                        }

                        public void onFinish() {
                            mParams.x = floatingParams.x + (int) (floatingImage.getWidth() * 0.25);
                            windowManager.updateViewLayout(floatingBar, mParams);
                            canDraw = true;
                        }
                    }.start();
                }

                // 오른쪽 벽에 붙어있는 경우 애니메이션
                else {
                    canDraw = false;
                    new CountDownTimer(500, 5) {
                        WindowManager.LayoutParams floatingParams = (WindowManager.LayoutParams) floatingHead.getLayoutParams();
                        WindowManager.LayoutParams barParams = (WindowManager.LayoutParams) floatingBar.getLayoutParams();
                        WindowManager.LayoutParams mParams = barParams;

                        public void onTick(long t) {
                            int step = (500 - (int) t) / 7;
                            int gap = -2 * (int) (screenshotImage.getLayoutParams().width * 3.65);
                            int accValue = gap / step;

                            mParams.x = barParams.x + accValue;

                            if (mParams.x < floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65))
                                mParams.x = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);

                            windowManager.updateViewLayout(floatingBar, mParams);
                        }

                        public void onFinish() {
                            mParams.x = floatingParams.x - (int) (screenshotImage.getLayoutParams().width * 3.65);
                            windowManager.updateViewLayout(floatingBar, mParams);
                            canDraw = true;
                        }
                    }.start();
                }

                isBarActive = true;
            }
        }
    }

    /**
     * 버튼을 눌렀을 때 선택되었음을 보여주도록
     */
    ImageView.OnTouchListener imageTouchEventListener = new ImageView.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    ImageView view = (ImageView) v;
                    // overlay 색상 설정
                    view.getDrawable().setColorFilter(Color.GRAY, PorterDuff.Mode.MULTIPLY);
                    view.invalidate();
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL: {
                    ImageView view = (ImageView) v;
                    // overlay 색상 제거
                    view.getDrawable().clearColorFilter();
                    view.invalidate();
                    break;
                }
            }
            return false;
        }
    };

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
                /*Intent intent = new Intent(getApplicationContext(), TestActivity.class);
                intent.putExtra("screenshot", screenBitmap);
                startActivity(intent);*/
                break;
            case R.id.floatingCropLeft:
            case R.id.floatingCropRight:
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
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(intent)
                .build();
    }

    /**
     * 스크린샷으로 저장소에 파일이 생성되는 것을 감지하는 FileObserver를 시작
     * ㅇㄹㄴㅇㄹ
     */
    private void setFileObserver()
    {
        String path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() + "/Screenshots/";

        FileObserver fileObserver = new FileObserver(path, FileObserver.CREATE) {
            @Override
            public void onEvent(int event, String path) {
                Toast.makeText(FloatingService.this, "스크린샷저장이 감지되었습니다", Toast.LENGTH_SHORT).show();
            }
        };
        Log.e(TAG,"FileObserver started watching");
        fileObserver.startWatching();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent pendingIntent = createPendingIntent();
        Notification notification = createNotification(pendingIntent);
        // Notification 시작
        startForeground(FOREGROUND_ID, notification);

        setFileObserver();
        if (startId == Service.START_STICKY) {
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


    /**
     * 생명주기 Destory 당시 붙였던 view들을 제거
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceActive = false;
        if(floatingHead != null){
            windowManager.removeView(floatingHead);
        }

        if(removeHead != null){
            windowManager.removeView(removeHead);
        }
    }

    public static boolean isServiceActive() {
        return isServiceActive;
    }

}
