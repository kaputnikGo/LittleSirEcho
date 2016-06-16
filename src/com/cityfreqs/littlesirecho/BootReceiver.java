package com.cityfreqs.littlesirecho;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class BootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if ("android.intent.action.BOOT_COMPLETED".equals(intent.getAction())) {
			Intent restartIntent = new Intent();
			restartIntent.setClassName("com.cityfreqs.littlesirecho", "com.cityfreqs.littlesirecho.MainActivity");
			restartIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		    context.startActivity(restartIntent);
		    Toast.makeText(context, "Little Sir Echo activity restarted.", Toast.LENGTH_LONG).show();
		}
	}	
}