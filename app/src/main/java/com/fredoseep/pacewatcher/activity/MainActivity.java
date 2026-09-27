package com.fredoseep.pacewatcher.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.fredoseep.pacewatcher.R;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        Button paceManButton = findViewById(R.id.paceman_button);
        Button rankedButton = findViewById(R.id.ranked_button);

        paceManButton.setOnClickListener(view->{
            Log.d(TAG,"paceman button clicked");
            Intent intent = new Intent(MainActivity.this, PacemanWatcherActivity.class);
            startActivity(intent);
        });
        rankedButton.setOnClickListener(view->{
            Log.d(TAG,"ranked button clicked");
            Intent intent = new Intent(MainActivity.this, RankedSelectActivity.class);
            startActivity(intent);
        });
    }
}