package com.example.gor.revolut_test;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/**
 * Created by Gor on 24.07.2017.
 */

class RecyclerAdapter extends RecyclerView.Adapter<SimpleViewHolder>{

    private NotifyCashChanged mNotifyCashChanged;

    private final WeakReference<LayoutInflater> localInflater;
    private CurrencyList mCurList;
    private String name;
    private String ownCurrencyName;



    public RecyclerAdapter(LayoutInflater layoutInflater, String name){

        localInflater = new WeakReference<>(layoutInflater);
        mCurList = CurrencyList.getInstance();
        this.name = name;
    }

    @Override
    public SimpleViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        LayoutInflater inflater = localInflater.get();
        if(inflater != null){
            return new SimpleViewHolder(inflater.inflate(R.layout.recycler_view, parent, false));
        }
        else {
            throw new  RuntimeException("Oooops, looks like activity is dead");
        }
    }

    @Override
    public void onBindViewHolder(SimpleViewHolder holder, int position) {

        ownCurrencyName = mCurList.getCurrencyName(position);
        double rateMy = 0;
        double rateAnother = 0;
        double sum = 0;

        if( ownCurrencyName != null) {


            holder.setCashChangedNotify(new SimpleViewHolder.CashChangedNotify() {
                @Override
                public void onNotify(double cash) {
                    mNotifyCashChanged.onNotify(ownCurrencyName, cash);
                }
            });

            String currencyTo = mCurList.getCurrencyFrom(name);

            // Условие из-за race condition адаптера и окончания закачки
            if(currencyTo!=null){
                rateMy = mCurList.getRate( ownCurrencyName, currencyTo);
                rateAnother = mCurList.getRate(currencyTo, ownCurrencyName);


                //--Description--Исходная валюта, т.е. та, в которой ввели число для перевода,
                // должна при каком-либо обновлении вью показать то же число.
                // Валюта, в которую переводят, должна получить переведённое число -первое условие-
                // А вот валюта, которая могла бы быть исходной (другая валюта из того же ресайклера),
                // должна получить 0, чтобы число от соседнего item не сохранилось при свайпе -второе условие-
                if(mCurList.mCash.getChanger().equals(name)){
                    if(mCurList.mCash.getVersionOfChanger().equals(ownCurrencyName))
                        sum = mCurList.mCash.getCash();
                }
                else sum = mCurList.mCash.getCash()*rateAnother;
            }

        }
        else ownCurrencyName = "non";

        holder.setCurrancyName(ownCurrencyName);
        holder.setCurrancyRate(rateMy);
        holder.setCashAmount(sum);
    }

    @Override
    public int getItemCount() {
        return 3;
    }

    //----Addition----

    public void setNotifyCashChanged(NotifyCashChanged notifyCashChanged){
        mNotifyCashChanged = notifyCashChanged;
    }

    public interface NotifyCashChanged {
        void onNotify(String currency, double cash);
    }
}
