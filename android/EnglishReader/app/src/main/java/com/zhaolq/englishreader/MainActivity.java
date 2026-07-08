package com.zhaolq.englishreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());

    private MediaPlayer player;
    private LessonView lessonView;
    private Lesson currentLesson;
    private int activeIndex = -1;
    private int sequenceIndex = -1;
    private boolean playSequence = false;
    private int wordStage = WORD_STAGE_FOLLOW;
    private int wordPosition = 0;
    private boolean wordAnswerShown = false;
    private final List<Integer> wordQueue = new ArrayList<>();
    private int sentenceStage = SENTENCE_STAGE_ZH_TO_EN;
    private int sentencePosition = 0;
    private boolean sentenceAnswerShown = false;
    private final List<Integer> sentenceQueue = new ArrayList<>();
    private int transferStage = TRANSFER_STAGE_ZH_TO_EN;
    private int transferPosition = 0;
    private boolean transferAnswerShown = false;
    private final List<Integer> transferQueue = new ArrayList<>();
    private Runnable backAction;

    private static final int WORD_STAGE_FOLLOW = 0;
    private static final int WORD_STAGE_ZH_TO_EN = 1;
    private static final int WORD_STAGE_EN_TO_ZH = 2;
    private static final int SENTENCE_STAGE_ZH_TO_EN = 0;
    private static final int SENTENCE_STAGE_EN_TO_ZH = 1;
    private static final int TRANSFER_STAGE_ZH_TO_EN = 0;
    private static final int TRANSFER_STAGE_EN_TO_ZH = 1;
    private static final String RELEASE_API_URL =
            "https://api.github.com/repos/zhaohongxin0/english-reader-android/releases/latest";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    private static final int COLOR_PAGE = Color.rgb(247, 250, 253);
    private static final int COLOR_TOP_BAR = Color.rgb(239, 246, 255);
    private static final int COLOR_SURFACE = Color.WHITE;
    private static final int COLOR_SURFACE_SOFT = Color.rgb(250, 253, 255);
    private static final int COLOR_TEXT = Color.rgb(24, 38, 58);
    private static final int COLOR_MUTED = Color.rgb(92, 108, 126);
    private static final int COLOR_PRIMARY = Color.rgb(20, 112, 214);
    private static final int COLOR_PRIMARY_DARK = Color.rgb(12, 83, 164);
    private static final int COLOR_PRIMARY_PRESSED = Color.rgb(8, 74, 148);
    private static final int COLOR_BUTTON = Color.rgb(255, 255, 255);
    private static final int COLOR_BUTTON_PRESSED = Color.rgb(229, 241, 255);
    private static final int COLOR_BORDER = Color.rgb(204, 219, 235);
    private static final int COLOR_DISABLED = Color.rgb(231, 236, 242);
    private static final int COLOR_DISABLED_TEXT = Color.rgb(140, 151, 164);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(COLOR_TOP_BAR);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        showLessonList();
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (backAction != null) {
            backAction.run();
            return;
        }
        super.onBackPressed();
    }

    private void showLessonList() {
        stopPlayback();
        backAction = null;
        try {
            JSONObject root = new JSONObject(readAssetText("lessons/index.json"));
            JSONArray lessons = root.getJSONArray("lessons");

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
            container.setPadding(dp(20), dp(28), dp(20), dp(20));
            container.setBackgroundColor(COLOR_PAGE);

            TextView title = new TextView(this);
            title.setText("English Reader");
            title.setTextColor(COLOR_TEXT);
            title.setTextSize(26);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            container.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int i = 0; i < lessons.length(); i++) {
                JSONObject item = lessons.getJSONObject(i);
                String label = item.getString("title") + "\n" + item.optString("subtitle");
                Button button = makePrimaryButton(label);
                button.setTextSize(18);
                button.setMinHeight(dp(78));
                button.setOnClickListener(v -> {
                    try {
                        showLessonModeChoice(loadLesson(item.getString("manifest")));
                    } catch (Exception e) {
                        showError(e);
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, dp(24), 0, 0);
                container.addView(button, lp);
            }

            TextView version = new TextView(this);
            version.setText("当前版本 v" + BuildConfig.VERSION_NAME);
            version.setTextColor(COLOR_MUTED);
            version.setTextSize(14);
            version.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams versionLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            versionLp.setMargins(0, dp(22), 0, 0);
            container.addView(version, versionLp);

            Button update = makeButton("检查更新");
            update.setTextSize(16);
            update.setOnClickListener(v -> checkForUpdates());
            LinearLayout.LayoutParams updateLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            updateLp.setMargins(0, dp(12), 0, 0);
            container.addView(update, updateLp);

            setContentView(container);
        } catch (Exception e) {
            showError(e);
        }
    }

    private void showLessonModeChoice(Lesson lesson) {
        stopPlayback();
        currentLesson = lesson;
        backAction = this::showLessonList;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.addView(makeTopBar(lesson.title, v -> showLessonList()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(20), dp(42), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText(lesson.subtitle);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        body.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView prompt = new TextView(this);
        prompt.setText("选择练习内容");
        prompt.setTextColor(COLOR_MUTED);
        prompt.setTextSize(16);
        prompt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        promptLp.setMargins(0, dp(16), 0, dp(20));
        body.addView(prompt, promptLp);

        Button words = makeButton("词汇练习");
        words.setMinHeight(dp(82));
        words.setEnabled(!lesson.words.isEmpty());
        words.setOnClickListener(v -> startWordPractice(lesson));
        body.addView(words, modeButtonLp(dp(12)));

        Button sentences = makeButton("句子练习");
        sentences.setMinHeight(dp(82));
        sentences.setOnClickListener(v -> showSentencePracticeChoice(lesson));
        body.addView(sentences, modeButtonLp(dp(16)));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        setContentView(root);
    }

    private void showSentencePracticeChoice(Lesson lesson) {
        stopPlayback();
        currentLesson = lesson;
        backAction = () -> showLessonModeChoice(lesson);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.addView(makeTopBar("句子练习", v -> showLessonModeChoice(lesson)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(20), dp(42), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText(lesson.subtitle);
        title.setTextColor(COLOR_TEXT);
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        body.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView prompt = new TextView(this);
        prompt.setText("选择句子练习方式");
        prompt.setTextColor(COLOR_MUTED);
        prompt.setTextSize(16);
        prompt.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams promptLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        promptLp.setMargins(0, dp(16), 0, dp(20));
        body.addView(prompt, promptLp);

        Button follow = makeButton("跟读练习");
        follow.setMinHeight(dp(82));
        follow.setOnClickListener(v -> showSentenceLesson(lesson));
        body.addView(follow, modeButtonLp(dp(12)));

        Button promptPractice = makeButton("提示练习");
        promptPractice.setMinHeight(dp(82));
        promptPractice.setOnClickListener(v -> startSentencePromptPractice(lesson));
        body.addView(promptPractice, modeButtonLp(dp(16)));

        Button transfer = makeButton("举一反三");
        transfer.setMinHeight(dp(82));
        transfer.setEnabled(!lesson.transferItems.isEmpty());
        transfer.setOnClickListener(v -> startTransferPractice(lesson));
        body.addView(transfer, modeButtonLp(dp(16)));

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        setContentView(root);
    }

    private Lesson loadLesson(String manifestPath) throws Exception {
        JSONObject json = new JSONObject(readAssetText(manifestPath));
        Lesson lesson = new Lesson();
        lesson.title = json.getString("title");
        lesson.subtitle = json.optString("subtitle");
        lesson.imagePath = json.getString("image");
        JSONArray words = json.optJSONArray("words");
        if (words != null) {
            for (int i = 0; i < words.length(); i++) {
                JSONObject obj = words.getJSONObject(i);
                WordItem word = new WordItem();
                word.english = obj.getString("english");
                word.chinese = obj.getString("chinese");
                word.englishAudioPath = obj.getString("english_audio");
                word.chineseAudioPath = obj.getString("chinese_audio");
                lesson.words.add(word);
            }
        }
        JSONArray items = json.getJSONArray("items");
        for (int i = 0; i < items.length(); i++) {
            JSONObject obj = items.getJSONObject(i);
            JSONArray region = obj.getJSONArray("region");
            SentenceItem sentence = new SentenceItem();
            sentence.text = obj.getString("text");
            sentence.chinese = obj.optString("chinese");
            sentence.audioPath = obj.getString("audio");
            sentence.chineseAudioPath = obj.optString("chinese_audio");
            sentence.region = new RectF(
                    (float) region.getDouble(0),
                    (float) region.getDouble(1),
                    (float) (region.getDouble(0) + region.getDouble(2)),
                    (float) (region.getDouble(1) + region.getDouble(3)));
            lesson.items.add(sentence);
        }
        JSONArray transferItems = json.optJSONArray("transfer_practice");
        if (transferItems != null) {
            for (int i = 0; i < transferItems.length(); i++) {
                JSONObject obj = transferItems.getJSONObject(i);
                TransferItem transfer = new TransferItem();
                transfer.text = obj.getString("text");
                transfer.chinese = obj.getString("chinese");
                transfer.audioPath = obj.getString("audio");
                transfer.chineseAudioPath = obj.getString("chinese_audio");
                lesson.transferItems.add(transfer);
            }
        }
        try (InputStream stream = getAssets().open(lesson.imagePath)) {
            lesson.bitmap = BitmapFactory.decodeStream(stream);
        }
        return lesson;
    }

    private void showLessonStart(Lesson lesson) {
        currentLesson = lesson;
        if (!lesson.words.isEmpty()) {
            startWordPractice(lesson);
        } else {
            showSentenceLesson(lesson);
        }
    }

    private void startWordPractice(Lesson lesson) {
        stopPlayback();
        currentLesson = lesson;
        wordStage = WORD_STAGE_FOLLOW;
        wordPosition = 0;
        wordAnswerShown = false;
        fillWordQueue();
        showWordPractice(true);
    }

    private void fillWordQueue() {
        wordQueue.clear();
        if (currentLesson == null) {
            return;
        }
        for (int i = 0; i < currentLesson.words.size(); i++) {
            wordQueue.add(i);
        }
        wordPosition = 0;
    }

    private void showWordPractice(boolean autoPlay) {
        stopPlayback();
        backAction = () -> showLessonModeChoice(currentLesson);
        if (currentLesson == null || currentLesson.words.isEmpty() || wordQueue.isEmpty()) {
            if (currentLesson != null) {
                showSentenceLesson(currentLesson);
            }
            return;
        }

        WordItem word = currentLesson.words.get(wordQueue.get(wordPosition));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.addView(makeTopBar(wordStageTitle(), v -> showLessonModeChoice(currentLesson)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(18), dp(28), dp(18), dp(12));

        TextView progress = new TextView(this);
        progress.setText(wordProgressText());
        progress.setTextColor(COLOR_MUTED);
        progress.setTextSize(15);
        progress.setGravity(Gravity.CENTER);
        body.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(24), dp(18), dp(24));
        stylePracticeCard(card);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1);
        cardLp.setMargins(0, dp(18), 0, dp(18));

        TextView prompt = new TextView(this);
        prompt.setText(wordPromptText(word));
        prompt.setTextColor(COLOR_TEXT);
        prompt.setTextSize(wordStage == WORD_STAGE_FOLLOW ? 42 : 36);
        prompt.setGravity(Gravity.CENTER);
        prompt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(prompt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView answer = new TextView(this);
        answer.setText(wordAnswerText(word));
        answer.setTextColor(COLOR_PRIMARY);
        answer.setTextSize(28);
        answer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        answerLp.setMargins(0, dp(22), 0, 0);
        card.addView(answer, answerLp);
        body.addView(card, cardLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);

        if (wordStage == WORD_STAGE_FOLLOW) {
            Button listen = makeButton("听一遍");
            listen.setOnClickListener(v -> playAsset(word.englishAudioPath, null));
            controls.addView(listen, fullButtonLp(0));

            Button next = makePrimaryButton(wordPosition == wordQueue.size() - 1 ? "开始练习" : "下一个");
            next.setOnClickListener(v -> nextFollowWord());
            controls.addView(next, fullButtonLp(dp(10)));
        } else if (!wordAnswerShown) {
            Button repeat = makeButton("再听提示");
            repeat.setOnClickListener(v -> playWordPrompt(word));
            controls.addView(repeat, fullButtonLp(0));

            Button show = makePrimaryButton("查看答案");
            show.setOnClickListener(v -> {
                wordAnswerShown = true;
                showWordPractice(false);
                playWordAnswer(word);
            });
            controls.addView(show, fullButtonLp(dp(10)));
        } else {
            Button listenAgain = makePrimaryButton("再听一遍");
            listenAgain.setOnClickListener(v -> playWordAnswer(word));
            controls.addView(listenAgain, fullButtonLp(0));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            Button mastered = makePrimaryButton("会了");
            mastered.setOnClickListener(v -> completeCurrentWord(true));
            row.addView(mastered, new LinearLayout.LayoutParams(0, dp(52), 1));

            Button retry = makeButton("再练一次");
            retry.setOnClickListener(v -> completeCurrentWord(false));
            LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(0, dp(52), 1);
            retryLp.setMargins(dp(10), 0, 0, 0);
            row.addView(retry, retryLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dp(10), 0, 0);
            controls.addView(row, rowLp);
        }

        body.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        setContentView(root);

        if (autoPlay) {
            handler.postDelayed(() -> {
                if (currentLesson != null && !wordQueue.isEmpty()) {
                    playWordPrompt(currentLesson.words.get(wordQueue.get(wordPosition)));
                }
            }, 220);
        }
    }

    private LinearLayout makeTopBar(String titleText, View.OnClickListener backClick) {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(10), dp(8), dp(10), dp(8));
        top.setBackgroundColor(COLOR_TOP_BAR);

        Button back = makeToolbarButton("返回");
        back.setOnClickListener(backClick);
        top.addView(back, new LinearLayout.LayoutParams(dp(72), dp(44)));

        TextView header = new TextView(this);
        header.setText(titleText);
        header.setTextColor(COLOR_TEXT);
        header.setTextSize(17);
        header.setGravity(Gravity.CENTER);
        header.setMaxLines(2);
        header.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams headerLp = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1);
        headerLp.setMargins(dp(8), 0, dp(8), 0);
        top.addView(header, headerLp);

        Button home = makeToolbarButton("首页");
        home.setOnClickListener(v -> showLessonList());
        top.addView(home, new LinearLayout.LayoutParams(dp(72), dp(44)));
        return top;
    }

    private Button makePrimaryButton(String text) {
        return makeButton(text, true);
    }

    private Button makeButton(String text) {
        return makeButton(text, false);
    }

    private Button makeToolbarButton(String text) {
        Button button = makeButton(text, false);
        button.setTextSize(15);
        button.setMinHeight(dp(44));
        button.setMinimumHeight(dp(44));
        button.setBackground(buttonBackground(COLOR_SURFACE, COLOR_BUTTON_PRESSED, COLOR_BORDER, 8));
        button.setElevation(0);
        return button;
    }

    private Button makeButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setMinHeight(dp(52));
        button.setMinimumHeight(dp(52));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setIncludeFontPadding(false);
        if (primary) {
            button.setTextColor(buttonTextColor(Color.WHITE));
            button.setBackground(buttonBackground(COLOR_PRIMARY, COLOR_PRIMARY_PRESSED, COLOR_PRIMARY, 8));
            button.setElevation(dp(2));
        } else {
            button.setTextColor(buttonTextColor(COLOR_PRIMARY_DARK));
            button.setBackground(buttonBackground(COLOR_BUTTON, COLOR_BUTTON_PRESSED, COLOR_BORDER, 8));
            button.setElevation(dp(1));
        }
        return button;
    }

    private void stylePracticeCard(LinearLayout card) {
        card.setBackground(roundedDrawable(COLOR_SURFACE_SOFT, COLOR_BORDER, 8));
        card.setElevation(dp(1));
    }

    private ColorStateList buttonTextColor(int enabledColor) {
        return new ColorStateList(
                new int[][]{
                        new int[]{-android.R.attr.state_enabled},
                        new int[]{}
                },
                new int[]{
                        COLOR_DISABLED_TEXT,
                        enabledColor
                });
    }

    private StateListDrawable buttonBackground(int normalColor, int pressedColor, int strokeColor, float radiusDp) {
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{-android.R.attr.state_enabled},
                roundedDrawable(COLOR_DISABLED, COLOR_BORDER, radiusDp));
        background.addState(
                new int[]{android.R.attr.state_pressed},
                roundedDrawable(pressedColor, strokeColor, radiusDp));
        background.addState(
                new int[]{},
                roundedDrawable(normalColor, strokeColor, radiusDp));
        return background;
    }

    private GradientDrawable roundedDrawable(int fillColor, int strokeColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullButtonLp(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52));
        lp.setMargins(0, topMargin, 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams modeButtonLp(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, topMargin, 0, 0);
        return lp;
    }

    private String wordStageTitle() {
        if (wordStage == WORD_STAGE_FOLLOW) {
            return "词汇跟读";
        }
        if (wordStage == WORD_STAGE_ZH_TO_EN) {
            return "听中文，说英文";
        }
        return "听英文，说中文";
    }

    private String wordProgressText() {
        if (wordStage == WORD_STAGE_FOLLOW) {
            return "先听，再跟读  " + (wordPosition + 1) + " / " + wordQueue.size();
        }
        return "没记住的会继续出现，还剩 " + wordQueue.size() + " 个";
    }

    private String wordPromptText(WordItem word) {
        if (wordStage == WORD_STAGE_FOLLOW) {
            return word.english;
        }
        if (wordStage == WORD_STAGE_ZH_TO_EN) {
            return word.chinese;
        }
        return word.english;
    }

    private String wordAnswerText(WordItem word) {
        if (wordStage == WORD_STAGE_FOLLOW) {
            return word.chinese;
        }
        if (!wordAnswerShown) {
            return wordStage == WORD_STAGE_ZH_TO_EN ? "请说出英文" : "请说出中文意思";
        }
        return wordStage == WORD_STAGE_ZH_TO_EN ? word.english : word.chinese;
    }

    private void nextFollowWord() {
        if (wordPosition < wordQueue.size() - 1) {
            wordPosition++;
            showWordPractice(true);
            return;
        }
        wordStage = WORD_STAGE_ZH_TO_EN;
        fillWordQueue();
        wordAnswerShown = false;
        showWordPractice(true);
    }

    private void completeCurrentWord(boolean mastered) {
        if (wordQueue.isEmpty()) {
            advanceWordStage();
            return;
        }
        int current = wordQueue.remove(wordPosition);
        if (!mastered) {
            wordQueue.add(current);
        }
        if (wordQueue.isEmpty()) {
            advanceWordStage();
            return;
        }
        if (wordPosition >= wordQueue.size()) {
            wordPosition = 0;
        }
        wordAnswerShown = false;
        showWordPractice(true);
    }

    private void advanceWordStage() {
        if (wordStage == WORD_STAGE_ZH_TO_EN) {
            wordStage = WORD_STAGE_EN_TO_ZH;
            fillWordQueue();
            wordAnswerShown = false;
            showWordPractice(true);
        } else if (wordStage == WORD_STAGE_EN_TO_ZH) {
            Toast.makeText(this, "词汇练习完成", Toast.LENGTH_SHORT).show();
            showLessonModeChoice(currentLesson);
        } else {
            wordStage = WORD_STAGE_ZH_TO_EN;
            fillWordQueue();
            wordAnswerShown = false;
            showWordPractice(true);
        }
    }

    private void playWordPrompt(WordItem word) {
        if (wordStage == WORD_STAGE_ZH_TO_EN) {
            playAsset(word.chineseAudioPath, null);
        } else {
            playAsset(word.englishAudioPath, null);
        }
    }

    private void playWordAnswer(WordItem word) {
        if (wordStage == WORD_STAGE_ZH_TO_EN) {
            playAsset(word.englishAudioPath, null);
        } else {
            playAsset(word.chineseAudioPath, null);
        }
    }

    private void startSentencePromptPractice(Lesson lesson) {
        stopPlayback();
        currentLesson = lesson;
        sentenceStage = SENTENCE_STAGE_ZH_TO_EN;
        sentencePosition = 0;
        sentenceAnswerShown = false;
        fillSentenceQueue();
        showSentencePromptPractice(true);
    }

    private void fillSentenceQueue() {
        sentenceQueue.clear();
        if (currentLesson == null) {
            return;
        }
        for (int i = 0; i < currentLesson.items.size(); i++) {
            sentenceQueue.add(i);
        }
        sentencePosition = 0;
    }

    private void showSentencePromptPractice(boolean autoPlay) {
        stopPlayback();
        backAction = () -> showSentencePracticeChoice(currentLesson);
        if (currentLesson == null || currentLesson.items.isEmpty() || sentenceQueue.isEmpty()) {
            if (currentLesson != null) {
                showSentencePracticeChoice(currentLesson);
            }
            return;
        }

        SentenceItem sentence = currentLesson.items.get(sentenceQueue.get(sentencePosition));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.addView(makeTopBar(sentenceStageTitle(), v -> showSentencePracticeChoice(currentLesson)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(18), dp(28), dp(18), dp(12));

        TextView progress = new TextView(this);
        progress.setText(sentenceProgressText());
        progress.setTextColor(COLOR_MUTED);
        progress.setTextSize(15);
        progress.setGravity(Gravity.CENTER);
        body.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(24), dp(18), dp(24));
        stylePracticeCard(card);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1);
        cardLp.setMargins(0, dp(18), 0, dp(18));

        TextView prompt = new TextView(this);
        prompt.setText(sentencePromptText(sentence));
        prompt.setTextColor(COLOR_TEXT);
        prompt.setTextSize(30);
        prompt.setGravity(Gravity.CENTER);
        prompt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(prompt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView answer = new TextView(this);
        answer.setText(sentenceAnswerText(sentence));
        answer.setTextColor(COLOR_PRIMARY);
        answer.setTextSize(24);
        answer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        answerLp.setMargins(0, dp(22), 0, 0);
        card.addView(answer, answerLp);
        body.addView(card, cardLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);

        if (!sentenceAnswerShown) {
            boolean hasPromptAudio = sentencePromptHasAudio(sentence);
            if (hasPromptAudio) {
                Button repeat = makeButton("再听提示");
                repeat.setOnClickListener(v -> playSentencePrompt(sentence));
                controls.addView(repeat, fullButtonLp(0));
            }

            Button show = makePrimaryButton("查看答案");
            show.setOnClickListener(v -> {
                sentenceAnswerShown = true;
                showSentencePromptPractice(false);
                playSentenceAnswer(sentence);
            });
            controls.addView(show, fullButtonLp(hasPromptAudio ? dp(10) : 0));
        } else {
            Button listenAgain = makePrimaryButton("再听一遍");
            listenAgain.setOnClickListener(v -> playSentenceAnswer(sentence));
            controls.addView(listenAgain, fullButtonLp(0));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            Button mastered = makePrimaryButton("会了");
            mastered.setOnClickListener(v -> completeCurrentSentence(true));
            row.addView(mastered, new LinearLayout.LayoutParams(0, dp(52), 1));

            Button retry = makeButton("再练一次");
            retry.setOnClickListener(v -> completeCurrentSentence(false));
            LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(0, dp(52), 1);
            retryLp.setMargins(dp(10), 0, 0, 0);
            row.addView(retry, retryLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dp(10), 0, 0);
            controls.addView(row, rowLp);
        }

        body.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        setContentView(root);

        if (autoPlay) {
            handler.postDelayed(() -> {
                if (currentLesson != null && !sentenceQueue.isEmpty()) {
                    playSentencePrompt(currentLesson.items.get(sentenceQueue.get(sentencePosition)));
                }
            }, 220);
        }
    }

    private String sentenceStageTitle() {
        return sentenceStage == SENTENCE_STAGE_ZH_TO_EN ? "看中文，说英文" : "听英文，说中文";
    }

    private String sentenceProgressText() {
        return "没记住的会继续出现，还剩 " + sentenceQueue.size() + " 句";
    }

    private String sentencePromptText(SentenceItem sentence) {
        return sentenceStage == SENTENCE_STAGE_ZH_TO_EN ? sentence.chinese : sentence.text;
    }

    private String sentenceAnswerText(SentenceItem sentence) {
        if (!sentenceAnswerShown) {
            return sentenceStage == SENTENCE_STAGE_ZH_TO_EN ? "请说出英文句子" : "请说出中文意思";
        }
        return sentenceStage == SENTENCE_STAGE_ZH_TO_EN ? sentence.text : sentence.chinese;
    }

    private void completeCurrentSentence(boolean mastered) {
        if (sentenceQueue.isEmpty()) {
            advanceSentenceStage();
            return;
        }
        int current = sentenceQueue.remove(sentencePosition);
        if (!mastered) {
            sentenceQueue.add(current);
        }
        if (sentenceQueue.isEmpty()) {
            advanceSentenceStage();
            return;
        }
        if (sentencePosition >= sentenceQueue.size()) {
            sentencePosition = 0;
        }
        sentenceAnswerShown = false;
        showSentencePromptPractice(true);
    }

    private void advanceSentenceStage() {
        if (sentenceStage == SENTENCE_STAGE_ZH_TO_EN) {
            sentenceStage = SENTENCE_STAGE_EN_TO_ZH;
            fillSentenceQueue();
            sentenceAnswerShown = false;
            showSentencePromptPractice(true);
            return;
        }
        Toast.makeText(this, "句子提示练习完成", Toast.LENGTH_SHORT).show();
        showSentencePracticeChoice(currentLesson);
    }

    private boolean sentencePromptHasAudio(SentenceItem sentence) {
        if (sentenceStage == SENTENCE_STAGE_ZH_TO_EN) {
            return sentence.chineseAudioPath != null && !sentence.chineseAudioPath.isEmpty();
        }
        return sentence.audioPath != null && !sentence.audioPath.isEmpty();
    }

    private void playSentencePrompt(SentenceItem sentence) {
        if (sentenceStage == SENTENCE_STAGE_ZH_TO_EN) {
            playOptionalAsset(sentence.chineseAudioPath);
        } else {
            playOptionalAsset(sentence.audioPath);
        }
    }

    private void playSentenceAnswer(SentenceItem sentence) {
        if (sentenceStage == SENTENCE_STAGE_ZH_TO_EN) {
            playOptionalAsset(sentence.audioPath);
        } else {
            playOptionalAsset(sentence.chineseAudioPath);
        }
    }

    private boolean playOptionalAsset(String audioPath) {
        if (audioPath == null || audioPath.isEmpty()) {
            return false;
        }
        playAsset(audioPath, null);
        return true;
    }

    private void startTransferPractice(Lesson lesson) {
        stopPlayback();
        currentLesson = lesson;
        transferStage = TRANSFER_STAGE_ZH_TO_EN;
        transferPosition = 0;
        transferAnswerShown = false;
        fillTransferQueue();
        showTransferPractice(true);
    }

    private void fillTransferQueue() {
        transferQueue.clear();
        if (currentLesson == null) {
            return;
        }
        for (int i = 0; i < currentLesson.transferItems.size(); i++) {
            transferQueue.add(i);
        }
        transferPosition = 0;
    }

    private void showTransferPractice(boolean autoPlay) {
        stopPlayback();
        backAction = () -> showSentencePracticeChoice(currentLesson);
        if (currentLesson == null || currentLesson.transferItems.isEmpty() || transferQueue.isEmpty()) {
            if (currentLesson != null) {
                showSentencePracticeChoice(currentLesson);
            }
            return;
        }

        TransferItem transfer = currentLesson.transferItems.get(transferQueue.get(transferPosition));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.addView(makeTopBar(transferStageTitle(), v -> showSentencePracticeChoice(currentLesson)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(dp(18), dp(28), dp(18), dp(12));

        TextView progress = new TextView(this);
        progress.setText(transferProgressText());
        progress.setTextColor(COLOR_MUTED);
        progress.setTextSize(15);
        progress.setGravity(Gravity.CENTER);
        body.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(18), dp(24), dp(18), dp(24));
        stylePracticeCard(card);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1);
        cardLp.setMargins(0, dp(18), 0, dp(18));

        TextView prompt = new TextView(this);
        prompt.setText(transferPromptText(transfer));
        prompt.setTextColor(COLOR_TEXT);
        prompt.setTextSize(29);
        prompt.setGravity(Gravity.CENTER);
        prompt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(prompt, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView answer = new TextView(this);
        answer.setText(transferAnswerText(transfer));
        answer.setTextColor(COLOR_PRIMARY);
        answer.setTextSize(23);
        answer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams answerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        answerLp.setMargins(0, dp(22), 0, 0);
        card.addView(answer, answerLp);
        body.addView(card, cardLp);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);

        if (!transferAnswerShown) {
            Button repeat = makeButton("再听提示");
            repeat.setOnClickListener(v -> playTransferPrompt(transfer));
            controls.addView(repeat, fullButtonLp(0));

            Button show = makePrimaryButton("查看答案");
            show.setOnClickListener(v -> {
                transferAnswerShown = true;
                showTransferPractice(false);
                playTransferAnswer(transfer);
            });
            controls.addView(show, fullButtonLp(dp(10)));
        } else {
            Button listenAgain = makePrimaryButton("再听一遍");
            listenAgain.setOnClickListener(v -> playTransferAnswer(transfer));
            controls.addView(listenAgain, fullButtonLp(0));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);

            Button mastered = makePrimaryButton("会了");
            mastered.setOnClickListener(v -> completeCurrentTransfer(true));
            row.addView(mastered, new LinearLayout.LayoutParams(0, dp(52), 1));

            Button retry = makeButton("再练一次");
            retry.setOnClickListener(v -> completeCurrentTransfer(false));
            LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(0, dp(52), 1);
            retryLp.setMargins(dp(10), 0, 0, 0);
            row.addView(retry, retryLp);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, dp(10), 0, 0);
            controls.addView(row, rowLp);
        }

        body.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        setContentView(root);

        if (autoPlay) {
            handler.postDelayed(() -> {
                if (currentLesson != null && !transferQueue.isEmpty()) {
                    playTransferPrompt(currentLesson.transferItems.get(transferQueue.get(transferPosition)));
                }
            }, 220);
        }
    }

    private String transferStageTitle() {
        return transferStage == TRANSFER_STAGE_ZH_TO_EN
                ? "举一反三：看中文，说英文"
                : "举一反三：听英文，说中文";
    }

    private String transferProgressText() {
        return "没掌握的会继续出现，还剩 " + transferQueue.size() + " 句";
    }

    private String transferPromptText(TransferItem transfer) {
        return transferStage == TRANSFER_STAGE_ZH_TO_EN ? transfer.chinese : transfer.text;
    }

    private String transferAnswerText(TransferItem transfer) {
        if (!transferAnswerShown) {
            return transferStage == TRANSFER_STAGE_ZH_TO_EN ? "请说出英文句子" : "请说出中文意思";
        }
        return transferStage == TRANSFER_STAGE_ZH_TO_EN ? transfer.text : transfer.chinese;
    }

    private void completeCurrentTransfer(boolean mastered) {
        if (transferQueue.isEmpty()) {
            advanceTransferStage();
            return;
        }
        int current = transferQueue.remove(transferPosition);
        if (!mastered) {
            transferQueue.add(current);
        }
        if (transferQueue.isEmpty()) {
            advanceTransferStage();
            return;
        }
        if (transferPosition >= transferQueue.size()) {
            transferPosition = 0;
        }
        transferAnswerShown = false;
        showTransferPractice(true);
    }

    private void advanceTransferStage() {
        if (transferStage == TRANSFER_STAGE_ZH_TO_EN) {
            transferStage = TRANSFER_STAGE_EN_TO_ZH;
            fillTransferQueue();
            transferAnswerShown = false;
            showTransferPractice(true);
            return;
        }
        Toast.makeText(this, "举一反三练习完成", Toast.LENGTH_SHORT).show();
        showSentencePracticeChoice(currentLesson);
    }

    private void playTransferPrompt(TransferItem transfer) {
        if (transferStage == TRANSFER_STAGE_ZH_TO_EN) {
            playOptionalAsset(transfer.chineseAudioPath);
        } else {
            playOptionalAsset(transfer.audioPath);
        }
    }

    private void playTransferAnswer(TransferItem transfer) {
        if (transferStage == TRANSFER_STAGE_ZH_TO_EN) {
            playOptionalAsset(transfer.audioPath);
        } else {
            playOptionalAsset(transfer.chineseAudioPath);
        }
    }

    private void showSentenceLesson(Lesson lesson) {
        stopPlayback();
        currentLesson = lesson;
        activeIndex = -1;
        backAction = () -> showSentencePracticeChoice(lesson);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_PAGE);
        root.addView(makeTopBar(lesson.subtitle, v -> showSentencePracticeChoice(lesson)), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        lessonView = new LessonView(this);
        lessonView.setLesson(lesson);
        lessonView.setListener(index -> playItem(index, false));
        root.addView(lessonView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(dp(10), dp(8), dp(10), dp(10));
        controls.setBackgroundColor(COLOR_TOP_BAR);

        Button playAll = makePrimaryButton("顺序播放");
        playAll.setOnClickListener(v -> startSequence());
        controls.addView(playAll, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button stop = makeButton("停止");
        stop.setOnClickListener(v -> stopPlayback());
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(0, dp(52), 1);
        stopLp.setMargins(dp(10), 0, 0, 0);
        controls.addView(stop, stopLp);

        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
    }

    private void startSequence() {
        if (currentLesson == null || currentLesson.items.isEmpty()) {
            return;
        }
        playSequence = true;
        sequenceIndex = 0;
        playItem(sequenceIndex, true);
    }

    private void playItem(int index, boolean keepSequence) {
        if (currentLesson == null || index < 0 || index >= currentLesson.items.size()) {
            return;
        }
        releasePlayer();
        playSequence = keepSequence;
        activeIndex = index;
        if (lessonView != null) {
            lessonView.setActiveIndex(index);
        }

        SentenceItem item = currentLesson.items.get(index);
        playAsset(item.audioPath, mp -> {
            if (playSequence) {
                sequenceIndex++;
                if (sequenceIndex < currentLesson.items.size()) {
                    handler.postDelayed(() -> playItem(sequenceIndex, true), 280);
                } else {
                    stopPlayback();
                }
            }
        });
    }

    private void playAsset(String audioPath, MediaPlayer.OnCompletionListener completionListener) {
        releasePlayer();
        try {
            AssetFileDescriptor afd = getAssets().openFd(audioPath);
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            if (completionListener != null) {
                player.setOnCompletionListener(completionListener);
            }
            player.prepare();
            player.setVolume(1.0f, 1.0f);
            player.start();
        } catch (Exception e) {
            showError(e);
        }
    }

    private void stopPlayback() {
        playSequence = false;
        sequenceIndex = -1;
        activeIndex = -1;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        if (lessonView != null) {
            lessonView.setActiveIndex(-1);
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
    }

    private String readAssetText(String path) throws Exception {
        try (InputStream input = getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void checkForUpdates() {
        Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                UpdateInfo update = fetchLatestUpdate();
                runOnUiThread(() -> {
                    if (!update.hasUpdate) {
                        showMessage("已经是最新版", "当前版本 v" + BuildConfig.VERSION_NAME);
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("发现新版本 v" + update.versionName)
                            .setMessage("是否从 GitHub 下载并安装最新 APK？")
                            .setPositiveButton("下载升级", (dialog, which) -> downloadAndInstall(update))
                            .setNegativeButton("取消", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showMessage("更新检查失败", e.getMessage()));
            }
        }).start();
    }

    private UpdateInfo fetchLatestUpdate() throws Exception {
        JSONObject release = new JSONObject(readHttpText(RELEASE_API_URL));
        String tag = release.optString("tag_name", "");
        String versionName = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
        String apkUrl = findApkUrl(release.optJSONArray("assets"));
        if (apkUrl.isEmpty()) {
            throw new IllegalStateException("GitHub latest release did not include an APK asset");
        }
        UpdateInfo info = new UpdateInfo();
        info.versionName = versionName;
        info.apkUrl = apkUrl;
        info.hasUpdate = isNewerVersion(versionName, BuildConfig.VERSION_NAME);
        return info;
    }

    private String findApkUrl(JSONArray assets) throws Exception {
        if (assets == null) {
            return "";
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            String name = asset.optString("name", "").toLowerCase(Locale.US);
            if (name.endsWith(".apk")) {
                return asset.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.replaceAll("[^0-9.]", "").split("\\.");
        String[] currentParts = current.replaceAll("[^0-9.]", "").split("\\.");
        int count = Math.max(latestParts.length, currentParts.length);
        for (int i = 0; i < count; i++) {
            int latestValue = parseVersionPart(latestParts, i);
            int currentValue = parseVersionPart(currentParts, i);
            if (latestValue != currentValue) {
                return latestValue > currentValue;
            }
        }
        return false;
    }

    private int parseVersionPart(String[] parts, int index) {
        if (index >= parts.length || parts[index].isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String readHttpText(String urlText) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlText).openConnection();
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "EnglishReader/" + BuildConfig.VERSION_NAME);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        try (InputStream input = stream;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            String text = out.toString(StandardCharsets.UTF_8.name());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("GitHub HTTP " + status + ": " + text);
            }
            return text;
        } finally {
            connection.disconnect();
        }
    }

    private void downloadAndInstall(UpdateInfo update) {
        Toast.makeText(this, "正在下载 APK...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) {
                    throw new IllegalStateException("External downloads directory is unavailable");
                }
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Could not create downloads directory");
                }
                File apk = new File(dir, "english-reader-v" + update.versionName + ".apk");
                downloadToFile(update.apkUrl, apk);
                runOnUiThread(() -> openApkInstaller(apk));
            } catch (Exception e) {
                runOnUiThread(() -> showMessage("下载失败", e.getMessage()));
            }
        }).start();
    }

    private void downloadToFile(String urlText, File target) throws Exception {
        URL url = new URL(urlText);
        for (int redirects = 0; redirects < 5; redirects++) {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "EnglishReader/" + BuildConfig.VERSION_NAME);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(60000);
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty()) {
                    throw new IllegalStateException("Download redirect did not include Location");
                }
                url = new URL(location);
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new IllegalStateException("APK download HTTP " + status);
            }
            try (InputStream input = connection.getInputStream();
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } finally {
                connection.disconnect();
            }
            return;
        }
        throw new IllegalStateException("Too many APK download redirects");
    }

    private void openApkInstaller(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(this)
                    .setTitle("需要安装权限")
                    .setMessage("请允许 English Reader 安装未知应用。打开后返回本应用，再点一次“检查更新”。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        Intent intent = new Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apk);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, APK_MIME_TYPE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message == null ? "" : message)
                .setPositiveButton("确定", null)
                .show();
    }

    private void showError(Exception e) {
        TextView error = new TextView(this);
        error.setText("Error: " + e.getMessage());
        error.setTextColor(Color.rgb(176, 32, 32));
        error.setTextSize(16);
        error.setPadding(dp(20), dp(40), dp(20), dp(20));
        setContentView(error);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Lesson {
        String title;
        String subtitle;
        String imagePath;
        Bitmap bitmap;
        final List<WordItem> words = new ArrayList<>();
        final List<SentenceItem> items = new ArrayList<>();
        final List<TransferItem> transferItems = new ArrayList<>();
    }

    private static final class WordItem {
        String english;
        String chinese;
        String englishAudioPath;
        String chineseAudioPath;
    }

    private static final class SentenceItem {
        String text;
        String chinese;
        String audioPath;
        String chineseAudioPath;
        RectF region;
    }

    private static final class TransferItem {
        String text;
        String chinese;
        String audioPath;
        String chineseAudioPath;
    }

    private static final class UpdateInfo {
        String versionName;
        String apkUrl;
        boolean hasUpdate;
    }

    private static final class LessonView extends View {
        interface TapListener {
            void onSentenceTap(int index);
        }

        private Lesson lesson;
        private TapListener listener;
        private int activeIndex = -1;
        private int pageIndex = 0;
        private final List<RectF> cardRects = new ArrayList<>();
        private final List<Integer> cardIndexes = new ArrayList<>();
        private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float downX;
        private float downY;
        private final int touchSlop;
        private static final float CAPTION_FRACTION = 0.27f;
        private static final int ITEMS_PER_PAGE = 4;

        LessonView(Activity activity) {
            super(activity);
            setBackgroundColor(COLOR_PAGE);
            setFocusable(true);
            touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        }

        void setLesson(Lesson lesson) {
            this.lesson = lesson;
            pageIndex = 0;
            requestLayout();
            invalidate();
        }

        void setListener(TapListener listener) {
            this.listener = listener;
        }

        void setActiveIndex(int activeIndex) {
            this.activeIndex = activeIndex;
            if (activeIndex >= 0) {
                pageIndex = activeIndex / ITEMS_PER_PAGE;
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (lesson == null || lesson.bitmap == null) {
                return;
            }
            calculateCardRects(getWidth(), getHeight());

            for (int i = 0; i < cardRects.size(); i++) {
                int itemIndex = cardIndexes.get(i);
                RectF card = cardRects.get(i);
                Rect source = sourceRegion(lesson.items.get(itemIndex).region);

                cardPaint.setStyle(Paint.Style.FILL);
                cardPaint.setColor(Color.WHITE);
                canvas.drawRoundRect(card, dp(8), dp(8), cardPaint);
                canvas.drawBitmap(lesson.bitmap, source, card, bitmapPaint);

                RectF caption = captionRect(card);
                cardPaint.setStyle(Paint.Style.FILL);
                cardPaint.setColor(Color.WHITE);
                canvas.drawRect(caption, cardPaint);
                cardPaint.setStyle(Paint.Style.STROKE);
                cardPaint.setStrokeWidth(dp(1));
                cardPaint.setColor(Color.rgb(224, 229, 235));
                canvas.drawLine(caption.left, caption.top, caption.right, caption.top, cardPaint);

                cardPaint.setStyle(Paint.Style.STROKE);
                cardPaint.setStrokeWidth(dp(1));
                cardPaint.setColor(Color.rgb(196, 205, 214));
                canvas.drawRoundRect(card, dp(8), dp(8), cardPaint);

                if (itemIndex == activeIndex) {
                    overlayPaint.setStyle(Paint.Style.FILL);
                    overlayPaint.setColor(Color.argb(42, 255, 213, 79));
                    canvas.drawRoundRect(card, dp(8), dp(8), overlayPaint);
                    overlayPaint.setStyle(Paint.Style.STROKE);
                    overlayPaint.setStrokeWidth(dp(4));
                    overlayPaint.setColor(Color.rgb(255, 179, 0));
                    canvas.drawRoundRect(card, dp(8), dp(8), overlayPaint);
                }
                drawSentence(canvas, lesson.items.get(itemIndex).text, caption);
            }
            drawPageDots(canvas);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (lesson == null || listener == null) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                downX = event.getX();
                downY = event.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dx = Math.abs(event.getX() - downX);
                float dy = Math.abs(event.getY() - downY);
                if (dx > touchSlop * 2f && dx > dy) {
                    if (event.getX() < downX) {
                        nextPage();
                    } else {
                        previousPage();
                    }
                    performClick();
                } else if (dx <= touchSlop && dy <= touchSlop) {
                    calculateCardRects(getWidth(), getHeight());
                    for (int i = 0; i < cardRects.size(); i++) {
                        if (cardRects.get(i).contains(event.getX(), event.getY())) {
                            listener.onSentenceTap(cardIndexes.get(i));
                            performClick();
                            break;
                        }
                    }
                }
                return true;
            }
            return super.onTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            super.performClick();
            return true;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            if (height <= 0) {
                height = dpInt(520);
            }
            setMeasuredDimension(width, height);
        }

        private void calculateCardRects(int viewWidth, int viewHeight) {
            cardRects.clear();
            cardIndexes.clear();
            if (lesson == null || lesson.bitmap == null || viewWidth <= 0 || viewHeight <= 0) {
                return;
            }
            pageIndex = Math.max(0, Math.min(pageIndex, pageCount() - 1));
            int pageStart = pageIndex * ITEMS_PER_PAGE;
            int pageEnd = Math.min(lesson.items.size(), pageStart + ITEMS_PER_PAGE);
            int visibleCount = pageEnd - pageStart;
            if (visibleCount <= 0) {
                return;
            }

            int columns = columnCount(viewWidth);
            int rows = (int) Math.ceil(visibleCount / (float) columns);
            float padding = dp(8);
            float gap = dp(8);
            float dotsHeight = dp(28);
            float cardWidth = (viewWidth - padding * 2f - gap * (columns - 1)) / columns;
            float naturalCardHeight = cardWidth / averageItemAspectRatio();
            float maxCardHeight = (viewHeight - padding * 2f - dotsHeight - Math.max(0, rows - 1) * gap) / rows;
            float cardHeight = Math.min(naturalCardHeight, maxCardHeight);
            for (int i = 0; i < visibleCount; i++) {
                int row = i / columns;
                int column = i % columns;
                float left = padding + column * (cardWidth + gap);
                float top = padding + row * (cardHeight + gap);
                cardRects.add(new RectF(left, top, left + cardWidth, top + cardHeight));
                cardIndexes.add(pageStart + i);
            }
        }

        private int columnCount(int viewWidth) {
            float widthDp = viewWidth / getResources().getDisplayMetrics().density;
            return widthDp < 700 ? 1 : 2;
        }

        private int pageCount() {
            if (lesson == null || lesson.items.isEmpty()) {
                return 1;
            }
            return (int) Math.ceil(lesson.items.size() / (float) ITEMS_PER_PAGE);
        }

        private void nextPage() {
            if (pageIndex < pageCount() - 1) {
                pageIndex++;
                invalidate();
            }
        }

        private void previousPage() {
            if (pageIndex > 0) {
                pageIndex--;
                invalidate();
            }
        }

        private void drawPageDots(Canvas canvas) {
            int count = pageCount();
            if (count <= 1) {
                return;
            }
            float radius = dp(4);
            float gap = dp(14);
            float totalWidth = count * radius * 2f + (count - 1) * gap;
            float x = (getWidth() - totalWidth) / 2f + radius;
            float y = getHeight() - dp(14);
            for (int i = 0; i < count; i++) {
                cardPaint.setStyle(Paint.Style.FILL);
                cardPaint.setColor(i == pageIndex ? COLOR_PRIMARY : Color.rgb(190, 199, 209));
                canvas.drawCircle(x, y, radius, cardPaint);
                x += radius * 2f + gap;
            }
        }

        private float averageItemAspectRatio() {
            float total = 0f;
            for (SentenceItem item : lesson.items) {
                total += itemAspectRatio(item.region);
            }
            return Math.max(1f, total / lesson.items.size());
        }

        private float itemAspectRatio(RectF region) {
            float sourceWidth = region.width() * lesson.bitmap.getWidth();
            float sourceHeight = region.height() * lesson.bitmap.getHeight();
            return sourceWidth / Math.max(1f, sourceHeight);
        }

        private Rect sourceRegion(RectF normalized) {
            int left = Math.round(normalized.left * lesson.bitmap.getWidth());
            int top = Math.round(normalized.top * lesson.bitmap.getHeight());
            int right = Math.round(normalized.right * lesson.bitmap.getWidth());
            int bottom = Math.round(normalized.bottom * lesson.bitmap.getHeight());
            return new Rect(left, top, right, bottom);
        }

        private RectF captionRect(RectF card) {
            float captionHeight = Math.max(dp(42), card.height() * CAPTION_FRACTION);
            float inset = dp(2);
            return new RectF(
                    card.left + inset,
                    card.bottom - captionHeight,
                    card.right - inset,
                    card.bottom - inset);
        }

        private void drawSentence(Canvas canvas, String sentence, RectF region) {
            float size = Math.max(sp(13), Math.min(sp(25), region.height() * 0.46f));
            textPaint.setTextSize(size);
            textPaint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.BOLD));

            String[] parts = sentence.split(" ");
            float space = textPaint.measureText(" ");
            float total = measureSentence(parts, space);
            float maxWidth = region.width() - dp(16);
            while (total > maxWidth && size > sp(11)) {
                size -= sp(0.5f);
                textPaint.setTextSize(size);
                space = textPaint.measureText(" ");
                total = measureSentence(parts, space);
            }

            float x = region.centerX() - total / 2f;
            Paint.FontMetrics metrics = textPaint.getFontMetrics();
            float y = region.centerY() - (metrics.ascent + metrics.descent) / 2f;

            for (int i = 0; i < parts.length; i++) {
                String part = parts[i];
                String clean = part.replace(".", "").replace(",", "").replace("'", "").toLowerCase();
                if (i == 0) {
                    textPaint.setColor(COLOR_PRIMARY);
                } else if ("not".equals(clean)) {
                    textPaint.setColor(Color.rgb(211, 47, 47));
                } else {
                    textPaint.setColor(Color.rgb(38, 50, 56));
                }
                canvas.drawText(part, x, y, textPaint);
                x += textPaint.measureText(part) + space;
            }
        }

        private float measureSentence(String[] parts, float space) {
            float total = 0f;
            for (String part : parts) {
                total += textPaint.measureText(part);
            }
            return total + space * Math.max(0, parts.length - 1);
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }

        private int dpInt(float value) {
            return Math.round(dp(value));
        }

        private float sp(float value) {
            return value * getResources().getDisplayMetrics().scaledDensity;
        }
    }
}
