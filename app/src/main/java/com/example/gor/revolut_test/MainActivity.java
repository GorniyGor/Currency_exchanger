package com.example.gor.revolut_test;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.util.Log;
import android.widget.EditText;

import com.example.gor.revolut_test.Internet.LoadService;

public class MainActivity extends AppCompatActivity {

    public final static String BR_ACTION = "com.example.gor.revolut_test";
    public final static String UPDATE_ACTION = "update data into recycler";
    public final static String LOAD_ACTION = "com.example.gor.revolut_test_load_data";

    private LoadService service;
    private ServiceConnection serviceConnection;
    private RecyclerBroadcastReceiver broadcastReceiver = new RecyclerBroadcastReceiver();

    private EditText editCashAmountView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        registerReceiver(broadcastReceiver, new IntentFilter(BR_ACTION));


 /*       Toolbar mToolbar = (Toolbar) findViewById(R.id.id_toolbar_fx);
        setSupportActionBar(mToolbar);*/

        //--Service----------------------------------
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                LoadService.MyBinder b = (LoadService.MyBinder) binder;
                service = b.getService();
                sendBroadcast(new Intent().setAction(BR_ACTION));
                Log.d(CurrencyList.TAG,"onServiceConnected: getService" );
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                service = null;
            }
        };

        Intent intent = new Intent(this, LoadService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
        //----------------------------------------------------------------


    }

    @Override
    protected void onStop() {
        super.onStop();
        AlarmManager alarmManagerCancled = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        alarmManagerCancled.cancel(PendingIntent.getBroadcast(this, 0,
                new Intent(this, LoadBroadcastReceiver.class), 0));
        unbindService(serviceConnection);/*
        unregisterReceiver(new RecyclerBroadcastReceiver.DataUpdateBroadcastReceiver());*/
        unregisterReceiver(broadcastReceiver);
    }

    public class RecyclerBroadcastReceiver extends BroadcastReceiver {
        private static final String idRecyclerTop = "TOP";
        private static final String idRecyclerBottom = "BOTTOM";

        RecyclerView mRecyclerViewTop;
        RecyclerView mRecyclerViewBottom;
        private RecyclerAdapter mRecyclerAdapterTop;
        private RecyclerAdapter mRecyclerAdapterBottom;
        private CurrencyList mCurList = CurrencyList.getInstance();
        private AlarmManager mAlarmManager;


        @Override
        public void onReceive(Context context, Intent intent) {

                registerReceiver(new DataUpdateBroadcastReceiver(), new IntentFilter(UPDATE_ACTION));

            //--Для обновления ресайклера, когда ввели сумму, которую нужно перевести
            //--Problem--Когда происходит скачивание новой партии данных - эдитор обнуляется! - не всегда
            //--maybeProblem--При изменении валюты, в которую переводят, обнуляется исходная. - просто не как в Revolut

            //--Problem--Ситауция, когда в обоих ресайклерах есть ненулевой cash, как обрабатывается это?
            //--Problem--Бывает неправильное обновление курсов!
            mCurList.setDataChangedListener(new CurrencyList.DataChangedListener() {
                @Override
                public void onNotify(String adapterName) {
                    sendBroadcast(new Intent().setAction(UPDATE_ACTION).
                            putExtra("adapter", adapterName));
                }
            });

            mCurList.initCashToExchange(idRecyclerTop);
            mCurList.initCashToExchange(idRecyclerBottom);

                //--Вызывается когда произошла закачка данных из сети--
                LoadService.NotifyListener mNotifyListener = new LoadService.NotifyListener() {
                    @Override
                    public void onNotify() {
                        if(mCurList.getCurrentlyExchangeSize() < 2){
                            mCurList.setCurrentlyExchange(mRecyclerViewTop, 0);
                            mCurList.setCurrentlyExchange(mRecyclerViewBottom, 0);

                        }

                        //--Problem--Почему то не меняется вид сразу после вызова.
                        // Остаётся, что бы ло нарисовано в xml. После какого-либо действия одновляется в норму
                        //--Solution--Делаем разнесенные вызовы с помощью бродкаст - плохое решение
                        Log.d(CurrencyList.TAG,"MainActivity: NotifyListener.onNotify setCurrentlyExchange" );

                        sendBroadcast(new Intent().setAction(UPDATE_ACTION).
                                putExtra("adapter", "both"));

                    }
                };
                service.setNotifyListener(mNotifyListener);

                //----------------------------------------------------------------------------------

                mRecyclerViewTop = (RecyclerView) findViewById(R.id.id_recycler_top);
                mRecyclerViewBottom = (RecyclerView) findViewById(R.id.id_recycler_bottom);

                //--Нужно для понимания каждым ресайклером, какая валюта в данный момент отображена в другом
                //--скорее даже не нужно, т.к. можно в SnapHelper и onNotify просто свои id передавать
                CurrencyList.RV_NAMES.put(idRecyclerTop, mRecyclerViewTop);
                CurrencyList.RV_NAMES.put(idRecyclerBottom, mRecyclerViewBottom);



                mRecyclerAdapterTop =
                        new RecyclerAdapter(getLayoutInflater(), idRecyclerTop );
                mRecyclerAdapterBottom =
                        new RecyclerAdapter(getLayoutInflater(), idRecyclerBottom );


                //---First recycler------

                mRecyclerViewTop.setAdapter(mRecyclerAdapterTop);
                mRecyclerViewTop.setLayoutManager(
                        new LinearLayoutManager(context,LinearLayoutManager.HORIZONTAL,false));

                mRecyclerViewTop.setHasFixedSize(true);


                //--Можно два экземпляра, чтобы понятно было, какой ресайклер меняется
                //--А можно попытаться через определения ресайклера, к которому attach
                //----
                //--Problem--1)Ложные срабатывания - подёргивания, но не полные сдвиги +
                //+ 2)Сама вью почему-то не обновляется для изменения курса при изменении валюты другой вью
                //  2)На самом деле, просто рано вызывались обновления экрана
                //--Solution 2)--Делаем разнесенные вызовы с помощью бродкаст - плохое решение


                SnapHelper mSnapHelperTop = new PagerSnapHelper() {
                    @Override
                    public boolean onFling (int velocityX, int velocityY){

                        Log.d(CurrencyList.TAG,"MainActivity: SnapHelper.onFling" +
                                " velocityX- " + velocityX + " ; ScrollDistance- " + calculateScrollDistance(velocityX, velocityY)[0]);

                        if(velocityX > 0) mCurList.changeCurrentlyExchange(mRecyclerViewTop, 1);
                        else mCurList.changeCurrentlyExchange(mRecyclerViewTop, -1);
                        //Может быть между этими действиями маленькая пауза, они идут как одно,
                        // а не последовательно
                        /*mRecyclerViewBottom.getAdapter().notifyDataSetChanged();
                        mRecyclerViewTop.getAdapter().notifyDataSetChanged();*/
                        sendBroadcast(new Intent().setAction(UPDATE_ACTION).
                                putExtra("adapter", "both"));


                        return super.onFling(velocityX, velocityY);
                    }
                };
                mSnapHelperTop.attachToRecyclerView(mRecyclerViewTop);

            //--Для обновления ресайклера, когда ввели сумму, которую нужно перевести
            mRecyclerAdapterTop.setNotifyCashChanged(new RecyclerAdapter.NotifyCashChanged() {
                @Override
                public void onNotify(double cash) {
                    mCurList.setCashToExchange(cash, idRecyclerTop);
                }
            });

                //---Second recycler------

                mRecyclerViewBottom.setAdapter(mRecyclerAdapterBottom);
                mRecyclerViewBottom.setLayoutManager(
                        new LinearLayoutManager(context,LinearLayoutManager.HORIZONTAL,false));

                mRecyclerViewBottom.setHasFixedSize(true);

                SnapHelper mSnapHelperBottom = new PagerSnapHelper(){
                    @Override
                    public boolean onFling (int velocityX, int velocityY){

                        Log.d(CurrencyList.TAG,"MainActivity: SnapHelper.onFling" +
                                " velocityX- " + velocityX + " ; ScrollDistance- " + calculateScrollDistance(velocityX, velocityY)[0]);

                        if(velocityX > 0) mCurList.changeCurrentlyExchange(mRecyclerViewBottom, 1);
                        else mCurList.changeCurrentlyExchange(mRecyclerViewBottom, -1);
                        sendBroadcast(new Intent().setAction(UPDATE_ACTION).
                                putExtra("adapter", "both"));

                        return super.onFling(velocityX, velocityY);
                    }
                };
                mSnapHelperBottom.attachToRecyclerView(mRecyclerViewBottom);

            //--Для обновления ресайклера, когда ввели сумму, которую нужно перевести
            mRecyclerAdapterBottom.setNotifyCashChanged(new RecyclerAdapter.NotifyCashChanged() {
                @Override
                public void onNotify(double cash) {
                    mCurList.setCashToExchange(cash, idRecyclerBottom);
                }
            });

                //----Работа с конкретными вью ресайклера------------------------

            /*View view = findViewById(R.id.id_edit_exchange_number);*/
            /*EditText editText = (EditText) mRecyclerViewTop.
                    findContainingItemView(view);*/
            /*RecyclerView.ViewHolder editText =
                    mRecyclerViewTop.findViewHolderForItemId(R.id.id_edit_exchange_number);*/
            /*editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    Log.d(CurrencyList.TAG,"TextChangedListener.beforeTextChanged: " + s.toString());
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    Log.d(CurrencyList.TAG,"TextChangedListener.onTextChanged: " + s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {

                    Log.d(CurrencyList.TAG,"TextChangedListener.afterTextChanged: " + s.toString()*//* +
                            "touch event: " + cashAmount.didTouchFocusSelect()*//*);
                }
            });*/

            //------------------------------------------

            //------Пероидизация подкачки данных с сайта---
            service.loadData();
            mAlarmManager = (AlarmManager) context.getSystemService(ALARM_SERVICE);
            //--Просмотреть каждое действие в строке и искать момент вызова onReceive
            // (сопоставя с sendBroadcast)

            Intent alarmIntent = new Intent(context, LoadBroadcastReceiver.class);
            alarmIntent.setAction(LOAD_ACTION);//--возможно это не нужно
            PendingIntent alarmPending = PendingIntent.getBroadcast(context, 0, alarmIntent, 0);
            //--maybeProblem--Система сдигает время до 60000 mls
            mAlarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis(), 30000, alarmPending);

            //--------------------------------------------------------------------------------------


            /*editCashAmountView = (EditText) mRecyclerViewTop.findContainingItemView();*
            editCashAmountView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    Log.d(CurrencyList.TAG,"TextChangedListener.beforeTextChanged: " + s.toString());
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    Log.d(CurrencyList.TAG,"TextChangedListener.onTextChanged: " + s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {
                    Log.d(CurrencyList.TAG,"TextChangedListener.afterTextChanged: " + s.toString());
                }
            });*/

        }

        //--Для обновления курсов на экране при смене валют.
        //Бродкаст нужен, чтобы изменения данных успевали происходить в системе,
        //т.е. просто для разнесенных по времени вызовов - плохое решение
        public class DataUpdateBroadcastReceiver extends BroadcastReceiver{

            @Override
            public void onReceive(Context context, Intent intent) {

                switch (intent.getStringExtra("adapter")){
                    case "both": {
                        mRecyclerViewTop.getAdapter().notifyDataSetChanged();
                        mRecyclerViewBottom.getAdapter().notifyDataSetChanged();
                        break;
                    }
                    case idRecyclerTop:
                        mRecyclerViewTop.getAdapter().notifyDataSetChanged();
                        break;
                    case idRecyclerBottom:
                        mRecyclerViewBottom.getAdapter().notifyDataSetChanged();
                        break;
                }

            }
        }
    }



}
