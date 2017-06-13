/*
 *  
 *  Little Sir Echo 
 *  Android SMS notification reminder app
 *  - listen for SMSM notifications, set a reminder notification
 *    to remind after user-defined minutes lapsed.
 *  
 *  Copyright (C) 2017  Kaputnik Go
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>. 
 * 
 */

package com.cityfreqs.littlesirecho;

import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	/*
	 * - version code: 5
	 * - version name: 2.3
	 * - min/target API 18 - 4.3
	 * - S4 4.3.1 (18) - testing use
	 * - s5 5.1.1 (22) - install only
	 * - added onBind() to notificationService
	 * 
	 */
	private TextView listenerView;
	private TextView timerView;
	private TextView awakeView;
	private static final String LISTENER_STRING = "com.cityfreqs.littlesirecho.NOTIFICATION_LISTENER";
	private static final String ALARM_ACTION = "com.cityfreqs.littlesirecho.alarm";
	private static final String TAG = "LittleSirEcho";
	private static final boolean DEBUG = false; // release version
	
	private static final String SMS = "sms";
	private static final String MMS = "mms";
	private static final int DEBUG_WAIT_TIME = 30000; // 30 secs
	private static final int DEFAULT_WAIT_TIME = 600000; // 10 mins
	private static final int THIRD_WAIT_TIME = 1200000; // 20 mins
	private static final int LONG_WAIT_TIME = 1800000; // 30 mins
	private static final int DEFAULT_AWAKE_START = 7; // 7am
	private static final int DEFAULT_AWAKE_END = 23; // 11pm
	
	private LSEServiceReceiver LSEsr;
	private NotificationCompat.Builder mBuilder;
	private NotificationManager notificationManager;
	
	private static final boolean VERSION_ALARM = false; // testing only
	private ScheduledExecutorService scheduler;
	private WakeLock wakeLock;
	private PendingIntent alarmIntent;
	private AlarmManager alarmManager;
	
	private SharedPreferences sharedPrefs;
	
	private Uri soundUri;
	private String notifyOwner;
	private boolean running;
	private NumberPicker startHourPicker;
	private NumberPicker endHourPicker;
	private int userSelectedWaitTime;
	private int userAwakeStart;
	private int userAwakeEnd;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listenerView = (TextView) findViewById(R.id.listenerView);
        timerView = (TextView) findViewById(R.id.timerView);
        awakeView = (TextView) findViewById(R.id.awakeView);
        
        running = false;
        userSelectedWaitTime = DEFAULT_WAIT_TIME;
        userAwakeStart = DEFAULT_AWAKE_START;
        userAwakeEnd = DEFAULT_AWAKE_END;
        
        checkSettings();
        initLittleSirEcho();        
    }
    
    @Override
    protected void onStart() {
    	super.onStart();
    	sharedPrefs = getSharedPreferences("LSE", MODE_PRIVATE);
    	Editor editor = sharedPrefs.edit();
    	editor.putBoolean("activity_run", true);
    	editor.commit();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    }
    
    @Override
    protected void onPause() {
    	super.onPause();
    }
    
    @Override
    protected void onStop() {
    	super.onStop();
    	sharedPrefs = getSharedPreferences("LSE", MODE_PRIVATE);
    	Editor editor = sharedPrefs.edit();
    	editor.putBoolean("activity_run", false);
    	editor.putInt("wait_time", userSelectedWaitTime);
    	editor.putInt("start_time", userAwakeStart);
    	editor.putInt("end_time", userAwakeEnd);
    	editor.commit();
    }
    
    @SuppressLint("Wakelock")
	@Override
    protected void onDestroy() {
    	super.onDestroy();
		if (wakeLock != null && wakeLock.isHeld()) {
		    wakeLock.release();
		    wakeLock = null;
		}
    	if (mBuilder != null) {
    		notificationManager.cancelAll();
    		mBuilder = null;
    	}
    	if (scheduler != null) {
    		scheduler.shutdown();
    		scheduler = null;
    	}
    }   
/*
* init  
*/   
    private void initLittleSirEcho() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(LISTENER_STRING);
        LSEsr = new LSEServiceReceiver();
        
        LocalBroadcastManager.getInstance(this).registerReceiver(LSEsr, new IntentFilter(LISTENER_STRING));
        soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);  
        
        setAwakeView();
        startHourPicker = (NumberPicker) findViewById(R.id.start_hour_picker);
        startHourPicker.setMinValue(0);
        startHourPicker.setMaxValue(11);
        startHourPicker.setValue(DEFAULT_AWAKE_START);
        startHourPicker.setWrapSelectorWheel(false);
        // stop numpad auto appearing
        startHourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        // set listener here instead of implementing at activity level
        startHourPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
        	@Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        		if (picker == startHourPicker) {
	        		if (newVal != userAwakeStart) {
	        			userAwakeStart = newVal;
	        			setAwakeView();
	        		}
        		}	
            }
        });
        
        endHourPicker = (NumberPicker) findViewById(R.id.end_hour_picker);
        endHourPicker.setMinValue(12);
        endHourPicker.setMaxValue(23);
        endHourPicker.setValue(DEFAULT_AWAKE_END);
        endHourPicker.setWrapSelectorWheel(false);
        // stop numpad auto appearing
        endHourPicker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        // set listener here instead of implementing at activity level
        endHourPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
        	@Override
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
        		if (picker == endHourPicker) {
	        		if (newVal != userAwakeEnd) {
	        			userAwakeEnd = newVal;
	        			setAwakeView();
	        		}
        		}
            }
        });
    } 
    
    private void checkSettings() {
    	if (Settings.Secure.getString(this.getContentResolver(), 
			   "enabled_notification_listeners").contains(this.getPackageName())) {
		   
    	// Notification access service already enabled
		    Toast.makeText(this,"Little Sir Echo enabled",Toast.LENGTH_LONG).show();
		} 
    	else {
		    // take user to notifications access settings view to allow LSE access
    		// inform user via dialog about changing settings 		
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    		builder.setTitle(R.string.dialog_title).setMessage(R.string.dialog_message);
    		builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
    			public void onClick(DialogInterface dialog, int id) {
    				startActivityForResult(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"), 0);
    	        }
    	    });
    		builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
    	        public void onClick(DialogInterface dialog, int id) {
    	        	notEnabled();
    	        }
    	    });
    		
    		AlertDialog dialog = builder.create();
    		dialog.show();
		}
    }
    
    private void notEnabled() {
    	Toast.makeText(this,"Little Sir Echo NOT enabled",Toast.LENGTH_LONG).show();
    	
    }
/*
 * View Controls   
 */       
    public void buttonClicked(View view) {
    	if (view.getId() == R.id.btnClearNotify) {
    		// manually clear all timers, notifications, etc
    		logger("clear all pressed");
			if (VERSION_ALARM) {
				removeAlarmNotification("LSE");
			}
			else {
    			// wakelock version
				clearNotifications("LSE");
			}
    	}
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();      
        switch(view.getId()) {
            case R.id.debug_wait_time:
                if (checked)
                	userSelectedWaitTime = DEBUG_WAIT_TIME;
                break;
            case R.id.default_wait_time:
                if (checked)
                	userSelectedWaitTime = DEFAULT_WAIT_TIME;
                break;
            case R.id.third_wait_time:
                if (checked)
                	userSelectedWaitTime = THIRD_WAIT_TIME;
                break;
            case R.id.long_wait_time:
                if (checked)
                	userSelectedWaitTime = LONG_WAIT_TIME;
                break;   
        }
        timerView.setText("timer: " + convertSeconds(userSelectedWaitTime));
    }
    
    private void setAwakeView() {
    	// set or update the selected awake hours text view for LSE to operate within
    	awakeView.setText("Awake hours set: " + userAwakeStart + " to " + userAwakeEnd);   
    }    
/*
 * Notification control    
 */    
    @SuppressLint("DefaultLocale")
	private void logNotification(String owner) {
    	// only log our owners    	    	    	
    	if (isAwakeTime()) {    	
	    	if (owner != null) {
	    		// combine these types into sms
	    		if ( (owner.toLowerCase().contains(SMS)) || (owner.toLowerCase().contains(MMS)) ) { 
	    			// only allow one runnable
	    	    	if (!running) {
	    	    		notifyOwner = SMS;
	    	    		notifyTimer();
	    	    		running = true;
	    	    	}
	    		}
	    	}
    	}
    }
    
    private void notifyTimer() {
    	logger("notifyTimer: " + userSelectedWaitTime);
    	
    	if (VERSION_ALARM) {
	    	setAlarmNotification();
    	}
    	else {
	    	// wakelock version
	    	buildNotification();
	    	runScheduler();
    	}
    }
    
// new AlarmManager notify
    private void setAlarmNotification() {
    	Context context = getBaseContext();
    	Intent notifyIntent = new Intent();
    	notifyIntent.setAction(ALARM_ACTION);
    	
    	if (alarmIntent != null) {
    		// already set
    		return;
    	}
    	else {
    		alarmManager = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
    		alarmIntent = PendingIntent.getBroadcast(context, 0, notifyIntent, 0);
    		alarmManager.setRepeating(
    				AlarmManager.RTC_WAKEUP, 
    				System.currentTimeMillis() + userSelectedWaitTime, 
    				userSelectedWaitTime, 
    				alarmIntent);
    	}
    }
    
    private void removeAlarmNotification(String owner) {
    	logger("clear owner: " + owner);
    	if (alarmIntent != null) {
    		alarmManager.cancel(alarmIntent);
    		alarmIntent = null;
    	}
    	if (mBuilder != null) {
    		notificationManager.cancelAll();
    		mBuilder = null;
    	}
    	running = false;
    }
    
    private void runScheduler() {
    	PowerManager pm = (PowerManager) this.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG); 
        wakeLock.acquire();
    	
    	scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
            	logger("executor echo.");
                echoNotification();
            }
        }, userSelectedWaitTime, userSelectedWaitTime, TimeUnit.MILLISECONDS);
    }

    private void clearNotifications(String owner) {
    	// check for any of our owners, 
    	// clear any notifications this app has,
    	// cancel any schedulers here too
    	// check that we have an echo associated
    	logger("clear owner: " + owner);
		if (wakeLock != null) {
	    	if (wakeLock.isHeld()) {
			    wakeLock.release();
			    wakeLock = null;
			}
		}
    	if (mBuilder != null) {
    		notificationManager.cancelAll();
    		mBuilder = null;
    	}		
    	if (scheduler != null) {
    		scheduler.shutdown();
    		scheduler = null;
    	}
    	running = false;
    }
    

    private void buildNotification() {
    	mBuilder = new NotificationCompat.Builder(getApplicationContext())
    	        .setSmallIcon(R.drawable.ic_launcher)
    	        .setContentTitle("Little Sir Echo Notifier")
    	        .setContentText(notifyOwner + " message waiting...")
    	        .setAutoCancel(true)
    	        .setSound(soundUri);
    }
    
    private void echoNotification() {
    	// may need to remove all first then:
    	logger("echo notify.");
    	notificationManager.notify(0, mBuilder.build());
    }    
/* 
 *  inner receiver class      
 */      
    class LSEServiceReceiver extends BroadcastReceiver {   	
    	@Override
    	public void onReceive(Context context, Intent intent) {  		
    		String type = intent.getStringExtra("type");
    		String owner = intent.getStringExtra("owner"); 
    		if (type == "posted") {
    			logNotification(owner);
    		}
    		else if (type == "removed") {
    			if (VERSION_ALARM) {
    				removeAlarmNotification(owner);
    			}
    			else {
	    			// wakelock version
	    			clearNotifications(owner);
    			}
    		}            
    		listenerView.setText(type + " " + owner);
    		logger("type: " + owner);
    	}
    }    
/*
 *  utilities        
 */
    @SuppressLint("DefaultLocale")
	private String convertSeconds(long timeSet) {
        int s = (int) ((timeSet / 1000) % 60);
        int m = (int) ((timeSet / 1000) / 60);
        return String.format("%02d:%02d", m, s);
    }
    
    private boolean isAwakeTime() {
    	// return whether current time is between "awake" hours
    	// will get 24h value (0-23)
    	// default awake == 7-23
    	int hourNow = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
    	logger("hourNow: " + hourNow);
    	return (hourNow >= userAwakeStart && hourNow <= userAwakeEnd);
    }
    
    private void logger(String message) {
    	if (DEBUG) {
    		Log.d(TAG, message);
    	}    	
    }
}

