package com.student.pomodoro;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String CHANNEL_ID = "pomodoro_channel";
    private static final String NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS";

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView timeText;
    private TextView modeBadge;
    private TextView roundText;
    private TextView messageText;
    private TextView doneText;
    private TextView minutesText;
    private TextView breaksText;
    private ProgressBar progressBar;

    private Button startPauseButton;
    private Button musicButton;
    private EditText workInput;
    private EditText breakInput;
    private EditText longBreakInput;
    private EditText roundsInput;
    private SeekBar volumeInput;

    private SharedPreferences prefs;
    private ToneGenerator toneGenerator;
    private MediaPlayer mediaPlayer;

    private boolean isRunning = false;
    private boolean isWorkMode = true;
    private boolean musicOn = false;
    private int round = 1;
    private int totalSeconds;
    private int secondsLeft;

    private int doneCount = 0;
    private int workMinutesCount = 0;
    private int breaksCount = 0;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isRunning) {
                return;
            }

            secondsLeft--;

            if (secondsLeft <= 0) {
                finishInterval(true);
                return;
            }

            updateView();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        prefs = getSharedPreferences("pomodoro_stats", MODE_PRIVATE);
        loadStats();
        createNotificationChannel();
        requestNotificationPermission();
        buildInterface();

        totalSeconds = getCurrentDuration();
        secondsLeft = totalSeconds;
        updateView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        stopMusicPlayer();

        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void buildInterface() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(237, 242, 244));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Таймер Pomodoro");
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(29, 38, 48));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Простое приложение для учебного задания");
        subtitle.setTextSize(15);
        subtitle.setTextColor(Color.rgb(92, 104, 116));
        subtitle.setPadding(0, dp(4), 0, dp(16));
        root.addView(subtitle);

        modeBadge = new TextView(this);
        modeBadge.setGravity(Gravity.CENTER);
        modeBadge.setTextColor(Color.WHITE);
        modeBadge.setTypeface(Typeface.DEFAULT_BOLD);
        modeBadge.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(modeBadge, matchWrapParams());

        roundText = new TextView(this);
        roundText.setGravity(Gravity.CENTER);
        roundText.setTextSize(17);
        roundText.setTextColor(Color.rgb(92, 104, 116));
        roundText.setPadding(0, dp(24), 0, dp(6));
        root.addView(roundText);

        timeText = new TextView(this);
        timeText.setGravity(Gravity.CENTER);
        timeText.setTextSize(64);
        timeText.setTypeface(Typeface.DEFAULT_BOLD);
        timeText.setTextColor(Color.rgb(29, 38, 48));
        root.addView(timeText);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        root.addView(progressBar, matchWrapParams());

        LinearLayout buttonsRow = row();
        startPauseButton = makeButton("Старт");
        Button resetButton = makeButton("Сброс");
        buttonsRow.addView(startPauseButton, weightParams());
        buttonsRow.addView(resetButton, weightParams());
        root.addView(buttonsRow);

        messageText = new TextView(this);
        messageText.setGravity(Gravity.CENTER);
        messageText.setTextColor(Color.rgb(73, 86, 98));
        messageText.setPadding(0, dp(12), 0, dp(12));
        root.addView(messageText);

        root.addView(sectionTitle("Интервалы"));
        workInput = makeNumberInput("25");
        breakInput = makeNumberInput("5");
        longBreakInput = makeNumberInput("15");
        roundsInput = makeNumberInput("4");
        root.addView(makeInputBlock("Работа, минут", workInput));
        root.addView(makeInputBlock("Перерыв, минут", breakInput));
        root.addView(makeInputBlock("Длинный перерыв, минут", longBreakInput));
        root.addView(makeInputBlock("Рабочих интервалов до длинного перерыва", roundsInput));

        root.addView(sectionTitle("Музыка"));

        LinearLayout musicRow = row();
        musicButton = makeButton("Включить");
        volumeInput = new SeekBar(this);
        volumeInput.setMax(100);
        volumeInput.setProgress(35);
        musicRow.addView(musicButton, weightParams());
        musicRow.addView(volumeInput, weightParams());
        root.addView(musicRow);

        root.addView(sectionTitle("Статистика"));
        LinearLayout statsRow = row();
        doneText = makeStatText();
        minutesText = makeStatText();
        breaksText = makeStatText();
        statsRow.addView(makeStatBlock(doneText, "помидоров"), weightParams());
        statsRow.addView(makeStatBlock(minutesText, "минут работы"), weightParams());
        statsRow.addView(makeStatBlock(breaksText, "перерывов"), weightParams());
        root.addView(statsRow);

        Button clearStatsButton = makeButton("Очистить статистику");
        root.addView(clearStatsButton, matchWrapParams());

        startPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isRunning) {
                    pauseTimer();
                } else {
                    startTimer();
                }
            }
        });

        resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetTimer();
            }
        });

        clearStatsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doneCount = 0;
                workMinutesCount = 0;
                breaksCount = 0;
                saveStats();
                updateView();
            }
        });

        musicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMusic();
            }
        });

        volumeInput.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                recreateToneGenerator();
                setMusicVolume();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        addTimerInputWatcher(workInput);
        addTimerInputWatcher(breakInput);
        addTimerInputWatcher(longBreakInput);
        addTimerInputWatcher(roundsInput);

        setContentView(scrollView);
    }

    private void startTimer() {
        if (isRunning) {
            return;
        }

        isRunning = true;
        messageText.setText(isWorkMode ? "Работаем. Телефон лучше убрать." : "Перерыв, можно выдохнуть.");
        handler.postDelayed(tickRunnable, 1000);
        updateView();
    }

    private void pauseTimer() {
        isRunning = false;
        handler.removeCallbacks(tickRunnable);
        messageText.setText("Пауза. Таймер не сброшен.");
        updateView();
    }

    private void resetTimer() {
        pauseTimer();
        isWorkMode = true;
        round = 1;
        totalSeconds = getCurrentDuration();
        secondsLeft = totalSeconds;
        messageText.setText("Таймер сброшен.");
        updateView();
    }

    private void finishInterval(boolean countStats) {
        isRunning = false;
        handler.removeCallbacks(tickRunnable);

        if (countStats) {
            playSignal();
        }

        if (isWorkMode) {
            if (countStats) {
                doneCount++;
                workMinutesCount += readNumber(workInput, 25, 1, 180);
                saveStats();
                sendNotification("Работа закончена", "Пора сделать перерыв.");
            }

            isWorkMode = false;
            messageText.setText(countStats ? "Рабочий интервал завершен." : "Перешли к перерыву без записи в статистику.");
        } else {
            if (countStats) {
                breaksCount++;
                saveStats();
                sendNotification("Перерыв закончен", "Можно начинать следующий рабочий интервал.");
            }

            isWorkMode = true;
            round++;
            messageText.setText(countStats ? "Перерыв завершен." : "Перешли к работе без записи в статистику.");
        }

        totalSeconds = getCurrentDuration();
        secondsLeft = totalSeconds;

        if (countStats) {
            startTimer();
        } else {
            updateView();
        }
    }

    private int getCurrentDuration() {
        if (isWorkMode) {
            return readNumber(workInput, 25, 1, 180) * 60;
        }

        int roundsBeforeLongBreak = readNumber(roundsInput, 4, 1, 12);
        if (round % roundsBeforeLongBreak == 0) {
            return readNumber(longBreakInput, 15, 1, 90) * 60;
        }

        return readNumber(breakInput, 5, 1, 60) * 60;
    }

    private void updateView() {
        timeText.setText(formatTime(secondsLeft));
        roundText.setText("Интервал " + round);
        startPauseButton.setText(isRunning ? "Пауза" : "Старт");
        doneText.setText(String.valueOf(doneCount));
        minutesText.setText(String.valueOf(workMinutesCount));
        breaksText.setText(String.valueOf(breaksCount));

        int progress = 0;
        if (totalSeconds > 0) {
            progress = 100 - Math.round((secondsLeft * 100f) / totalSeconds);
        }
        progressBar.setProgress(progress);

        if (isWorkMode) {
            modeBadge.setText("Работа");
            modeBadge.setBackgroundColor(Color.rgb(43, 138, 62));
        } else {
            modeBadge.setText("Перерыв");
            modeBadge.setBackgroundColor(Color.rgb(25, 113, 194));
        }
    }

    private String formatTime(int value) {
        int minutes = value / 60;
        int seconds = value % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private int readNumber(EditText input, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(input.getText().toString());
            return Math.max(min, Math.min(max, value));
        } catch (Exception error) {
            return fallback;
        }
    }

    private void addTimerInputWatcher(EditText input) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!isRunning) {
                    totalSeconds = getCurrentDuration();
                    secondsLeft = totalSeconds;
                    updateView();
                }
            }
        });
    }

    private void toggleMusic() {
        if (musicOn) {
            musicOn = false;
            stopMusicPlayer();
            musicButton.setText("Включить");
            return;
        }

        musicOn = true;
        musicButton.setText("Выключить");
        startMusicPlayer();
    }

    private void startMusicPlayer() {
        stopMusicPlayer();

        mediaPlayer = MediaPlayer.create(this, R.raw.music);

        if (mediaPlayer == null) {
            musicOn = false;
            musicButton.setText("Включить");
            return;
        }

        mediaPlayer.setLooping(true);
        setMusicVolume();
        mediaPlayer.start();
    }

    private void stopMusicPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void setMusicVolume() {
        if (mediaPlayer == null || volumeInput == null) {
            return;
        }

        float volume = volumeInput.getProgress() / 100f;
        mediaPlayer.setVolume(volume, volume);
    }

    private void ensureToneGenerator() {
        if (toneGenerator == null) {
            toneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, Math.max(1, volumeInput.getProgress()));
        }
    }

    private void recreateToneGenerator() {
        if (toneGenerator != null) {
            toneGenerator.release();
            toneGenerator = null;
        }
    }

    private void playSignal() {
        ensureToneGenerator();
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 180);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ensureToneGenerator();
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 180);
            }
        }, 240);
    }

    private void sendNotification(String title, String body) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags | PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);
        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        builder.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(R.drawable.ic_stat_timer)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Pomodoro",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(NOTIFICATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{NOTIFICATION_PERMISSION}, 10);
        }
    }

    private void loadStats() {
        doneCount = prefs.getInt("done", 0);
        workMinutesCount = prefs.getInt("minutes", 0);
        breaksCount = prefs.getInt("breaks", 0);
    }

    private void saveStats() {
        prefs.edit()
                .putInt("done", doneCount)
                .putInt("minutes", workMinutesCount)
                .putInt("breaks", breaksCount)
                .apply();
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(29, 38, 48));
        title.setPadding(0, dp(20), 0, dp(8));
        return title;
    }

    private EditText makeNumberInput(String value) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setSingleLine(true);
        input.setTextSize(16);
        return input;
    }

    private LinearLayout makeInputBlock(String label, EditText input) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(0, 0, 0, dp(8));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.rgb(73, 86, 98));
        block.addView(labelView);
        block.addView(input, matchWrapParams());
        return block;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private TextView makeStatText() {
        TextView text = new TextView(this);
        text.setTextSize(28);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextColor(Color.rgb(25, 113, 194));
        text.setGravity(Gravity.CENTER);
        return text;
    }

    private LinearLayout makeStatBlock(TextView numberView, String label) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER);
        block.setPadding(dp(4), dp(8), dp(4), dp(8));

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setGravity(Gravity.CENTER);
        labelView.setTextColor(Color.rgb(92, 104, 116));

        block.addView(numberView);
        block.addView(labelView);
        return block;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(8), 0, dp(8));
        return row;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private LinearLayout.LayoutParams matchWrapParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(4), 0, dp(4));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
