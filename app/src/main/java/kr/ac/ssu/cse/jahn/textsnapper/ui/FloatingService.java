package kr.ac.ssu.cse.jahn.textsnapper.ui;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import kr.ac.ssu.cse.jahn.textsnapper.R;

public class FloatingService extends Service {

    private static final int FOREGROUND_ID = 7816;
    private static final int REQUEST_PENDING = 9048;

    private WindowManager windowManager;
    private RelativeLayout removeHead, floatingHead;
    private ImageView removeImage, floatingImage;
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

        /**
         * Remove Head inflate 부분
         * TYPE_PHONE Flag가 deprecated라고 하여 삭제할 생각은 XXXXXX
         * TYPE_PHONE Flag를 전달해야 이벤트를 처리가능하게 Overlay 가능
         */
        removeHead = (RelativeLayout) inflater.inflate(R.layout.activity_remove, null);
        WindowManager.LayoutParams removeParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
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
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);


        floatingParams.gravity = Gravity.TOP | Gravity.LEFT;
        /**
         * Floating Button의 초기 위치 설정 코드
         * 이후 사양이 변경되면 이 곳을 수정
         */
        floatingParams.x = windowSize.x - 100;
        floatingParams.y = windowSize.y * 3 / 4;
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
                WindowManager.LayoutParams newFloatingParams = (WindowManager.LayoutParams)floatingHead.getLayoutParams();

                int leftMax = -floatingImage.getWidth() / 2;
                int rightMax = windowSize.x - floatingImage.getWidth() / 2;
                int topMax = 0;
                int bottomMax = windowSize.y - floatingImage.getHeight();
                /**
                 * 지속적으로 현재 위치를 업데이트
                 * RawX, Y를 받아올 것!
                 * getX, getY를 그냥 받아오면 상대좌표라 계속 흔들림
                 */
                int currentX = (int)event.getRawX();
                int currentY = (int)event.getRawY();

                // 이동되는 좌표
                int afterX;
                int afterY;

                switch(event.getAction()) {
                    // 롱클릭
                    case MotionEvent.ACTION_DOWN:
                        removeImageWidth = removeImage.getLayoutParams().width;
                        removeImageHeight = removeImage.getLayoutParams().height;
                        startTime = System.currentTimeMillis();
                        // 삭제하는 이미지가 얼마나 오랫동안 누르고 있어야 등장할 지 결정
                        longHandler.postDelayed(longRunnable, 500);
                        //longHandler.post(longRunnable);

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

                        if(afterX < leftMax)
                            afterX = leftMax;
                        if(afterX > rightMax)
                            afterX = rightMax;
                        if(afterY < topMax)
                            afterY = topMax;
                        if(afterY > bottomMax)
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

                        if(isOnRemoveHead) {
                            stopService(new Intent(FloatingService.this, FloatingService.class));
                            isOnRemoveHead = false;
                            break;
                        }

                        int diffX = currentX - initX;
                        int diffY = currentY - initY;

                        if(Math.abs(diffX) < 5 && Math.abs(diffY) < 5) {
                            endTime = System.currentTimeMillis();
                            if((endTime - startTime) < 300) {
                                // click 했을 때 리스트 띄우는 코드
                            }
                        }

                        afterY = marginY + diffY;

                        int BarHeight = getStatusBarHeight();

                        newFloatingParams.y = afterY;
                        isOnRemoveHead = false;

                        break;
                }
                return true;
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

        Log.v("DEBUG", "EXECUTED! x : " + removeParams.width + " y : " + removeParams.height);
        windowManager.updateViewLayout(removeHead, removeParams);
    }

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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        PendingIntent pendingIntent = createPendingIntent();
        Notification notification = createNotification(pendingIntent);
        // Notification 시작
        startForeground(FOREGROUND_ID, notification);
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
        if(floatingHead != null){
            windowManager.removeView(floatingHead);
        }

        if(removeHead != null){
            windowManager.removeView(removeHead);
        }
    }

}
