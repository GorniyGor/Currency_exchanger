package com.example.gor.revolut_test;

import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Gor on 26.07.2017.
 */

//  Singleton / Data store----

public class CurrencyList {

    public static final String TAG = "myLogs" ;

    public static HashMap<String, String> FX_URL = new HashMap<>();

    public static volatile CurrencyList sSelf;
    private HashMap<RecyclerView, Integer>  currentlyExchange = new HashMap<>();
    private String otherCurrency; //---Нужно для метода getCurrencyFrom

    private HashMap<String, CurrencyClass> exchangeRate = new HashMap<>();
    private ArrayList<String> positionOfCurrency = new ArrayList<>(); //--Optimization--Вместо него можно FX_URL ArrayList
    private String currentCurrencyFrom;

    public static CurrencyList getInstance(){
        if(sSelf == null){
            synchronized (CurrencyClass.class){
                if(sSelf == null){
                    sSelf = new CurrencyList();
                }
            }
        }
        return sSelf;
    }
    //------------------------------------------------------

    //--Main work methods-----------------------------------

    public void setCurrency(String nameFrom, CurrencyClass mCurrency){
        exchangeRate.put(nameFrom,mCurrency);
        positionOfCurrency.add(nameFrom);
    }

    public double getRate(String currencyTo){
        if(currencyTo != currentCurrencyFrom) {
            return exchangeRate.get(currentCurrencyFrom).getRate(currencyTo);
        }
        else return 1;
    }

    public String getCurrencyName(int position){

        if(positionOfCurrency.size() > position) {
            currentCurrencyFrom = positionOfCurrency.get(position);
            return currentCurrencyFrom;
        }
        return null;
    }


    //----Для взаимодействия между вью (валютами)---------------------------------------------------

    //---Необходимо для понимания, какие именно валюты в ланный момент нужно обменивать
    //---А для этого нужно понимать, какая валюта стоит в другой вьюшке
    public void setCurrentlyExchange(RecyclerView name, Integer currency){
        currentlyExchange.put(name, currency);
    }
    public int getCurrentlyExchangeSize(){return currentlyExchange.size();}

    //---В структуре currentlyExchange должно быть только 2 элемента,
    //---соответственно для одной вьюшки и для другой.
    //---Нам нужно взять название валюты у другой (не собственной) вьюшки
    public String getCurrencyFrom(RecyclerView name){

        if(currentlyExchange.size() > 2) Log.d(TAG, "getCurrencyFrom: ViewGroup parents too many!");

        for(Map.Entry entry : currentlyExchange.entrySet()){
            if(!name.equals(entry.getKey())){
                otherCurrency = positionOfCurrency.get((Integer) entry.getValue());
            }
        }
        return otherCurrency;
    }
}