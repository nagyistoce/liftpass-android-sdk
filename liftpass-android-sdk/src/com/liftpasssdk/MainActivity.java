package com.liftpasssdk;

import java.util.Hashtable;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity implements LiftpassGoodsInfoUpdateListener {
	
	private static String LiftpassServer 		= "http://52.6.100.42";
	private static int LiftpassPort 			= 9090;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		Liftpass.GetInstance().init("4a0fcf37e5a84add901114b9107c572a",
				"7f6161446d874e61b218357027bdb6ba",
				LiftpassServer,
				LiftpassPort,
				"prices.txt",
				this, this);
		Liftpass.GetInstance().serUserId("111");
	}
	
	@Override
	protected void onStart() {
		Liftpass.GetInstance().applicationDidBecomeActive();
		
		Liftpass.GetInstance().updateStringMetric(6, "hello");
		Liftpass.GetInstance().incrementNumberMetric(10);
		
		Liftpass.GetInstance().sync();
		
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		Liftpass.GetInstance().applicationDidEnterBackground();
		
		super.onStop();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onGoodsInfoUpdated(Hashtable<String, LiftpassCurrency> goods) {
		// TODO Auto-generated method stub
		Log.d("Liftpass", goods.toString());
	}
}
