package com.Simoorg.EasyTrackManipulator;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.VideoView;

public class SplashScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash_screen);
        final Intent EQIntent = new Intent(SplashScreenActivity.this, MainActivity.class);

        final VideoView vv = findViewById(R.id.videoView);
        String path = "android.resource://" + getPackageName() + "/" + R.raw.superpowered;
        vv.setVideoURI(Uri.parse(path));
        vv.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                startActivity(EQIntent);
                finish();
            }
        });
        vv.start();
    }
}

