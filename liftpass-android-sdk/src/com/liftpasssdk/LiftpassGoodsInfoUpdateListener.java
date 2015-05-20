package com.liftpasssdk;

import java.util.Hashtable;

public interface LiftpassGoodsInfoUpdateListener {
	public void onGoodsInfoUpdated(Hashtable<String, LiftpassCurrency> goods);
}
