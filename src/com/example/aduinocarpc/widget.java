package com.example.aduinocarpc;


import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class widget extends AppWidgetProvider {
	public static String ACTION_WIDGET_RECEIVER = "ActionReceiverWidget";
	private static final String TAG = "arduinoPc";
	
	
	@Override
	public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
		    context.startService(new Intent(context, irService.class));
		    Log.d(TAG, "onUpdate");
		    
		    RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
		    
            //Подготавливаем Intent для Broadcast
            Intent active = new Intent(context, widget.class);
            active.setAction(ACTION_WIDGET_RECEIVER);
            active.putExtra("msg", "try connect");

            //создаем наше событие
            PendingIntent actionPendingIntent = PendingIntent.getBroadcast(context, 0, active, 0);

            //регистрируем наше событие
            remoteViews.setOnClickPendingIntent(R.id.temp, actionPendingIntent);

            //обновляем виджет
            appWidgetManager.updateAppWidget(appWidgetIds, remoteViews);
	}
	
	 @Override
     public void onReceive(Context context, Intent intent) {

          //Ловим наш Broadcast, проверяем и выводим сообщение
          final String action = intent.getAction();
          if (ACTION_WIDGET_RECEIVER.equals(action)) {
        	  context.startService(new Intent(context, irService.class));
               String msg = "null";
               try {
                     msg = intent.getStringExtra("msg");
               } catch (NullPointerException e) {
                     Log.e("Error", "msg = null");
               }
               Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
          } 
          super.onReceive(context, intent);
    }
//	@Override
//	public void onDeleted(Context context, int[] appWidgetIds){
//		 Intent serviceIntent = new Intent(context, irService.class);
//		 context.stopService(serviceIntent);
//		 super.onDeleted(context, appWidgetIds);
//	}
	 
}
