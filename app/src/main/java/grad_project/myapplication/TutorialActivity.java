package grad_project.myapplication;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.rd.PageIndicatorView;

public class TutorialActivity extends AppCompatActivity {
    private SharedPreferences infoData;
    private ViewPager viewPager;
    private ViewPagerAdapter pagerAdapter ;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tutorial_main);

        viewPager = (ViewPager)findViewById(R.id.viewPager) ;
        pagerAdapter = new ViewPagerAdapter(this) ;
        viewPager.setAdapter(pagerAdapter);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            throw new NullPointerException("Null ActionBar");
        } else {
            actionBar.hide();
        }
    }

    class ViewPagerAdapter extends PagerAdapter {
        // LayoutInflater 서비스 사용을 위한 Context 참조 저장.
        private ImageButton endBut;
        private ImageView img;
        private Context context = null ;

        // Context를 전달받아 저장하는 생성자 추가.
        public ViewPagerAdapter(Context context) {
            super();
            this.context = context ;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View view = null ;

            if (context != null) {
                // LayoutInflater를 통해 각 xml파일을 뷰로 생성.
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.tutorial_page, container, false);

                endBut = (ImageButton)view.findViewById(R.id.end_button);
                img = (ImageView) view.findViewById(R.id.tutorial_image) ;
                if(position == 0) {
                    img.setImageResource(R.drawable.tutorial1);
                    endBut.setVisibility(View.GONE);
                }
                else if(position == 1) {
                    img.setImageResource(R.drawable.tutorial2);
                    endBut.setVisibility(View.GONE);
                }
                else {
                    img.setImageResource(R.drawable.tutorial3);
                    endBut.setVisibility(View.VISIBLE);
                    endBut.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent resultIntent = new Intent();
                            setResult(RESULT_OK, resultIntent);
                            finish();
                        }
                    });
                }
            }

            // 뷰페이저에 추가.
            container.addView(view) ;

            return view ;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            // 뷰페이저에서 삭제.
            container.removeView((View) object);
        }

        @Override
        public int getCount() {
            // 전체 페이지 수는 3개로 고정.
            return 3;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == (View)object;
        }
    }
}
