package com.example.pedestrian.signlanguageinteraction;

import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.iflytek.cloud.SpeechConstant.ENGINE_TYPE;
import static com.iflytek.cloud.SpeechConstant.KEY_REQUEST_FOCUS;
import static com.iflytek.cloud.SpeechConstant.PARAMS;
import static com.iflytek.cloud.SpeechConstant.PITCH;
import static com.iflytek.cloud.SpeechConstant.SPEED;
import static com.iflytek.cloud.SpeechConstant.STREAM_TYPE;
import static com.iflytek.cloud.SpeechConstant.TYPE_LOCAL;
import static com.iflytek.cloud.SpeechConstant.VOICE_NAME;
import static com.iflytek.cloud.SpeechConstant.VOLUME;

public class MainActivity extends AppCompatActivity {

    private Socket socket;
    private EditText editText;
    private Button connectButton;
//    private Button disconnectButton;
    private TextView iat;
    private TextView tts;
    private Button start;
    private int ret = 0;
    private Toast mToast;
    private Boolean flag = true;

    private SpeechRecognizer mIat;
    private SpeechSynthesizer mTts;
    private HashMap<String, String> mIatResults = new LinkedHashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SpeechUtility.createUtility(MainActivity.this, SpeechConstant.APPID+"=581c7563");

        editText = (EditText) findViewById(R.id.ipAddress);
        connectButton = (Button) findViewById(R.id.connect);
//        disconnectButton = (Button) findViewById(R.id.disconnect);
        iat = (TextView) findViewById(R.id.iat);
        tts = (TextView) findViewById(R.id.tts);
        start = (Button) findViewById(R.id.start);

        mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);

        mIat = SpeechRecognizer.createRecognizer(MainActivity.this, mInitListener);
        mTts = SpeechSynthesizer.createSynthesizer(MainActivity.this, mInitListener);

        mIat_setParams();
        mTts_setParam();

        final Handler handler = new Handler(){
            @Override
            public void handleMessage(Message msg){
                super.handleMessage(msg);
                if(msg.what == 1){
                    String data = (String)msg.obj;
                    tts.setText(data);
                    ret = mTts.startSpeaking(data, mTtsListener);
                    if (ret != ErrorCode.SUCCESS) {
                        mToast.setText("合成失败,错误码: " + ret);
                        mToast.show();
                    }
                }
                else if(msg.what == 2){
                    mToast.setText("Socket链接出现问题，原因可能是：Invalid Ip Address");
                    mToast.show();
                }
                else{
                    connectButton.setText("disconnect");
                    mToast.setText("Socket已经链接");
                    mToast.show();
                }
            }
        };

        connectButton.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (flag) {
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                socket = new Socket(editText.getText().toString(), 9527);
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            BufferedReader recv = new BufferedReader(new InputStreamReader(socket.getInputStream(), "utf-8"));
                                            String line;
                                            while (socket.isConnected()) {
                                                if ((line = recv.readLine()) != null) {
                                                    Message message = new Message();
                                                    message.what = 1;
                                                    message.obj = line;
                                                    handler.sendMessage(message);
                                                }
                                            }
                                        } catch (IOException e) {
                                        }
                                    }
                                }).start();
                                flag = false;
                                Message message = new Message();
                                message.what = 3;
                                handler.sendMessage(message);
                            } catch (IOException e) {
                                Message message = new Message();
                                message.what = 2;
                                handler.sendMessage(message);
                            }
                        }
                    }.start();
                }
                else{
                    try {
                        iat.setText(null);
                        tts.setText(null);
                        socket.close();
                        flag = true;
                        connectButton.setText("connect");
                        mToast.setText("Socket已经断开");
                        mToast.show();
                    } catch (IOException e) {
                        mToast.setText("socket关闭出现问题！");
                        mToast.show();
                    }
                }
            }
        });

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mTts.isSpeaking())
                    mTts.stopSpeaking();
                iat.setText(null);
                mIatResults.clear();
                ret = mIat.startListening(mIatListener);
                if (ret != ErrorCode.SUCCESS) {
                    mToast.setText("听写失败,错误码：" + ret);
                    mToast.show();
                }
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState){
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putString("message", editText.getText().toString());
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);
        String message = savedInstanceState.getString("message");
        editText.setText(message);
    }

    private InitListener mInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            if (code != ErrorCode.SUCCESS) {
                mToast.setText("初始化失败,错误码："+code);
                mToast.show();
            }
        }
    };

    private RecognizerListener mIatListener = new RecognizerListener() {

        @Override
        public void onBeginOfSpeech() {
            mToast.setText("语音听写开始");
            mToast.show();
        }

        @Override
        public void onError(SpeechError error) {
            mToast.setText("语音听写没有结果");
            mToast.show();
        }

        @Override
        public void onEndOfSpeech() {
            // 此回调表示：检测到了语音的尾端点，已经进入识别过程，不再接受语音输入
            mToast.setText("语音听写结束");
            mToast.show();
        }

        @Override
        public void onResult(RecognizerResult results, boolean isLast) {
            String text = JsonParser.parseIatResult(results.getResultString());
            String sn = null;
            // 读取json结果中的sn字段
            try {
                JSONObject resultJson = new JSONObject(results.getResultString());
                sn = resultJson.optString("sn");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            mIatResults.put(sn, text);

            final StringBuffer resultBuffer = new StringBuffer();
            for (String key : mIatResults.keySet()) {
                resultBuffer.append(mIatResults.get(key));
            }

            if (isLast) {
                // TODO 最后的结果
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        iat.setText(resultBuffer.toString());
                    }
                });
            }
        }

        @Override
        public void onVolumeChanged(int volume, byte[] data) {
            mToast.setText("当前正在说话，音量大小：" + volume);
            mToast.show();
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    private SynthesizerListener mTtsListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {
            mToast.setText("语音合成开始");
            mToast.show();
        }

        @Override
        public void onSpeakPaused() {
        }

        @Override
        public void onSpeakResumed() {
        }

        @Override
        public void onBufferProgress(int percent, int beginPos, int endPos,
                                     String info) {
        }

        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
        }

        @Override
        public void onCompleted(SpeechError error) {
            if (error == null) {
                mToast.setText("语音合成结束");
                mToast.show();
            } else if (error != null) {
                mToast.setText(error.getPlainDescription(true));
                mToast.show();
            }
        }

        @Override
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj) {
        }
    };

    public void mIat_setParams() {
        mIat.setParameter(PARAMS, null);
        mIat.setParameter(ENGINE_TYPE, TYPE_LOCAL);
        mIat.setParameter(SpeechConstant.RESULT_TYPE, "json");
        mIat.setParameter(SpeechConstant.LANGUAGE, "zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin ");
        mIat.setParameter(SpeechConstant.VAD_BOS,"4000");
        mIat.setParameter(SpeechConstant.VAD_EOS,"1000");
        // 设置标点符号,设置为"0"返回结果无标点,设置为"1"返回结果有标点
        mIat.setParameter(SpeechConstant.ASR_PTT,"1");
        mIat.setParameter(SpeechConstant.AUDIO_FORMAT,"wav");
        mIat.setParameter(SpeechConstant.ASR_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/讯飞语音平台/iat.wav");
    }

    private void mTts_setParam(){
        mTts.setParameter(PARAMS, null);
        mTts.setParameter(ENGINE_TYPE, TYPE_LOCAL);
        mTts.setParameter(VOICE_NAME, "xiaoyan");
        mTts.setParameter(SPEED, "50");
        mTts.setParameter(PITCH, "50");
        mTts.setParameter(VOLUME, "50");
        //设置播放器音频流类型
        mTts.setParameter(STREAM_TYPE, "3");
        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(KEY_REQUEST_FOCUS, "true");
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory()+"/讯飞语音平台/tts.wav");
    }
}
