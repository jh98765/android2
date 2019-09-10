package grad_project.myapplication;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class NotiService extends Service {
    SharedPreferences infoData;
    IBinder mBinder = new NotiBinder();
    boolean is_notify = false;  // 커스텀 노티 기능 ON/OFF 여부(SharedPreferences 사용)
    boolean is_start = false;   // 관람 시작 여부
    boolean is_end = false;     // 관람 종료 여부
    String s_id = "";             // 로그인된 사용자 아이디
    String s_stateNoti = "";     // 커스텀 노티에 표시될 관람 상태 내용(관람 시작 전/관람 진행 중/관람 종료)
    long l_startTime = 0;       // 시작시간 서버에서 받아옴
    long l_nowTime = 0;         // 현재 시간 값 저장
    long l_elapseTime = -1;     // 진행시간 계산(현재시간-시작시간)
    String s_elapseTime = "";   // 진행시간 커스텀 노티에 표시하기 위함
    int i_state = -1;           // 현재 진행 상태(0:시작 안함, 1:진행 중, 2:종료)
    boolean is_notification;    // 커스텀 노티가 띄워져 있는지 여부
    boolean is_defaultNoti;     // 기본 노티가 띄워져 있는지 여부
    boolean is_warned;          // 위치 벗어났을 때 1회 경고가 되었는지 여부
    boolean is_inLocation;      // 검사한 위치 상태 임시 저장용 변수
    int location_count;         // 위치 검사 결과가 연속 false일 때 초기화하기 위해 카운트

    /* 노티 채널 */
    Notification notification;
    NotificationManager notiManager, notiManager_default, notiManager_warning;
    final String channelId = "notiChannel";
    final String channelName = "관람 상태 알림";
    final String channelId_default = "notiChannel_2";
    final String channelName_default = "위치 검사";
    final String channelId_warning = "notiChannel_warning";
    final String channelName_warning = "경고";
    final int NOTIFICATION_ID = 1;
    final int NOTIFICATION_ID_default = 2;
    final int NOTIFICATION_ID_warning = 3;
    int requestId, requestId_default, requestId_warning;
    NotificationCompat.Builder builder;
    NotificationCompat.Builder builder_default;
    NotificationCompat.Builder builder_warning;
    final int nFLAG_INITIALIZE = 0;
    final int nFLAG_START = 1;
    final int nFLAG_FINISH = 2;
    final int nFLAG_TIME = 10;

    /* 타이머 */
    final int NETWORK_DELAY = 60; // 네트워크 통신 시간 간격 : 값 변경할 때는 초 단위로 변경
    StateTimerHandler stateTimerhandler;
    final int START_TIMER_START = 100;

    final int END_TIMER_START = 200;
    TimeTimerHandler timeTimerHandler;
    final int NOWTIME_TIMER_START = 101;

    final int LOCATION_DELAY = 10; // 위치검사 시간 간격 : 값 변경할 때는 초 단위로 변경
    LocationTimerHandler locationTimerHandler;
    final int LOCATION_TIMER_START = 300;

    /***** php 통신 *****/
    private static final String BASE_PATH = "http://35.221.108.183/android/";

    public static final String GET_ISSTART = BASE_PATH + "get_isStart.php";              //시작여부(성공 1, 실패 0 반환)
    public static final String GET_ISEND = BASE_PATH + "get_isEnd.php";    //전시 종료 여부 받기(종료됨 : 시간, 종료안됨 : 0)

    class NotiBinder extends Binder {
        NotiService getService() {
            return NotiService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("NotiService", "서비스 onBind 시작");

        return mBinder;
    }
    @Override
    public void onCreate() {
        super.onCreate();

        // 사용자 아이디와 커스텀 노티 ON/OFF 여부 가져옴
        infoData = getSharedPreferences("infoData", MODE_PRIVATE);
        s_id = infoData.getString("ID", "");
        is_notify = infoData.getBoolean("NOTIFICATION", false);

        // 초기화
        is_notification = false;
        stateTimerhandler = new StateTimerHandler();
        timeTimerHandler = new TimeTimerHandler();
        locationTimerHandler = new LocationTimerHandler();

        location_count = 0;
        is_inLocation = false;
        is_warned = false;

        notiManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notiManager_default = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notiManager_warning = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 오레오 이상 노티 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 커스텀 노티 생성할 알림 채널(중요도 LOW:무음)
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel mChannel = new NotificationChannel(channelId, channelName, importance);
            mChannel.setShowBadge(false);
            notiManager.createNotificationChannel(mChannel);
            // 커스텀 노티 기능 OFF일 때 기본 생성될 알림 채널(중요도 NONE:무음 및 최소화인데 최소화는 자동으로 안되는듯)
            int importance_default = NotificationManager.IMPORTANCE_NONE;
            NotificationChannel mChannel_default = new NotificationChannel(channelId_default, channelName_default, importance_default);
            mChannel_default.setShowBadge(false);
            notiManager_default.createNotificationChannel(mChannel_default);
            // 위치 이탈했을 때 경고 알림용 채널(중요도 HIGH:소리 및 팝업)
            int importance_warning = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel mChannel_warning = new NotificationChannel(channelId_warning, channelName_warning, importance_warning);
            notiManager_warning.createNotificationChannel(mChannel_warning);
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("NotiService", "서비스 시작");

        // 서비스 시작하면 시작 여부와 종료 여부(시작했을 때) 받아옴
        if (!getStartState()) { // 네트워크 통신 오류 예외처리
                Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
        }
        if (is_start) {
            if (!isEnd()) { // 네트워크 통신 오류 예외처리
                Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
            }
        }

        // 노티피케이션 생성
        if (is_notify) {    // 사용자가 알림 기능을 ON 했으면
            createNotification();   // 커스텀 노티 만들고
            startForeground(NOTIFICATION_ID, notification); // Foreground로 실행(이래야 앱이 꺼져도 서비스가 안죽음)
            is_notification = true;
            if (is_defaultNoti) {   // 기본 노티가 띄워져 있으면 죽임
                notiManager_default.cancel(NOTIFICATION_ID_default);
                is_defaultNoti = false;
            }
            // 시작여부 일정 시간마다 받아오는 핸들러 실행
            stateTimerhandler.sendEmptyMessage(START_TIMER_START);
            timeTimerHandler.sendEmptyMessage(NOWTIME_TIMER_START);
        } else {    // 사용자가 알림 기능을 OFF 했으면
            // 오레오 이상 버전은 죽지 않는 서비스를 실행하기 위해 노티를 꼭 띄워야 함
            // 오레오 미만은 노티 띄울 필요 없어서 따로 동작하는 코드 없음
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 기본적인 노티 생성 과정
                builder_default = new NotificationCompat.Builder(getApplicationContext(), channelId_default);

                Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                requestId_default = (int) System.currentTimeMillis();

                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                        requestId_default, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                builder_default.setContentTitle("독립기념관 관람")
                        .setContentText("앱 열기")
                        .setDefaults(Notification.DEFAULT_LIGHTS)
                        .setAutoCancel(false)
                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        .setSmallIcon(android.R.drawable.btn_star)
                        .setOngoing(true)
                        .setContentIntent(pendingIntent);

                Notification temp_notification;
                temp_notification = builder_default.build();

                // 노티 빌드 후 foreground로 실행
                startForeground(NOTIFICATION_ID_default,temp_notification);
                is_defaultNoti = true;
                if (is_notification) {
                    notiManager.cancel(NOTIFICATION_ID);
                    is_notification = false;
                }
                stateTimerhandler.sendEmptyMessage(START_TIMER_START);
            }
        }
        locationTimerHandler.sendEmptyMessage(LOCATION_TIMER_START);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d("NotiService", "서비스 종료");

        notiManager.cancel(NOTIFICATION_ID);
        notiManager_default.cancel(NOTIFICATION_ID_default);

        is_notification = false;
        is_defaultNoti = false;

        stateTimerhandler.removeMessages(START_TIMER_START);
        stateTimerhandler.removeMessages(END_TIMER_START);
        timeTimerHandler.removeMessages(NOWTIME_TIMER_START);
        locationTimerHandler.removeMessages(LOCATION_TIMER_START);

        Intent intent = new Intent("Service Destroyed");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        super.onDestroy();
    }

    public boolean checkInLocation() {
        boolean check = true;
        return check;
    }

    public void setOutLocation() {
        Log.d("위치 초기화", "위치 초기화!!");
//        stopSelf(); // 서비스 끄면 안되고... 다른 조치

        // 초기화되었다는 정보를 broadcast 한다
        Intent intent = new Intent("Process Initialized");
        LocalBroadcastManager.getInstance(NotiService.this).sendBroadcast(intent);
    }

    // 지정한 시간 단위로 시작/종료 여부 서버에서 받아옴
    private class StateTimerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == START_TIMER_START) {
                if (is_inLocation) {
                    // 관람 시작 전 : 관람 시작 여부 검사
                    if (getStartState()) {
                        Log.d("ISSTART_SERVICE", Boolean.toString(is_start));
                        // 관람 시작 했으면 관람 진행 상태로 넘어감
                        if (is_start) {
                            stateTimerhandler.removeMessages(START_TIMER_START);
                            if (i_state == 0) {
                                s_stateNoti = "관람 진행 중";
                                i_state = 1;
                                updateNotification(nFLAG_START);
                            }
                            this.sendEmptyMessage(END_TIMER_START);
                            Log.d("START TIMER", "RUNNING");
                        } else {
                            this.sendEmptyMessageDelayed(START_TIMER_START, NETWORK_DELAY * 1000);
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.d("ISINLOCATION_SERVICE", "밖에 나가있음");
                    this.sendEmptyMessageDelayed(START_TIMER_START, NETWORK_DELAY * 1000);
                }
            }
            // 관람 진행 중 : 관람 종료 여부 및 초기화 여부 검사
            else if (msg.what == END_TIMER_START) {
                if (is_inLocation) {    // 독립기념관 내부에 있을 때만 검사
                    if (getStartState()) {
                        // 서버에서 관람기록 초기화했을 경우 앱에서도 초기화
                        if (!is_start) {
                            if (i_state == 1) {
                                stateTimerhandler.removeMessages(END_TIMER_START);
                                this.sendEmptyMessage(START_TIMER_START);
                                s_stateNoti = "관람 시작 전";
                                updateNotification(nFLAG_INITIALIZE);
                                i_state = 0;
                            }
                        }
                    }
                    if (isEnd()) {
                        // 관람 종료되었으면 관람 종료했다고 띄워줌
                        if (is_end) {
                            stateTimerhandler.removeMessages(END_TIMER_START);
                            timeTimerHandler.removeMessages(NOWTIME_TIMER_START);
                            s_stateNoti = "관람 종료";
                            i_state = 2;
                            updateNotification(nFLAG_FINISH);
                            Log.d("END TIMER", "RUNNING");
                            stopSelf();
                        } else {
                            this.sendEmptyMessageDelayed(END_TIMER_START, NETWORK_DELAY * 1000);
                        }
                    } else {
                        Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Log.d("ISINLOCATION_SERVICE", "밖에 나가있음");
                this.sendEmptyMessageDelayed(END_TIMER_START, NETWORK_DELAY * 1000);
            }
        }
    }

    // 경과시간 새로고침
    private class TimeTimerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == NOWTIME_TIMER_START) {
                Log.d("TimeTimerHandler", "Running");
                if (is_start) {
                    l_nowTime = System.currentTimeMillis();
                    l_elapseTime = l_nowTime - l_startTime;
                    long temp_time = l_elapseTime / 1000;
                    if (temp_time % 60 >= 0 && temp_time % 60 < 30) {
                        Date elapseDate = new Date(l_elapseTime);
                        SimpleDateFormat sdfElapse = new SimpleDateFormat("HH:mm", Locale.getDefault());
                        sdfElapse.setTimeZone(TimeZone.getTimeZone("UTC"));
                        s_elapseTime = sdfElapse.format(elapseDate);
                        updateNotification(nFLAG_TIME);
                        Log.d("TIME TIMER", "NOTI UPDATE");
                    }
                    Log.d("TIME TIMER", Long.toString(l_elapseTime));
                    this.sendEmptyMessageDelayed(NOWTIME_TIMER_START, 30000);
                } else {
                    this.sendEmptyMessageDelayed(NOWTIME_TIMER_START, 30000);
                }
            }
        }
    }

    // 시간단위 위치추적
    private class LocationTimerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == LOCATION_TIMER_START) {
                if (checkInLocation()) {
                    location_count = 0;
                    is_inLocation = true;
                    is_warned = false;
                    Log.d("LOCATIONTIMER", "영역 안에 있음");
                    this.sendEmptyMessageDelayed(LOCATION_TIMER_START, LOCATION_DELAY * 1000);
                } else {
                    location_count++;
                    if (location_count > 3) {
                        is_inLocation = false;
                        Log.d("LOCATIONTIMER", "영역 밖에 있음");
                        if (is_start) {
                            if (is_warned) {
                                location_count = 0;
                                is_warned = false;
                                builder_warning.setContentText("관람 기록이 초기화되었습니다.\n안내센터에 문의하세요.");
                                notiManager_warning.notify(NOTIFICATION_ID_warning, builder_warning.build());

                                setOutLocation();
                            } else {
                                builder_warning = new NotificationCompat.Builder(getApplicationContext(), channelId_warning);

                                Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
                                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

                                requestId_warning = (int) System.currentTimeMillis();

                                PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                                        requestId_warning, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                                builder_warning.setContentTitle("경고")
                                        .setContentText("외부로 나가면 관람기록이 초기화됩니다!")
                                        .setDefaults(Notification.DEFAULT_ALL)
                                        .setAutoCancel(false)
                                        .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                                        .setSmallIcon(android.R.drawable.btn_star)
                                        .setContentIntent(pendingIntent);

                                notiManager_warning.notify(NOTIFICATION_ID_warning, builder_warning.build());
                                is_warned = true;
                                location_count = 0;
                            }
                        }
                    }

                    this.sendEmptyMessageDelayed(LOCATION_TIMER_START, LOCATION_DELAY * 1000);
                }
            }
        }
    }

    public void createNotification() {

        if (getStartState()) {
            if (is_start) {
                s_stateNoti = "관람 진행 중";
                i_state = 1;
                if (isEnd()) {
                    if (is_end) {
                        s_stateNoti = "관람 종료";
                        i_state = 2;
                    }
                } else {    // 네트워크 통신 오류 예외처리
//                Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
                    s_stateNoti = "Error";
                    i_state = -1;
                }
            } else {
                s_stateNoti = "관람 시작 전";
                i_state = 0;
            }
        } else {    // 네트워크 통신 오류 예외처리
//                Toast.makeText(getApplicationContext(), "네트워크 통신 오류", Toast.LENGTH_SHORT).show();
            s_stateNoti = "Error";
            i_state = -1;
        }

        if (is_start) {
            l_nowTime = System.currentTimeMillis();
            l_elapseTime = l_nowTime - l_startTime;
        }

//        // 오레오 이상 노티 채널 생성
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            int importance = NotificationManager.IMPORTANCE_LOW;
//            NotificationChannel mChannel = new NotificationChannel(channelId, channelName, importance);
//            notiManager.createNotificationChannel(mChannel);
//        }

        builder = new NotificationCompat.Builder(getApplicationContext(), channelId);

        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);

        requestId = (int) System.currentTimeMillis();

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(),
                requestId, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent in_openMain = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi_openMain = PendingIntent.getActivity(getApplicationContext(), requestId, in_openMain, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent in_openCheck = new Intent(getApplicationContext(), CheckActivity.class);
        PendingIntent pi_openCheck = PendingIntent.getActivity(getApplicationContext(), requestId, in_openCheck, PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification);
        if (is_start) {
            Date elapseDate = new Date(l_elapseTime);
            SimpleDateFormat sdfElapse = new SimpleDateFormat("HH:mm", Locale.getDefault());
            sdfElapse.setTimeZone(TimeZone.getTimeZone("UTC"));
            s_elapseTime = sdfElapse.format(elapseDate);
            remoteViews.setTextViewText(R.id.tv_time, s_elapseTime);
        } else {
            remoteViews.setTextViewText(R.id.tv_time, "-");
        }
        remoteViews.setTextViewText(R.id.tv_state, s_stateNoti);
        remoteViews.setOnClickPendingIntent(R.id.bt_openMain, pi_openMain);
        remoteViews.setOnClickPendingIntent(R.id.bt_openCheck, pi_openCheck);
        builder.setContent(remoteViews)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setAutoCancel(false)
//                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setSmallIcon(android.R.drawable.btn_star)
                .setOngoing(true)
                .setContentIntent(pendingIntent);

        notification = builder.build();

//        notiManager.notify(NOTIFICATION_ID, notification);
    }

    public void updateNotification(int flag) {
        RemoteViews remoteViews = new RemoteViews(getPackageName(), R.layout.notification);

        switch (flag) {
            case nFLAG_INITIALIZE :
                remoteViews.setTextViewText(R.id.tv_state, s_stateNoti);
                remoteViews.setTextViewText(R.id.tv_time, "-");
//                builder.setDefaults(Notification.DEFAULT_ALL);
                break;
            case nFLAG_START :
                remoteViews.setTextViewText(R.id.tv_state, s_stateNoti);
                remoteViews.setTextViewText(R.id.tv_time, s_elapseTime);
//                builder.setDefaults(Notification.DEFAULT_ALL);
                break;
            case nFLAG_FINISH :
                remoteViews.setTextViewText(R.id.tv_state, s_stateNoti);
                remoteViews.setTextViewText(R.id.tv_time, "-");
//                builder.setDefaults(Notification.DEFAULT_ALL);
                break;
            case nFLAG_TIME :
                remoteViews.setTextViewText(R.id.tv_state, s_stateNoti);
                remoteViews.setTextViewText(R.id.tv_time, s_elapseTime);
//                builder.setDefaults(Notification.DEFAULT_LIGHTS);
                break;
        }

        Intent in_openMain = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pi_openMain = PendingIntent.getActivity(getApplicationContext(), requestId, in_openMain, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent in_openCheck = new Intent(getApplicationContext(), CheckActivity.class);
        PendingIntent pi_openCheck = PendingIntent.getActivity(getApplicationContext(), requestId, in_openCheck, PendingIntent.FLAG_UPDATE_CURRENT);

        remoteViews.setOnClickPendingIntent(R.id.bt_openMain, pi_openMain);
        remoteViews.setOnClickPendingIntent(R.id.bt_openCheck, pi_openCheck);
        builder.setContent(remoteViews);

        notiManager.notify(NOTIFICATION_ID, builder.build());
    }

    /* DB-서버 통신 파트 */
    // 관람 시작이 되었는지 여부 받아오는 메소드(연결 상태 return)
    public boolean getStartState() {
        // 관람 시작 여부
        GetIsStartTask startTask = new GetIsStartTask(this);
        try {
            String result = startTask.execute(GET_ISSTART, s_id).get();
            Log.d("GETSTART RESULT", result);
            if (result.equals("ERROR")) {
                return false;
            } else {
                is_start = !result.equals("0");
                if (is_start) {
                    SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA);
                    l_startTime = df.parse(result).getTime();
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isEnd() {
        FinishTask finishTask = new FinishTask(this);
        try {
            String result = finishTask.execute(GET_ISEND, s_id).get();
            Log.d("ISEND RESULT", result);
            if (result.equals("ERROR")) {
                return false;
            } else {
                is_end = !result.equals("0");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /***** 서버 통신 *****/
    // 관람 시작 여부 받아오는 부분
    public static class GetIsStartTask extends AsyncTask<String, Void, String> {

        GetIsStartTask(NotiService context) {
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            /*출력값*/
        }
        @Override
        protected String doInBackground(String... params) {
            String serverURL = params[0];
            String id = params[1];
            String postParameters = "&id=" + id;
            try {
                URL url = new URL(serverURL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.connect();
                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(postParameters.getBytes("UTF-8"));
                outputStream.flush();
                outputStream.close();
                int responseStatusCode = httpURLConnection.getResponseCode();
                InputStream inputStream;
                if(responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                }
                else{
                    inputStream = httpURLConnection.getErrorStream();
                }
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                bufferedReader.close();
                return sb.toString();
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
    }

    // 관람 종료 여부 정보 받아오기
    public static class FinishTask extends AsyncTask<String, Void, String> {

        FinishTask(NotiService context) {

        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }
        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
        }
        @Override
        protected String doInBackground(String... params) {
            String serverURL = params[0];
            String id = params[1];
            String postParameters = "&id=" + id;
            try {
                URL url = new URL(serverURL);
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                httpURLConnection.setReadTimeout(5000);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.connect();
                OutputStream outputStream = httpURLConnection.getOutputStream();
                outputStream.write(postParameters.getBytes("UTF-8"));
                outputStream.flush();
                outputStream.close();
                int responseStatusCode = httpURLConnection.getResponseCode();
                InputStream inputStream;
                if(responseStatusCode == HttpURLConnection.HTTP_OK) {
                    inputStream = httpURLConnection.getInputStream();
                }
                else{
                    inputStream = httpURLConnection.getErrorStream();
                }
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = bufferedReader.readLine()) != null){
                    sb.append(line);
                }
                bufferedReader.close();
                return sb.toString();
            } catch (Exception e) {
                return "ERROR";
            }
        }
    }
}
