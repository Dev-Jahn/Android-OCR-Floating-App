package kr.ac.ssu.cse.jahn.textsnapper.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import kr.ac.ssu.cse.jahn.textsnapper.R;

/**
 * Created by ArchSlave on 2017-12-02.
 */

public class FloatingBarActivity extends AppCompatActivity {
    public static boolean active = false;
    private boolean isEng;

    RelativeLayout floatingLayout;
    ImageView screenshotImage;
    ImageView cropImage;
    ImageView languageImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floatingbar);

        floatingLayout = (RelativeLayout)findViewById(R.id.floatingBarLayout);
        screenshotImage = (ImageView)findViewById(R.id.floatingScreentshot);
        cropImage = (ImageView)findViewById(R.id.floatingCrop);
        languageImage = (ImageView)findViewById(R.id.floatingLanguage);

        languageImage.setOnClickListener(ocrLanguageChangeListener);
    }

    ImageView.OnClickListener ocrLanguageChangeListener = new ImageView.OnClickListener() {
        @Override
        public void onClick(View v) {
            SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = pref.edit();
            if(isEng) {
                editor.putString("ocrSelect", "한국어");
                isEng = false;
                languageImage.setImageResource(R.drawable.kor);
            } else {
                editor.putString("ocrSelect", "English");
                isEng = true;
                languageImage.setImageResource(R.drawable.eng);
            }
        }
    };



    @Override
    protected void onResume() {
        super.onResume();
        active = true;
        SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
        /**
         * 설정에 따라서 Image 변경
         */
        String ocrLanguage = pref.getString("ocrSelect", "English");
        if(ocrLanguage.equals("English")) {
            languageImage.setImageResource(R.drawable.eng);
            isEng = true;
        } else if(ocrLanguage.equals("한국어")) {
            languageImage.setImageResource(R.drawable.kor);
            isEng = false;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        active = false;
    }
}
