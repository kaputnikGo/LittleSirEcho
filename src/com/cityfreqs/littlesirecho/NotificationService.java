package com.cityfreqs.littlesirecho;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;

public class NotificationService extends NotificationListenerService {
    //private static final String TAG = "LittleSirEcho-Notify_Service";
	private static final String SMS = "sms";
	private static final String MMS = "mms";
    private static final String LISTENER_STRING = "com.cityfreqs.littlesirecho.NOTIFICATION_LISTENER";
    Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
        IntentFilter filter = new IntentFilter();
        filter.addAction(LISTENER_STRING);
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    }
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
    	String owner = sbn.getPackageName();
    	if ( (owner.contains(SMS)) || (owner.contains(MMS)) ) {
	    	Intent msgrcv = new Intent(LISTENER_STRING);	    	
	    	msgrcv.putExtra("type", "posted");
	        msgrcv.putExtra("owner", sbn.getPackageName());         
        	LocalBroadcastManager.getInstance(context).sendBroadcast(msgrcv);    
        }
    }
    
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
    	String owner = sbn.getPackageName();
    	if ( (owner.contains(SMS)) || (owner.contains(MMS)) ) {
	    	Intent msgrcv = new Intent(LISTENER_STRING);
	    	msgrcv.putExtra("type", "removed");
	        msgrcv.putExtra("owner", sbn.getPackageName());
	        LocalBroadcastManager.getInstance(context).sendBroadcast(msgrcv);
    	}
    }
}
