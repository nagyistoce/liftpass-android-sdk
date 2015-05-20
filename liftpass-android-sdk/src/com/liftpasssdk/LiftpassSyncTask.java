package com.liftpasssdk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import android.os.AsyncTask;
import android.util.Log;

public class LiftpassSyncTask extends AsyncTask<String, String, String>{
	private LiftpassSyncTaskListener listener;
	private String _hash = "";
	
	public LiftpassSyncTask(LiftpassSyncTaskListener listener) {
		this.listener = listener;
	}
	
	@Override
    protected String doInBackground(String... params) {
        String responseString = "";
        try {
        	String UserId = params[0];
        	String AppKey = params[1];
        	String AppSecret = params[2];
        	String LiftpassServer = params[3];
        	String LiftpassPort = params[4];
        	String LiftpassUpdateUrl = params[5];
        	
			URL url = new URL(LiftpassServer + ":" + LiftpassPort + LiftpassUpdateUrl);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			
			Dictionary<String, Object> originalJson = new Hashtable<String, Object>();
			originalJson.put("liftpass-application", AppKey);
			originalJson.put("liftpass-time", String.valueOf(System.currentTimeMillis() / 1000));
			originalJson.put("liftpass-url", LiftpassUpdateUrl);
			originalJson.put("user", UserId);
			originalJson.put("events", Liftpass.GetInstance().loadEvents());
			
			Type dictOfObject = new TypeToken<Hashtable<String, Object>>(){}.getType();
			Gson gson = new Gson();
			String data = gson.toJson(originalJson, dictOfObject);
			String hash = Liftpass.HMAC(AppSecret, data);
			byte[] binaryData = data.getBytes();
			
			connection.addRequestProperty("Content-Length", String.valueOf(binaryData.length));
			connection.addRequestProperty("Content-Type", "application/json");
			connection.addRequestProperty("liftpass-hash", hash);
			connection.setReadTimeout(10000);
			connection.setConnectTimeout(15000);
			connection.setDoInput(true);
			connection.setDoOutput(true);
			
			OutputStream os = connection.getOutputStream();
			BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));			
			writer.write(data);			
			writer.flush();
			writer.close();
			os.close();

			int responseCode = connection.getResponseCode();
			
			if (responseCode == HttpURLConnection.HTTP_OK) {
				InputStream is = connection.getInputStream();
				BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				responseString = reader.readLine();
				
				_hash = connection.getHeaderField("liftpass-hash");
			} else {
				Log.d(Liftpass.TAG, "Http Response Code " + responseCode);
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
        return responseString;
    }
	
	@Override
	protected void onProgressUpdate(String... progress) {
		super.onProgressUpdate(progress);
    }

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        //Do anything with response..
        listener.onTaskComplete(result, _hash);
    }
}
