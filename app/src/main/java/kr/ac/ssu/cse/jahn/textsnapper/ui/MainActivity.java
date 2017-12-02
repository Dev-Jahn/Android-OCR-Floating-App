package kr.ac.ssu.cse.jahn.textsnapper.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.NavigationView;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import kr.ac.ssu.cse.jahn.textsnapper.R;


public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    ActionBarDrawerToggle toggle;
    ViewPager viewPager;


    // Floating Action Button Overlay를 위한 요청 코드
    public static int PERMISSION_REQUEST_CODE_FLOATING_BUTTON = 1234;
    public static int PERMISSION_REQUEST_CODE_FLOATING_BAR = 5678;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        viewPager = (ViewPager)findViewById(R.id.viewPager);
        viewPager.setAdapter(new PagerAdapter(getSupportFragmentManager()));

        TabLayout tabLayout = (TabLayout)findViewById(R.id.tabLayout);
        tabLayout.setupWithViewPager(viewPager);
        tabLayout.addOnTabSelectedListener(tabSelectedListener);
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
                    // 문제점 1. 리소스에 따라서 반응하는 형식이 다름..
                    view.getDrawable().setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_ATOP);
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
            if(Utils.canDrawOverlays(MainActivity.this)) {
                startFloatingHead();
            } else{
                requestPermission(PERMISSION_REQUEST_CODE_FLOATING_BUTTON);
            }
        }
    };

    ImageView.OnClickListener floatingBarEventListener = new ImageView.OnClickListener() {

        @Override
        public void onClick(View v) {
            if(Utils.canDrawOverlays(MainActivity.this)) {

            } else {
              requestPermission(PERMISSION_REQUEST_CODE_FLOATING_BAR);
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

    //     * FloatingHead Service가 완성될 시 주석 해제
    private void startFloatingHead() {
        Intent intent = new Intent(getApplicationContext(), FloatingService.class);
        startService(intent);
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

        if (requestCode == PERMISSION_REQUEST_CODE_FLOATING_BUTTON) {
            if (!Utils.canDrawOverlays(this)) {
                needPermissionDialog(requestCode);
            } else {
                startFloatingHead();
            }
        }
    }
}