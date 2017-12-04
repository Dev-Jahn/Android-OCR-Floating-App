package kr.ac.ssu.cse.jahn.textsnapper.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.hardware.display.DisplayManager;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import kr.ac.ssu.cse.jahn.textsnapper.R;
import kr.ac.ssu.cse.jahn.textsnapper.ocr.ImageSource;
import kr.ac.ssu.cse.jahn.textsnapper.util.Utils;

import static kr.ac.ssu.cse.jahn.textsnapper.util.Utils.DATA_PATH;
import static kr.ac.ssu.cse.jahn.textsnapper.util.Utils.copyTessdata;


public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {


    public static final int REQUEST_CAMERA = 100;
    public static final int REQUEST_GALLERY = 101;
    public static final int REQUEST_CROP = 102;
    public static final int REQUEST_MEDIA_PROJECTION = 103;
    public static final int REQUEST_PERMISSION_OVERLAY = 104;

    // Floating Action Button Overlay를 위한 요청 코드
    private static final String TAG = "Mainactivity";
    private static final String[] LANGS = {"eng", "kor"};
    protected String mPhotoDirPath = DATA_PATH + "photo/";
    protected String mPhotoPath;

    protected MediaProjectionManager mProjectionManager;
    protected MediaProjection mProjection;
    protected ImageReader mImageReader;
    protected Display mDisplay;
    protected Intent mProjectionIntent;
    protected int mDensity;
    protected int mWidth;
    protected int mHeight;
    private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

    ActionBarDrawerToggle toggle;
    ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //권한요청
        Utils.request(this, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,Manifest.permission.CAMERA);
        mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mProjectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);

        //디렉토리 생성
        Utils.makeAppDir();
        //파일복사
        copyTessdata(getAssets(),LANGS);


        /**
         * DrawerLayout ==> 좌상단 메뉴버튼 클릭하면 나오는 버튼을 위한 코드
         * toggle.syncState()를 해야 메뉴 버튼이 추가되니 toggle.syncState()까지
         * 코드를 수정하지 말 것!
         **/
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.mainDrawer);
        toggle = new ActionBarDrawerToggle(
                this, drawer, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);
            }
        };
        drawer.addDrawerListener(toggle);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.navi_view);
        navigationView.setNavigationItemSelectedListener(this);


        /**
         * 이하는 Main Activity 버튼에 관한 이벤트 처리
         **/
        /**
        Button button = (Button)findViewById(R.id.widget);
        button.setOnClickListener(floatingButtonEventListener);
         */

        /**
         * 각 버튼에 대해 클릭되었을 때 나타나는 애니메이션 리스너를 달아준다
         */
        ImageView imageCamera = (ImageView)findViewById(R.id.imageCamera);
        ImageView imageGallery = (ImageView)findViewById(R.id.imageGallery);
        ImageView imageWidget = (ImageView)findViewById(R.id.imageWidget);

        imageCamera.setOnTouchListener(imageClickEventListener);
        imageGallery.setOnTouchListener(imageClickEventListener);
        imageWidget.setOnTouchListener(imageClickEventListener);
        imageWidget.setOnClickListener(floatingButtonEventListener);

        imageGallery.setOnClickListener(new ButtonClickListener());
        imageCamera.setOnClickListener(new ButtonClickListener());

        viewPager = (ViewPager) findViewById(R.id.viewPager);
        viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));

        TabLayout tabLayout = (TabLayout)findViewById(R.id.tabLayout);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.addOnTabSelectedListener(tabSelectedListener);
    }
    public class ButtonClickListener implements View.OnClickListener
    {
        public void onClick(View view)
        {
            switch (view.getId())
            {
            case R.id.imageGallery:
                fromGallery();
                break;
            case R.id.imageCamera:
                Log.e(TAG,"Camera Button");
                //fromCamera();
                break;
            }
        }
    }
    private void fromGallery()
    {
        Log.e("TAG", "fromGallery()");
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, REQUEST_GALLERY);
    }

    private void fromCamera()
    {
        File photoFile = createImageFile();
        //파일프로바이더 사용
        Uri outputFileUri = FileProvider.getUriForFile(getApplicationContext(), getPackageName()+".provider",photoFile);

        final Intent takePic = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        takePic.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
        takePic.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (takePic.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePic, REQUEST_CAMERA);
        }
    }
    private File createImageFile()
    {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = new File(mPhotoDirPath);
        File image;
        try
        {
            image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );
        }
        catch (IOException e)
        {
            image=null;
            Log.e("TAG","Can't create TempFile");
        }
        // Save a file: path for use with ACTION_VIEW intents
        mPhotoPath = image.getAbsolutePath();
        return image;
    }
    /**
     * 버튼을 눌렀을 때 선택되었음을 보여주도록
     */
    ImageView.OnTouchListener imageClickEventListener = new ImageView.OnTouchListener() {
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
     * Floating Button Overlay!
     * 완성된 코드이므로 더 이상 수정할 필요 없음
     */
    ImageView.OnClickListener floatingButtonEventListener = new ImageView.OnClickListener() {

        @Override
        public void onClick(View v) {
            SharedPreferences pref = getPreferences(MODE_PRIVATE);
            boolean canStart = pref.getBoolean("floatingButtonUse", true);
            if(canStart) {
                if (Utils.canDrawOverlays(MainActivity.this)) {
                    if (FloatingService.isServiceActive() == false)
                        startFloatingHead();
                } else {
                    requestPermission(REQUEST_PERMISSION_OVERLAY);
                }
            } else {
                Toast.makeText(getApplicationContext(), "Floating Button Option이 비활성화되어 있습니다.", Toast.LENGTH_LONG).show();
            }
        }
    };

    /**
     * TabLayout 리스너
     */
    TabLayout.OnTabSelectedListener tabSelectedListener = new TabLayout.OnTabSelectedListener() {
        @Override
        public void onTabSelected(TabLayout.Tab tab) {
            viewPager.setCurrentItem(tab.getPosition());
        }
        @Override
        public void onTabUnselected(TabLayout.Tab tab) { }

        @Override
        public void onTabReselected(TabLayout.Tab tab) { }
    };

    /**
     * Resumed 상태에서 SharedPreferences 복구
     * Preference 상태 변화에 따른 변화 코드를 작성할 때는
     * PrefFragment.java 에서 작성할 것
     */
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences setRefer = PreferenceManager
                .getDefaultSharedPreferences(this);
    }

    /**
     * 우측 상단의 메뉴 버튼 추가
     **/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    /**
     * 우측 상단 메뉴 버튼이 눌렸을 때의 이벤트
     **/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int curId = item.getItemId();
        Intent intent;
        if (toggle.onOptionsItemSelected(item))
            return true;
        switch (curId) {
            case R.id.menuConfig:
                intent = new Intent(this, ConfigActivity.class);
                startActivity(intent);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Navigation Button이 눌렸을 때의 이벤트
     **/
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int curId = item.getItemId();
        Intent intent;

        switch (curId) {
            case R.id.navigation_config:
                intent = new Intent(this, ConfigActivity.class);
                startActivity(intent);
                break;
            case R.id.navigation_guide:
                break;
            case R.id.navigation_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
            case R.id.navigation_favorite:
                break;
        }
        return true;
    }

    private void startFloatingHead() {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean canUseFloating = pref.getBoolean("floatingButtonUse", true);
        /**
         * 서비스가 버튼을 클릭할 때 마다 실행되는 문제 해결
         */
        if(!FloatingService.isServiceActive() && canUseFloating) {
            Intent intent = new Intent(getApplicationContext(), FloatingService.class);
            intent.putExtra("projection", mProjectionIntent);
            startService(intent);
        }
        /**
         * Option 연동
         */
        if(!canUseFloating) {
            Toast.makeText(getApplicationContext(), "FloatingButton 옵션이 비활성화되어 있습니다.", Toast.LENGTH_LONG).show();
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        if (grantResults.length <= 0)
            return;
        for(int i = 0; i<grantResults.length; i++)
        {
            if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                Log.v("TAG",permissions[i]+"권한 승인");
            else
                Log.v("TAG",permissions[i]+"권한 거부");
        }
    }
    /**
     * Floating Button을 위해서는 Overlay Permission을 반드시 얻어야 함
     **/
    private void requestPermission(int requestCode){
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, requestCode);
    }

    private void needPermissionDialog(final int requestCode){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("기능을 이용하시기 위해서는\n권한을 부여받아야 합니다.");
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestPermission(requestCode);
                    }
                });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        builder.setCancelable(true);
        builder.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Bitmap imageBitmap;
        Uri photoUri;
        Log.i(TAG, "resultCode: " + resultCode);

        if (resultCode == RESULT_OK)
        {
            photoUri = data.getData();
            switch (requestCode)
            {
            case REQUEST_PERMISSION_OVERLAY:
                if (!Utils.canDrawOverlays(this)) {
                    needPermissionDialog(requestCode);
                } else {
                    startFloatingHead();
                }
                break;
            case REQUEST_MEDIA_PROJECTION:
                mProjectionIntent = data;
                data.putExtra("resultcode", resultCode);
                break;
            case REQUEST_GALLERY:
                Intent editIntent = new Intent(Intent.ACTION_EDIT);
                editIntent.setDataAndType(photoUri, "image/*");
                editIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivityForResult(Intent.createChooser(editIntent, null),REQUEST_CROP);

                //mImageView.setImageBitmap(imageBitmap);
                break;
            case REQUEST_CAMERA:
                imageBitmap = BitmapFactory.decodeFile(mPhotoPath);
                //onPhotoTaken();
                break;
            case REQUEST_CROP:
                Log.e("TAG","onActivityResult():REQUEST_CROP");
                Intent result = new Intent(this, TestActivity.class);
                result.setDataAndType(photoUri,"image/*");
                result.putExtra("imagesource", ImageSource.CROP);
                startActivity(result);

                break;
            }
        }
        else
        {
            Log.v(TAG, "User cancelled");
        }
    }
}