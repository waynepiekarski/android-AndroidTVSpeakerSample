/*
 * Copyright (C) 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wearable.speaker;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;


/**
 * We first get the required permission to use the MIC. If it is granted, then we continue with
 * the application and present the UI with three icons: a MIC icon (if pressed, user can record up
 * to 10 seconds), a Play icon (if clicked, it wil playback the recorded audio file) and a music
 * note icon (if clicked, it plays an MP3 file that is included in the app).
 */
public class MainActivity extends FragmentActivity implements
        SoundRecorder.OnVoicePlaybackStateChangedListener {

    public enum UIState {
        MIC_UP(0), SOUND_UP(1), MUSIC_UP(2), HOME(3);
        private int mState;

        UIState(int state) {
            mState = state;
        }

        static UIState getUIState(int state) {
            for(UIState uiState : values()) {
                if (uiState.mState == state) {
                    return uiState;
                }
            }
            return null;
        }
    }

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 100;
    private static final long COUNT_DOWN_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long MILLIS_IN_SECOND = TimeUnit.SECONDS.toMillis(1);
    private static final String VOICE_FILE_NAME = "audiorecord.pcm";

    private MediaPlayer mMediaPlayer;
    private AppState mState = AppState.READY;
    private UIState mUiState = UIState.HOME;
    private SoundRecorder mSoundRecorder;

    private ProgressBar mProgressBar;
    private CountDownTimer mCountDownTimer;
    private Button mButtonPlay, mButtonMic, mButtonMusic, mButtonStop;
    private TextView mInfoText;

     enum AppState {
        READY, PLAYING_VOICE, PLAYING_MUSIC, RECORDING
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        mProgressBar = findViewById(R.id.progress_bar);

        mButtonPlay  = findViewById(R.id.play);
        mButtonMic   = findViewById(R.id.mic);
        mButtonMusic = findViewById(R.id.music);
        mButtonStop  = findViewById(R.id.stop);
        mInfoText   = findViewById(R.id.info);

        mButtonPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUIStateChanged(UIState.SOUND_UP);
            }
        });
        mButtonMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUIStateChanged(UIState.MIC_UP);
            }
        });
        mButtonMusic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUIStateChanged(UIState.MUSIC_UP);
            }
        });
        mButtonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onUIStateChanged(UIState.HOME);
            }
        });
    }

    private void setProgressBar(long progressInMillis) {
        mProgressBar.setProgress((int) (progressInMillis / MILLIS_IN_SECOND));
    }

    public void onUIStateChanged(UIState state) {
        Log.d(TAG, "UI State is: " + state);
        if (mUiState == state) {
            return;
        }

        // Do not allow multiple playback or recording to happen, always reset to HOME before
        if ((mState != AppState.READY) && (state != UIState.HOME)) {
            Log.d(TAG, "Setting to HOME state first");
            onUIStateChanged(UIState.HOME);
        }

        switch (state) {
            case MUSIC_UP:
                mState = AppState.PLAYING_MUSIC;
                mUiState = state;
                playMusic();
                break;
            case MIC_UP:
                mState = AppState.RECORDING;
                mUiState = state;
                mSoundRecorder.startRecording();
                setProgressBar(COUNT_DOWN_MS);
                mCountDownTimer = new CountDownTimer(COUNT_DOWN_MS, MILLIS_IN_SECOND) {
                    @Override
                    public void onTick(long millisUntilFinished) {
                        mProgressBar.setVisibility(View.VISIBLE);
                        setProgressBar(millisUntilFinished);
                        Log.d(TAG, "Time Left: " + millisUntilFinished / MILLIS_IN_SECOND);
                    }

                    @Override
                    public void onFinish() {
                        mProgressBar.setProgress(0);
                        mProgressBar.setVisibility(View.INVISIBLE);
                        mSoundRecorder.stopRecording();
                        onUIStateChanged(UIState.HOME);
                        mState = AppState.READY;
                        mCountDownTimer = null;
                    }
                };
                mCountDownTimer.start();
                break;
            case SOUND_UP:
                mState = AppState.PLAYING_VOICE;
                mUiState = state;
                mSoundRecorder.startPlay();
                break;
            case HOME:
                switch (mState) {
                    case PLAYING_MUSIC:
                        mState = AppState.READY;
                        mUiState = state;
                        stopMusic();
                        break;
                    case PLAYING_VOICE:
                        mState = AppState.READY;
                        mUiState = state;
                        mSoundRecorder.stopPlaying();
                        break;
                    case RECORDING:
                        mState = AppState.READY;
                        mUiState = state;
                        mSoundRecorder.stopRecording();
                        if (mCountDownTimer != null) {
                            mCountDownTimer.cancel();
                            mCountDownTimer = null;
                        }
                        mProgressBar.setVisibility(View.INVISIBLE);
                        setProgressBar(COUNT_DOWN_MS);
                        break;
                }
                break;
        }
    }

    /**
     * Plays back the MP3 file embedded in the application
     */
    private void playMusic() {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer.create(this, R.raw.sound);
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    // we need to transition to the READY/Home state
                    Log.d(TAG, "Music Finished");
                    onUIStateChanged(UIState.HOME);
                }
            });
        }
        mMediaPlayer.start();

        mInfoText.setText("Playing music: No information");
    }

    /**
     * Stops the playback of the MP3 file.
     */
    private void stopMusic() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    /**
     * Checks the permission that this app needs and if it has not been granted, it will
     * prompt the user to grant it, otherwise it shuts down the app.
     */
    private void checkPermissions() {
        boolean recordAudioPermissionGranted =
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        == PackageManager.PERMISSION_GRANTED;

        if (recordAudioPermissionGranted) {
            start();
        } else {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.RECORD_AUDIO},
                    PERMISSIONS_REQUEST_CODE);
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
            String permissions[], int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                start();
            } else {
                // Permission has been denied before. At this point we should show a dialog to
                // user and explain why this permission is needed and direct him to go to the
                // Permissions settings for the app in the System settings. For this sample, we
                // simply exit to get to the important part.
                Toast.makeText(this, R.string.exiting_for_permissions, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * Starts the main flow of the application.
     */
    private void start() {
        mSoundRecorder = new SoundRecorder(this, VOICE_FILE_NAME, this, mInfoText);
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkPermissions();
    }

    @Override
    protected void onStop() {
        if (mSoundRecorder != null) {
            mSoundRecorder.cleanup();
            mSoundRecorder = null;
        }
        if (mCountDownTimer != null) {
            mCountDownTimer.cancel();
        }

        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        super.onStop();
    }

    @Override
    public void onPlaybackStopped() {
        onUIStateChanged(UIState.HOME);
    }
}
