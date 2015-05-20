package com.liftpasssdk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.util.Log;

public class Liftpass implements LiftpassSyncTaskListener {
	
	private static final int PIPE_DISABLED = -1;

	private static final String LiftpassEventsKey 			= "LiftpassEventsKey";
	private static final String LiftpassGoodsData 			= "LiftpassGoodsData";
	private static final String LiftpassCurrentProgressKey	= "LiftpassCurrentProgressKey";
	private static final String LiftpassFirstLaunchKey 				= "LiftpassFLKey";

	private static String LiftpassServer 		= "http://52.6.100.42";
	private static String LiftpassPort 			= "9090";
	private static String LiftpassUpdateUrl 		= "/sdk/update/v1/";
	
	private static final String LiftpassOSVersion 				= "LiftpassOSVersion";
	private static final String LiftpassSessionsCount 			= "LiftpassSessionsCount";
	private static final String LiftpassTotalVirtualPurchases 	= "LiftpassTotalVirtualPurchases";
	private static final String LiftpassTotalIAPPurchases 		= "LiftpassTotalIAPPurchases";

	private static final String LiftpassLanguage 			= "LiftpassLanguage";
	private static final String LiftpassTimezone 			= "LiftpassTimezone";
	private static final String LiftpassTotalPlayTime 		= "TotalPlayTime";
	private static final String LiftpassDeviceModel 		= "LiftpassDeviceModel";
	//private static final String LiftpassTotalDollarsSpent 	= "TotalDollarsSpent";
	//private static final String LiftpassTotalCurrencySpent 	= "TotalCurrencySpent";

	private static final String LiftpassCurrency1 	= "LiftpassCurrency1";
	private static final String LiftpassCurrency2 	= "LiftpassCurrency2";
	private static final String LiftpassCurrency3 	= "LiftpassCurrency3";
	private static final String LiftpassCurrency4 	= "LiftpassCurrency4";
	private static final String LiftpassCurrency5 	= "LiftpassCurrency5";
	private static final String LiftpassCurrency6 	= "LiftpassCurrency6";
	private static final String LiftpassCurrency7 	= "LiftpassCurrency7";
	private static final String LiftpassCurrency8 	= "LiftpassCurrency8";
	private static final String LiftpassVersion 	= "LiftpassVersion";
	private static final String LiftpassVersionKey 	= "LiftpassVersionKey";
	private static final long LiftpassSaferequestTimeRange = 900000;
	
	private static String appKey;
	private static String appSecret;
	private static String userId;
	private static String pricesDataPath;
	
	private static long startTime = 0;
	private static boolean wasInitialized = false;
	private static boolean isFirstLauntch = false;
	
	private static boolean HasPendingRequest = false;
	
	private static Liftpass _instance = null;
	private static Activity _launcherActivity = null;
	private static LiftpassGoodsInfoUpdateListener _goodsInfoUpdateListener = null;
	
	private Hashtable<String, LiftpassCurrency> DefaultGoods = new Hashtable<String, LiftpassCurrency>();
	private Hashtable<String, LiftpassCurrency> Goods = new Hashtable<String, LiftpassCurrency>();
	
	private ArrayList<Object> TempEvents =  new ArrayList<Object>();
	
	public static final String TAG = "Liftpass";
	
	public static Liftpass GetInstance() {
		if (_instance == null) {
			_instance = new Liftpass();
		}
		return _instance;
	}
	
	public void init (String applicationKey, String applicationSecret, String liftpassServer, int liftpassPort, String defaultPricesDataPath, Activity launcher, LiftpassGoodsInfoUpdateListener goodsInfoUpdateListener) {

		LiftpassServer = liftpassServer;
		LiftpassPort = String.valueOf(liftpassPort);
		init(applicationKey, applicationSecret, defaultPricesDataPath, launcher, goodsInfoUpdateListener);
	}
	
	public void init(String applicationKey, String applicationSecret, String defaultPricesDataPath, Activity launcher, LiftpassGoodsInfoUpdateListener goodsInfoUpdateListener) {
		if(wasInitialized) {
	        return;
	    }
		
		appKey = applicationKey;
		appSecret = applicationSecret;
		_goodsInfoUpdateListener = goodsInfoUpdateListener;
		_launcherActivity = launcher;		
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		if (!prefs.contains(LiftpassFirstLaunchKey)) {
			isFirstLauntch = true;
			SharedPreferences.Editor editor = prefs.edit();
			editor.putBoolean(LiftpassFirstLaunchKey, true);
			editor.commit();
		}
		
	    wasInitialized = true;
	    pricesDataPath = defaultPricesDataPath;
	    
	    parceDefaultGoodsData();
	    loadProductsData();
	}
	
	public void applicationDidBecomeActive() {
		if(getLiftpassSessionCountPipeId() != PIPE_DISABLED) {
	        incrementNumberMetric(getLiftpassSessionCountPipeId());
	    }
	    
	    startTime = System.currentTimeMillis() / 1000;
	}
	
	public void applicationDidEnterBackground() {
		saveSessionTime();
	}
	
	public void setApplicationKey(String key, String secret) {
	    appKey = key;
	    appSecret = secret;
	}
	
	public void sync() {
		ArrayList<Object> events = loadEvents();
		if (!events.isEmpty()) {
			if (HasPendingRequest) {
				return;
			} else {
				HasPendingRequest = true;				
				sendEvents(events);
			}
		} else {
			Log.d(TAG, "No events to send.");
		}
	}
	
	public void setServer(String server, int port) {
		LiftpassServer = server;
		LiftpassPort = String.valueOf(port);
	}
	
	public void serUserId(String id) {
		userId = id;
	}
	
	private void saveSessionTime() {
	    long playedTime = System.currentTimeMillis() / 1000 - startTime;
	    
	    if(getLiftpassTotalPlayTimePipeId() != PIPE_DISABLED) {
	        incrementNumberMetric(getLiftpassTotalPlayTimePipeId(), (int)playedTime);
	    }
	}
	
	public void updateStringMetric(int metricId, String value) {
	    if(metricId <= 7) {
	        updateProgressMetric(metricId, value);
	    } else {
	          Log.d(TAG, "updateStringMetric metricId shoudl be less then 7. Call ignored");
	    }
	}
	
	private void updateNumberMetric(int metricId, int value) {
	    if(metricId > 7) {
	        updateProgressMetric(metricId, value);
	    } else {
	          Log.d(TAG, "updateStringMetric metricId shoudl be less then 7. Call ignored");
	    }
	}
	
	private void incrementNumberMetric (int metricId, int value) {		
	    if(metricId > 7) {
	        ArrayList<Object> progress = loadCurrentProgress();
	        int metricValue = (int) ((Double) progress.get(metricId) + value);
	        
	        updateNumberMetric(metricId, metricValue);
	    } else {
	    	Log.d(TAG, "incrementNumberMetric metricId shoudl be more then 7. Call ignored");
	    }
	}

	public void incrementNumberMetric (int metricId) {
	    incrementNumberMetric(metricId, 1);
	}
	
	public void recordVirtualGoodPurchase(String goodId) {
		LiftpassCurrency  price = getPrice(goodId);
		recordVirtualGoodPurchase(goodId, price);
	}

	public void recordVirtualGoodPurchase(String goodId, LiftpassCurrency price) {		
		ArrayList<Object> progress = loadCurrentProgress();

		ArrayList<Object> attributes =  new ArrayList<Object>(Arrays.asList(
			goodId, 
			null,
			null,
			null,
			price.getCurrency1(),
			price.getCurrency2(),
			price.getCurrency3(),
			price.getCurrency4(),
			price.getCurrency5(),
			price.getCurrency6(),
			price.getCurrency7(),
			price.getCurrency8(),
			null,
			null,
			null,
			null));
		
		Hashtable<String, Object> events =  new Hashtable<String, Object>();
		events.put("name", "liftpass-virtual-purchase");
		events.put("progress", progress);
		events.put("time", System.currentTimeMillis() / 1000);
		events.put("attributes", attributes);
		
		addNewEvent(events);

		if(getLiftpassTotalVirtualPurchasesPipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassTotalVirtualPurchasesPipeId());
		}

		if(getLiftpassCurrency1PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency1PipeId(), Integer.valueOf(price.getCurrency1()));
		}
		
		if(getLiftpassCurrency2PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency2PipeId(), Integer.valueOf(price.getCurrency2()));
		}
		
		if(getLiftpassCurrency3PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency3PipeId(), Integer.valueOf(price.getCurrency3()));
		}
		
		if(getLiftpassCurrency4PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency4PipeId(), Integer.valueOf(price.getCurrency4()));
		}
		
		if(getLiftpassCurrency5PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency5PipeId(), Integer.valueOf(price.getCurrency5()));
		}
		
		if(getLiftpassCurrency6PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency6PipeId(), Integer.valueOf(price.getCurrency6()));
		}
		
		if(getLiftpassCurrency7PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency7PipeId(), Integer.valueOf(price.getCurrency7()));
		}
		
		if(getLiftpassCurrency8PipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassCurrency8PipeId(), Integer.valueOf(price.getCurrency8()));
		}
	}
	
	public void recordIAPPurchase(String goodId, float price, String currencyCode) {
		LiftpassCurrency  reward = getReward(goodId);
		recordIAPPurchase(goodId, price, currencyCode, reward);
	}

	public void recordIAPPurchase(String goodId, float price, String currencyCode, LiftpassCurrency reward)	{
		ArrayList<Object> progress = loadCurrentProgress();

		@SuppressWarnings("unchecked")
		ArrayList<Object> attributes =  new ArrayList<Object>(Arrays.asList(
			goodId, 
			null,
			null,
			reward.getCurrency1(),
			reward.getCurrency2(),
			reward.getCurrency3(),
			reward.getCurrency4(),
			reward.getCurrency5(),
			reward.getCurrency6(),
			reward.getCurrency7(),
			reward.getCurrency8(),
			price,
			null,
			null,
			null
		));

		Hashtable<String, Object> events =  new Hashtable<String, Object>();
		events.put("name", "liftpass-iap-purchase");
		events.put("progress", progress);
		events.put("time", System.currentTimeMillis() / 1000);
		events.put("attributes", attributes);
		
		addNewEvent(events);

		if(getLiftpassTotalIAPPurchasesPipeId() != PIPE_DISABLED) {
			incrementNumberMetric(getLiftpassTotalIAPPurchasesPipeId());
		}
	}
		
	public LiftpassCurrency getPrice (String goodId) {
		LiftpassCurrency currency =  null;

		if(Goods.containsKey(goodId)) {
			currency = Goods.get(goodId);
		}

		if(currency == null) {
			if(DefaultGoods.containsKey(goodId)) {
				currency = DefaultGoods.get(goodId);
			}
		}
				
		return currency;
	}

	public LiftpassCurrency getReward(String goodId) {
		return getPrice(goodId);
	}

	public boolean hasPricesForGood(String goodId) {
		return getPrice(goodId) != null;
	}

	public boolean hasRewardForGood(String goodId) {
		return hasPricesForGood(goodId);
	}
	
	public void setStringPipe(String type, int metricId) {
		if(metricId <= 7) {
			SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();			
			editor.putInt(type, metricId);
			editor.commit();
			
			checkPipeUpdates();
		} else {
			Log.d(TAG, "SetStringPipe metricId should be less then 7. Call ignored");
		}
	}

	public void setNumberPipe(String type, int metricId) {
		if(metricId > 7) {
			SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt(type, metricId);
			editor.commit();
			
			checkPipeUpdates();
		} else {
			Log.d(TAG, "SetNumberPipe metricId should be more then 7. Call ignored");
		}
	}

	private void checkPipeUpdates() {
		if(getLiftpassOSVersionPipeId() != PIPE_DISABLED) {
			updateStringMetric(getLiftpassOSVersionPipeId(), "Android " + Build.VERSION.RELEASE);
		}
		
		if(getLiftpassLanguagePipeId() != PIPE_DISABLED) {
			updateStringMetric(getLiftpassLanguagePipeId(), Locale.getDefault().getDisplayLanguage());
		}
		
		if(getLiftpassTimezonePipeId() != PIPE_DISABLED) {
			updateNumberMetric(getLiftpassTimezonePipeId(), TimeZone.getTimeZone("GTM").getRawOffset() / 36000);
		}
		
		if(getLiftpassDeviceModelPipeId() != PIPE_DISABLED) {
			updateStringMetric(getLiftpassDeviceModelPipeId(), Build.MODEL);
		}

	}
	
	@SuppressWarnings("unchecked")
	private ArrayList<Object> loadCurrentProgress() {
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		ArrayList<Object> JSON = null;
		
		if (!prefs.contains(LiftpassCurrentProgressKey)) {
			JSON = new ArrayList<Object>(Arrays.asList("", "", "", "", "", "", "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));			
		} else {
			String JSONStr = prefs.getString(LiftpassCurrentProgressKey, "");
			
			Type listOfTestObject = new TypeToken<ArrayList<Object>>(){}.getType();
			Gson gson = new Gson();
			JSON = gson.fromJson(JSONStr, listOfTestObject);
		}
		
		return JSON;
	}
	
	private void updateProgressMetric (int key, Object value) {
		ArrayList<Object> progress = loadCurrentProgress();
	    progress.set(key, value);
	    
	    Type listOfTestObject = new TypeToken<ArrayList<Object>>(){}.getType();
	    Gson gson = new Gson();
	    String jsonData = gson.toJson(progress, listOfTestObject);

	    SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(LiftpassCurrentProgressKey, jsonData);
		editor.commit();
	    
	    saveMetricUpdateEvent(progress);
	}
	
	private void saveMetricUpdateEvent(ArrayList<Object> progress) {

		Dictionary<String, Object> event = new Hashtable<String, Object>();
		event.put("name", "liftpass-metric-update");
		event.put("progress", progress);
		event.put("time", System.currentTimeMillis() / 1000);		

		addNewEvent(event);
	}
	
	private void restoreEvents() {
		if(TempEvents.size() != 0) {
			ArrayList<Object> CurrentEvents = loadEvents();

			for(Object e : TempEvents) {
				CurrentEvents.add(e);
			}

			saveEvents(CurrentEvents);
			TempEvents =  new ArrayList<Object>();
		}
		HasPendingRequest = false;
	}
	
	private void addNewEvent(Object event)  {
		ArrayList<Object> events = loadEvents();
		events.add(event);
		saveEvents(events);
	}
	
	private void saveEvents(ArrayList<Object> events) {
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		
		Type listOfTestObject = new TypeToken<ArrayList<Object>>(){}.getType();
		Gson gson = new Gson();
		editor.putString(LiftpassEventsKey, gson.toJson(events, listOfTestObject));
		editor.commit();
	}
	
	public ArrayList<Object> loadEvents() {
		ArrayList<Object> events = new ArrayList<Object>();
		
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		if(prefs.contains(LiftpassEventsKey)) {
			String eventsStr = prefs.getString(LiftpassEventsKey, "");
			
			Type listOfTestObject = new TypeToken<ArrayList<Object>>(){}.getType();
			Gson gson = new Gson();
			events = gson.fromJson(eventsStr, listOfTestObject);
		}
		
		return events;
	}
	
	private Hashtable<String, LiftpassCurrency> getGoodsDictionary(String data) {
		Hashtable<String, LiftpassCurrency> loadedGoods = new Hashtable<String, LiftpassCurrency>();
		
		Type jsonData = new TypeToken<Map<String, Object>>(){}.getType();
		Gson gson = new Gson();
		Map<String, Object> JSON = gson.fromJson(data, jsonData);
		
		for (Map.Entry<String, Object> entry : JSON.entrySet()) {
		    String key = entry.getKey();
		    @SuppressWarnings("unchecked")
			ArrayList<Object> prices = (ArrayList<Object>) entry.getValue();
		    LiftpassCurrency currency = new LiftpassCurrency(
		    			String.valueOf(prices.get(0)),
		    			String.valueOf(prices.get(1)),
		    			String.valueOf(prices.get(2)),
		    			String.valueOf(prices.get(3)),
		    			String.valueOf(prices.get(4)),
		    			String.valueOf(prices.get(5)),
		    			String.valueOf(prices.get(6)),
		    			String.valueOf(prices.get(7)) );		    
		    loadedGoods.put(key, currency);
		}
		
		return loadedGoods;
	}
	
	private void parceDefaultGoodsData() {		
		AssetManager manager = _launcherActivity.getResources().getAssets();
		InputStream inputStream = null;
		
		try {
			inputStream = manager.open(pricesDataPath);
			if (inputStream != null) {
				InputStreamReader streamReader = new InputStreamReader(inputStream);
				BufferedReader bufferedReader = new BufferedReader(streamReader);
				
				StringBuffer stringBuffer = new StringBuffer();
				String line = "";				 
				while((line = bufferedReader.readLine()) != null){
					stringBuffer.append(line).append("\n");
				}				
				DefaultGoods = getGoodsDictionary(stringBuffer.toString());
			} else {
				DefaultGoods = new Hashtable<String, LiftpassCurrency>();				
				Log.d(TAG, "File with default prices not found at path: " + pricesDataPath);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void loadProductsData() {
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		if (prefs.contains(LiftpassGoodsData)) {
			Goods = getGoodsDictionary(prefs.getString(LiftpassGoodsData, ""));
		} else {
			Goods = DefaultGoods;
		}
		
		if (_goodsInfoUpdateListener != null) {
			_goodsInfoUpdateListener.onGoodsInfoUpdated(Goods);
		}
	}
	
	private void updateGoodsInfo(Map<String, Object> goods) {
		Type jsonData = new TypeToken<Map<String, Object>>(){}.getType();
		Gson gson = new Gson();
		String GoodsSerializedData = gson.toJson(goods, jsonData);
		
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(LiftpassGoodsData, GoodsSerializedData);
		editor.commit();
		
		loadProductsData();
	}
	
	private void cleanUpSavedEvents() {
		if(TempEvents.size() != 0) {
			saveEvents(TempEvents);			
		} else {
			SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.remove(LiftpassEventsKey);
			editor.commit();
		}
		HasPendingRequest = false;
	}
	
	private void saveVersion(String version) {
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(LiftpassVersionKey, version);
		editor.commit();
	}
	
	private int getLiftpassOSVersionPipeId() {
		return getPipeIdWithKey(LiftpassOSVersion);
	}
	
	private int getLiftpassLanguagePipeId() {
		return getPipeIdWithKey(LiftpassLanguage);
	}
	
	private int getLiftpassTimezonePipeId() {
		return getPipeIdWithKey(LiftpassTimezone);
	}
	
	private int getLiftpassDeviceModelPipeId() {
		return getPipeIdWithKey(LiftpassDeviceModel);
	}
	
	private int getLiftpassSessionCountPipeId() {
		return getPipeIdWithKey(LiftpassSessionsCount);
	}
	
	private int getLiftpassTotalPlayTimePipeId() {
		return getPipeIdWithKey(LiftpassTotalPlayTime);
	}
	
	private int getLiftpassTotalIAPPurchasesPipeId() {
		return getPipeIdWithKey(LiftpassTotalIAPPurchases);
	}
	
	private int getLiftpassCurrency1PipeId() {
		return getPipeIdWithKey(LiftpassCurrency1);
	}
	
	private int getLiftpassCurrency2PipeId() {
		return getPipeIdWithKey(LiftpassCurrency2);
	}
	
	private int getLiftpassCurrency3PipeId() {
		return getPipeIdWithKey(LiftpassCurrency3);
	}
	
	private int getLiftpassCurrency4PipeId() {
		return getPipeIdWithKey(LiftpassCurrency4);
	}
	
	private int getLiftpassCurrency5PipeId() {
		return getPipeIdWithKey(LiftpassCurrency5);
	}
	
	private int getLiftpassCurrency6PipeId() {
		return getPipeIdWithKey(LiftpassCurrency6);
	}
	
	private int getLiftpassCurrency7PipeId() {
		return getPipeIdWithKey(LiftpassCurrency7);
	}
	
	private int getLiftpassCurrency8PipeId() {
		return getPipeIdWithKey(LiftpassCurrency8);
	}
	
	private int getLiftpassTotalVirtualPurchasesPipeId() {
		return getPipeIdWithKey(LiftpassTotalVirtualPurchases);
	}
	
	private int getLiftpassVersionPipeId() {
		return getPipeIdWithKey(LiftpassVersion);
	}
	
	private int getPipeIdWithKey(String key) {
		SharedPreferences prefs = _launcherActivity.getPreferences(Context.MODE_PRIVATE);
		if (!prefs.contains(key)) {
			return PIPE_DISABLED;
		}
		
		return prefs.getInt(key, 0);
	}
	
	public boolean isFirstLaunch() {
		return isFirstLauntch;
	}
	
	public static String HMAC(String key, String data) {
		String result = "";
		final Charset asciiCs = Charset.forName("US-ASCII");
        Mac sha256_HMAC;
		try {
			sha256_HMAC = Mac.getInstance("HmacSHA256");			
			final SecretKeySpec secret_key = new javax.crypto.spec.SecretKeySpec(asciiCs.encode(key).array(), "HmacSHA256");
			sha256_HMAC.init(secret_key);
	        final byte[] mac_data = sha256_HMAC.doFinal(asciiCs.encode(data).array());
	        
	        for (final byte element : mac_data)
	        {
	           result += Integer.toString((element & 0xff) + 0x100, 16).substring(1);
	        }
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	private void sendEvents(ArrayList<Object> events) {
		LiftpassSyncTask task = new LiftpassSyncTask(this);
		task.execute(userId, appKey, appSecret, LiftpassServer, LiftpassPort, LiftpassUpdateUrl);
	}

	@Override
	public void onTaskComplete(String result, String hash) {
		HasPendingRequest = false;
		
		Type jsonData = new TypeToken<Map<String, Object>>(){}.getType();
		Gson gson = new Gson();
		Map<String, Object> JSON = gson.fromJson(result, jsonData);
		
		double serverTime = (Double) JSON.get("liftpass-time");
		double diff = System.currentTimeMillis() / 1000 - serverTime;
		if (Math.abs(diff) > LiftpassSaferequestTimeRange) {
			Log.d(TAG, "Time Validation FAILED.");
			restoreEvents();
			return;
		}
		
		String ResponceHash = hash;
		Log.d(TAG, "ResponceHash " + ResponceHash);
		
		String ClientHash = HMAC(appSecret, result);
		Log.d(TAG, "ClientHash " + ClientHash);
		
		if (!ClientHash.equals(ResponceHash)) {
			Log.d(TAG, "Hash Validation FAILED.");
			restoreEvents();
			return;
		}
		
		String version = String.valueOf(JSON.get("version"));
		saveVersion(version);
		
		if(getLiftpassVersionPipeId() != PIPE_DISABLED) {
			updateStringMetric(getLiftpassVersionPipeId(), version);
		}
		
		Map<String, Object> goods = gson.fromJson(String.valueOf(JSON.get("goods")), jsonData);
		updateGoodsInfo(goods);
		cleanUpSavedEvents();
	}	
}
