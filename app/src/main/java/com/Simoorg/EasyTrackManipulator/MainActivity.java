package com.Simoorg.EasyTrackManipulator;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.net.URISyntaxException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import it.beppi.knoblibrary.Knob;

public class MainActivity extends AppCompatActivity {

    private static float pitchValue, tempoValue;
    private Intent EQIntent;
    private int PERMISSION_REQUEST_RESULT;
    private ScheduledExecutorService mExecutor;
    private Runnable mSeekBarPositionUpdateTask;
    private long PLAYBACK_POSITION_REFRESH_INTERVAL_MS = 200;
    private boolean isUserSeeking = false;
    private String totalTimeText;
    private String destPath;
    private boolean playing = false;
    private boolean recording = false;
    private boolean vocalRemoved = false;
    private boolean dontPause = false;
    private boolean fromET = false;
    private boolean fromKnob = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        EQIntent = new Intent(MainActivity.this, EQActivity.class);

        PrepareAudioEngine();
        PrepareKnobsAndFields();
        PrepareSeekBar();
        SetParameters();
    }

    //region Preparation methods

    public void PrepareKnobsAndFields() {
        final EditText pitchET = findViewById(R.id.PitchED);
        final Knob pitchKnob = findViewById(R.id.PitchKnob);
        pitchKnob.setOnStateChanged(new Knob.OnStateChanged() {
            @Override
            public void onState(int state) {
                if (!fromET) {
                    fromKnob = true;
                    pitchValue = state - pitchKnob.getNumberOfStates() / 2;
                    pitchET.setText(Float.toString(pitchValue));
                    SetParameters();
                } else fromET = false;

            }
        });

        pitchET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString().isEmpty() || s.toString().equals("-") || s.toString().equals("."))
                    return;
                if (!fromKnob) {
                    float value = Float.parseFloat(s.toString());
                    if (value < -13.0)
                        pitchET.setText("-13.0");
                    else if (value > 12.0)
                        pitchET.setText("12.0");
                    else {
                        fromET = true;
                        pitchKnob.setState(Math.round(value + pitchKnob.getNumberOfStates() / 2));
                        pitchValue = value;
                        SetParameters();
                    }
                } else fromKnob = false;
            }
        });

        final EditText tempoET = findViewById(R.id.TempoED);
        final Knob tempoKnob = findViewById(R.id.TempoKnob);
        tempoKnob.setOnStateChanged(new Knob.OnStateChanged() {
            @Override
            public void onState(int state) {
                if (!fromET) {
                    tempoValue = state - tempoKnob.getNumberOfStates() / 2;
                    tempoET.setText(Float.toString(tempoValue*5));
                    SetParameters();
                } else fromET = false;

            }
        });

        tempoET.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if(s.toString().isEmpty() || s.toString().equals("-") || s.toString().equals("."))
                    return;
                if (!fromKnob) {
                    float value = Float.parseFloat(s.toString());
                    value /= 5;
                    if (value < -55)
                        tempoET.setText("-55.0");
                    else if (value > 50)
                        tempoET.setText("50.0");
                    else {
                        fromET = true;
                        tempoKnob.setState(Math.round(value + tempoKnob.getNumberOfStates() / 2));
                        tempoValue = value;
                        SetParameters();
                    }
                } else fromKnob = false;

            }
        });


    }

    public void SetParameters() {
        SetPitchAndRate(pitchValue, tempoValue);
    }

    public void PrepareAudioEngine() {
        // Get the device's sample rate and buffer size to enable
        // low-latency Android audio output, if available.
        String samplerateString = null, buffersizeString = null;
        AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
            buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
        }
        if (samplerateString == null) samplerateString = "48000";
        if (buffersizeString == null) buffersizeString = "480";
        int samplerate = Integer.parseInt(samplerateString);
        int buffersize = Integer.parseInt(buffersizeString);

        String tempPath = getCacheDir().getAbsolutePath() + "/temp.wav";
        destPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/MyRecording";

        System.loadLibrary("AudioProcessing");    // load native library
        StartAudio(samplerate, buffersize, tempPath, destPath);     // start audio engine
    }

    public void PrepareSeekBar() {
        final SeekBar sb = findViewById(R.id.seekBar);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    SetPosition((double) progress);
                    setCurrTimeText(progress);
                } else
                    setCurrTimeText(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
            }
        });
    }

    //endregion

    //region OnClick methods
    public void PlayPauseClick(View button) {
        PlayPause();
    }

    public void PlayPause() {
        TogglePlayback();
        playing = !playing;

        if (playing) {
            startUpdatingCallbackWithPosition();
        } else {
            stopUpdatingCallbackWithPosition();
        }
        Button b = findViewById(R.id.butPlayPause);
        b.setText(playing ? getResources().getString(R.string.pause) : getResources().getString(R.string.play));
    }

    public void RecordClick(View button) {
        Record();
    }

    public void Record() {
        try {
            ToggleRecording();
            recording = !recording;
            Button b = findViewById(R.id.butRec);
            b.setText(recording ? getResources().getString(R.string.stop_record) : getResources().getString(R.string.record));
            if (!recording) {
                Toast.makeText(this, getResources().getString(R.string.record_saved) + destPath, Toast.LENGTH_LONG).show();
            }
        }
        catch (Exception e){
            Toast.makeText(this, getResources().getString(R.string.cant_record), Toast.LENGTH_LONG).show();
        }
    }

    public void RemoveVocalClick(View button) {
        ToggleRemoveVocal();
        vocalRemoved = !vocalRemoved;
        Button b = findViewById(R.id.butVocal);
        b.setText(vocalRemoved ? getResources().getString(R.string.restore_vocal) : getResources().getString(R.string.remove_vocal));
    }

    public void ForwardClick(View button) {
        MoveForward();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        updateProgressCallbackTask();
    }

    public void BackwardClick(View button) {
        MoveBackward();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        updateProgressCallbackTask();
    }

    public void SetSongClick(View button) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_RESULT);
        }
        Intent intent;
        intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        startActivityForResult(Intent.createChooser(intent, getString(R.string.select_audio_file_title)), 1);
    }

    public void EQClick(View button) {
        dontPause = true;
        startActivity(EQIntent);
    }
    //endregion

    //region Song select methods
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data == null)
            return;

        try {
            Uri uri = data.getData();

            String str = null;
            str = getFilePath(this, uri);

            if (str == null || str.equals("")) {
                str = uri.getPath().replaceAll(".*//", "/");
            }


            OpenFileFromPath(str);         // open audio file from APK
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            setSeekBarNewSong();
            String[] parts = str.split("/");
            TextView tv = findViewById(R.id.songNameTV);
            tv.setText(parts[parts.length - 1]);
            super.onActivityResult(requestCode, resultCode, data);
        } catch (Exception e) {
            Toast.makeText(this, getResources().getString(R.string.cant_load), Toast.LENGTH_LONG).show();
        }
    }

    public static String getFilePath(Context context, Uri uri) {
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        if (DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            Cursor cursor = null;
            cursor = context.getContentResolver()
                    .query(uri, projection, selection, selectionArgs, null);
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            if (cursor.moveToFirst()) {
                return cursor.getString(column_index);
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    //endregion

    //region Seekbar handling methods

    private void startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadScheduledExecutor();
        }
        if (mSeekBarPositionUpdateTask == null) {
            mSeekBarPositionUpdateTask = new Runnable() {
                @Override
                public void run() {
                    updateProgressCallbackTask();
                }
            };
        }
        mExecutor.scheduleAtFixedRate(
                mSeekBarPositionUpdateTask,
                0,
                PLAYBACK_POSITION_REFRESH_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );
    }

    private void stopUpdatingCallbackWithPosition() {
        if (mExecutor != null) {
            mExecutor.shutdownNow();
            mExecutor = null;
            mSeekBarPositionUpdateTask = null;
        }
    }

    public void updateProgressCallbackTask() {
        if (!isUserSeeking) {
            SeekBar sb = findViewById(R.id.seekBar);
            int pos = (int) GetPosition();
            sb.setProgress(pos);
            //setCurrTimeText(pos);
        }
    }

    private void setCurrTimeText(int currMilis) {
        String str = String.format(Locale.ENGLISH, "%d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(currMilis),
                TimeUnit.MILLISECONDS.toSeconds(currMilis) % TimeUnit.MINUTES.toSeconds(1)
        );
        TextView tv = findViewById(R.id.TimeTextBox);
        tv.setText(str + " / " + totalTimeText);
    }

    private void setTotalTimeText(int totalMilis) {
        totalTimeText = String.format(Locale.ENGLISH, "%d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(totalMilis),
                TimeUnit.MILLISECONDS.toSeconds(totalMilis) % TimeUnit.MINUTES.toSeconds(1)
        );
        TextView tv = findViewById(R.id.TimeTextBox);
        tv.setText("0:00 / " + totalTimeText);
    }


    private void setSeekBarNewSong() {
        SeekBar sb = findViewById(R.id.seekBar);
        int duration = GetDuration();
        sb.setProgress(0);
        sb.setMax(duration);
        setTotalTimeText(duration);
    }

    //endregion

    //region app lifecycle methods

    @Override
    public void onPause() {
        super.onPause();
        if (!dontPause) {
            if (playing)
                PlayPause();
            if (recording)
                Record();
        }else dontPause = false;
        onBackground();
    }

    @Override
    public void onResume() {
        super.onResume();
        onForeground();
    }

    @Override
    public void onBackPressed() {

        super.onBackPressed();
    }

    protected void onDestroy() {
        super.onDestroy();
        Cleanup();
    }

    //endregion

    //region Native library functions

    // Functions implemented in the native library.
    private native void StartAudio(int samplerate, int buffersize, String tempPath, String destPath);

    private native void OpenFileFromPath(String path);

    private native void SetPitchAndRate(float shift, float rate);

    private native void TogglePlayback();

    private native void ToggleRecording();

    private native void ToggleRemoveVocal();

    private native void MoveForward();

    private native void MoveBackward();

    private native double GetPosition();

    private native int GetDuration();

    private native void SetPosition(double position);

    private native void onForeground();

    private native void onBackground();

    private native void Cleanup();

    //endregion
}
