package grad_project.myapplication;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;

public class NormalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_normal);

        ActionBar actionBar = getSupportActionBar();
        actionBar.hide();

        RelativeLayout bt_back_layout = findViewById(R.id.bt_back_layout);
        bt_back_layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    public void onBack(View v) {
        if (v == findViewById(R.id.bt_back)) {
            finish();
        }
    }
}
