package com.example.aduinocarpc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import android.appwidget.AppWidgetManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.RemoteViews;
import android.media.AudioManager;

import com.maxmpz.poweramp.player.PowerampAPI;

public class irService extends Service {
	private static final String GET_TEMP = "1";
	private static final String GET_CODE = "2";

	private static final int VOL_UP_CODE = 2;
	private static final int VOL_DOWN_CODE = 4;
	private static final int PREV_CODE = 1;
	private static final int NEXT_CODE = 3;
	private static final int UP_CODE = 7;
	private static final int DOWN_CODE = 5;
	private static final int EQ_CODE = 6;
	private static final int PLAY_PAUSE_CODE = 8;

	byte delimiterCode = 67;
	byte delimiterTemp = 84;

	private boolean shuffle_state = false;
	int i = 0;

	private BluetoothAdapter mBluetoothAdapter = null;
	private BluetoothSocket btSocket = null;
	private OutputStream outStream = null;
	private static String address = "20:13:12:02:19:70";
	private static final UUID MY_UUID = UUID
			.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private InputStream inStream = null;
	Handler handler = new Handler();
	byte delimiter = 10;
	boolean stopWorker = false;
	boolean threadStatus = false;
	int readBufferPosition = 0;
	byte[] readBuffer = new byte[1024];

	private static final String TAG = "arduinoPc SERVICE";
	public AudioManager audio;
	Handler h;
	int intRes;

	public void onCreate() {
		super.onCreate();
		audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		Log.d(TAG, "onCreate");
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "onStartCommand");
		if (CheckBt()) {
			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
			Log.e("Jon", device.toString());
			Connect();
		} else {
			Log.d(TAG, "Cannot connect");
		}
		return super.onStartCommand(intent, flags, startId);
	}

	private boolean CheckBt() {
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		if (!mBluetoothAdapter.isEnabled() || mBluetoothAdapter == null) {
			updateWidget("Bluetooth Disabled!");
			return false;
		} else {
			return true;
		}

	}

	private void writeData(String data) {
		try {
			outStream = btSocket.getOutputStream();
		} catch (IOException e) {
			Log.d(TAG, "Bug BEFORE Sending stuff", e);
		}

		String message = data;
		byte[] msgBuffer = message.getBytes();

		try {
			outStream.write(msgBuffer);
		} catch (IOException e) {
			Log.d(TAG, "Bug while sending stuff", e);
		}
	}

	public void Connect() {
		resetConnection();
		Log.d(TAG, address);
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		Log.d(TAG, "Connecting to ... " + device);
		updateWidget("Connecting to " + device);
		// mBluetoothAdapter.cancelDiscovery();
		try {
			btSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
			btSocket.connect();
			Log.d(TAG, "Connection made...");
			updateWidget("Connection made");
			try {
				Log.d(TAG, "Reconnect");
				TimeUnit.MILLISECONDS.sleep(50);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			beginListenForData();
			try {
				Log.d(TAG, "Reconnect");
				TimeUnit.MILLISECONDS.sleep(50);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		} catch (IOException e) {
			try {
				btSocket.close();
				Log.d(TAG, "socket closed");
			} catch (IOException e2) {
				updateWidget("Unable to socket close");
				Log.d(TAG, "Unable to end the connection");
			}

			updateWidget("Socket creation failed");
			Log.d(TAG, "Socket creation failed");
			try {
				Log.d(TAG, "Reconnect");
				TimeUnit.MILLISECONDS.sleep(1000);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			Connect();
		}

	}

	private void resetConnection() {
		if (inStream != null) {
			try {
				inStream.close();
			} catch (Exception e) {
			}
			inStream = null;
		}

		if (outStream != null) {
			try {
				outStream.close();
			} catch (Exception e) {
			}
			outStream = null;
		}

		if (btSocket != null) {
			try {
				btSocket.close();
			} catch (Exception e) {
			}
			btSocket = null;
		}
		Log.d(TAG, "reset connection");
	}

	public void updateWidget(String temp) {
		Context context = this;
		AppWidgetManager appWidgetManager = AppWidgetManager
				.getInstance(context);
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
				R.layout.widget);
		ComponentName thisWidget = new ComponentName(context, widget.class);
		remoteViews.setTextViewText(R.id.temp, temp);
		appWidgetManager.updateAppWidget(thisWidget, remoteViews);
	}

	public void updateWidgetTempTextColor(String color) {
		Context context = this;
		AppWidgetManager appWidgetManager = AppWidgetManager
				.getInstance(context);
		RemoteViews remoteViews = new RemoteViews(context.getPackageName(),
				R.layout.widget);
		ComponentName thisWidget = new ComponentName(context, widget.class);
		remoteViews.setTextColor(R.id.temp, Color.parseColor(color));
		appWidgetManager.updateAppWidget(thisWidget, remoteViews);
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

	public void beginListenForData() {
		try {

			inStream = btSocket.getInputStream();
			h = new Handler() {
				String strBuffer = "";

				public void handleMessage(Message msg) {

					if (msg.obj != null) {
						String message = "";
						byte[] ba = (byte[]) msg.obj;
						try {
							message = convertString(ba);
							strBuffer += message;
						} catch (UnsupportedEncodingException e) {
						}

						Pattern p = Pattern
								.compile("\\@(T|C)([0-9.\\-]+)\\$(.*)");
						Matcher m = p.matcher(strBuffer);

						Log.d("RX", "buffer: " + strBuffer);
						if (m.find()) {
							Log.d("RX", "Message: " + m.group(1) + m.group(2));
							Log.d("RX", "tRASH: " + m.group(3));
							strBuffer = m.group(3);
							parseData(m.group(2), m.group(1));
						} else {
							Log.d("RX", "raw: " + message);
						}

						Log.d("DD", "ba r: " + ba.length);
						
					} else {
						Log.d("DD", "NULL");
					}
				};
			};

			Thread workerThread = new Thread(new Runnable() {

				public void run() {

					while (true) {
						// Log.d(TAG, "Thread work");
						try {
							int bytesAvailable = inStream.available();
							if (bytesAvailable > 0) {

								byte[] packetBytes = new byte[bytesAvailable];
								inStream.read(packetBytes);

								// Bundle bundle = new Bundle();
								// bundle.putByteArray("data", packetBytes);
								// Message m = new Message();
								// m.setData(bundle);
								// h.sendMessage(m);
								// m.sendToTarget();
								// Log.d(TAG, "bytesAvailable"+m);
								h.obtainMessage(0, bytesAvailable, -1,
										packetBytes).sendToTarget();
								Log.d("DD", "ba s: " + bytesAvailable);
							}
						} catch (IOException ex) {
							stopWorker = true;
						}
					}
				}
			});

			workerThread.start();
		} catch (IOException e) {
			Log.d(TAG, "create input socket error", e);
			// Connect();
		}
	}

	public void parseData(String message, String code) {

		if (code.equals("C")) {
			try {
				try {
					intRes = Integer.parseInt(message);
				} catch (NumberFormatException e) {
					e.printStackTrace();
				}
				doAction(intRes);
				Log.d(TAG, "do action code = " + message);
			} catch (NumberFormatException e) {
				Log.e(TAG, e.toString());
			}
		}

		if (code.equals("T")) {
			updateWidget(message + "°");//
			if (message.startsWith("-")) {
				updateWidgetTempTextColor("#00bcff");
			} else {
				updateWidgetTempTextColor("#ff9000");
			}

		}

//		Log.d(TAG, "byte: " + packetBytes.length);
//		byte b = packetBytes[i];
//		if (b == delimiterCode || nextC) {
//			if (packetBytes.length == 1) {
//				nextC = true;
//				continue;
//			}
//			if (nextC) {
//
//				byte[] encodedBytes = new byte[readBufferPosition];
//				System.arraycopy(readBuffer, 0, encodedBytes, 0,
//						encodedBytes.length);
//				String data = "";
//				try {
//					data = convertString(packetBytes);
//				} catch (UnsupportedEncodingException e) {
//				}
//				String codestring = data;
//				readBufferPosition = 0;
//				try {
//					if (codestring.endsWith("c")) {
//						codestring = codestring.substring(0,
//								codestring.length() - 1);
//						try {
//							intRes = Integer.parseInt(codestring);
//						} catch (NumberFormatException e) {
//							e.printStackTrace();
//						}
//						doAction(intRes);
//						Log.d(TAG, "do action code = " + codestring);
//					}
//				} catch (NumberFormatException e) {
//					Log.e(TAG, e.toString());
//				}
//				nextC = false;
//
//			} else {
//				byte[] encodedBytes = new byte[readBufferPosition];
//				System.arraycopy(readBuffer, 0, encodedBytes, 0,
//						encodedBytes.length);
//
//				String codestring = "";
//				try {
//					codestring = convertString(packetBytes);
//				} catch (UnsupportedEncodingException e) {
//				}
//				Log.d(TAG, "recieved code bytes: " + packetBytes);
//				readBufferPosition = 0;
//				try {
//					if (codestring.startsWith("C")) {
//						codestring = codestring.replace("C", "");
//						codestring = codestring.substring(0,
//								codestring.length() - 1);
//						try {
//							intRes = Integer.parseInt(codestring);
//						} catch (NumberFormatException e) {
//							e.printStackTrace();
//						}
//						doAction(intRes);
//						Log.d(TAG, "do action code = " + codestring);
//					}
//				} catch (NumberFormatException e) {
//					Log.e(TAG, e.toString());
//				}
//			}
//		} else if (b == delimiterTemp || nextT) {
//
//			if (packetBytes.length == 1) {
//				nextT = true;
//				continue;
//			}
//			if (nextT) {
//				byte[] encodedBytes = new byte[readBufferPosition];
//				System.arraycopy(readBuffer, 0, encodedBytes, 0,
//						encodedBytes.length);
//				String data = "";
//				try {
//					data = convertString(packetBytes);
//				} catch (UnsupportedEncodingException e) {
//				}
//
//				String tempstring = data;
//				Log.d(TAG, "recieved temp bytes: " + packetBytes);
//				readBufferPosition = 0;
//				try {
//					tempstring = tempstring.substring(0,
//							tempstring.length() - 1);
//					updateWidget(tempstring + "°");
//					if (tempstring.startsWith("-")) {
//						updateWidgetTempTextColor("#00bcff");
//					} else {
//						updateWidgetTempTextColor("#ff9000");
//					}
//				} catch (NumberFormatException e) {
//					Log.e(TAG, e.toString());
//				}
//				nextT = false;
//			} else {
//				byte[] encodedBytes = new byte[readBufferPosition];
//				System.arraycopy(readBuffer, 0, encodedBytes, 0,
//						encodedBytes.length);
//				String data = "";
//				try {
//					data = convertString(packetBytes);
//				} catch (UnsupportedEncodingException e) {
//				}
//				String tempstring = data;
//				Log.d(TAG, "recieved temp bytes: " + packetBytes);
//				readBufferPosition = 0;
//				try {
//					// Log.d(TAG, str);
//					int start = tempstring.indexOf("T");
//					int stop = tempstring.indexOf("t");
//					if (start >= 0 && stop > 0) {
//						if (tempstring.startsWith("T")) {
//							tempstring = tempstring.substring(start + 1, stop);
//							// Log.d(TAG, str);
//							updateWidget(tempstring + "°");
//							if (tempstring.startsWith("-")) {
//								updateWidgetTempTextColor("#00bcff");
//							} else {
//								updateWidgetTempTextColor("#ff9000");
//							}
//						}
//					}
//				} catch (NumberFormatException e) {
//					Log.e(TAG, e.toString());
//				}
//			}
//
//			// Log.d(TAG, str);
//
//		} else {
//			readBuffer[readBufferPosition++] = b;
//		}
	}

	private String convertString(byte[] bytes)
			throws UnsupportedEncodingException {
		final String data = new String(bytes, "UTF-8");
		return data;
	}

	private void doAction(int intRes) {

		switch (intRes) {
		case VOL_UP_CODE:

			audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
					AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
			Log.d(TAG, "Volume Up");

			break;
		case VOL_DOWN_CODE:
			audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
					AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
			Log.d(TAG, "Volume Down");
			break;
		case PREV_CODE:
			startService(new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
					PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT));
			Log.d(TAG, "Next");
			break;
		case NEXT_CODE:
			startService(new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
					PowerampAPI.COMMAND, PowerampAPI.Commands.PREVIOUS));
			Log.d(TAG, "Prev");
			break;
		case UP_CODE:
			startService(new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
					PowerampAPI.COMMAND, PowerampAPI.Commands.NEXT_IN_CAT));
			Log.d(TAG, "Up");
			break;
		case DOWN_CODE:
			startService(new Intent(PowerampAPI.ACTION_API_COMMAND).putExtra(
					PowerampAPI.COMMAND, PowerampAPI.Commands.PREVIOUS_IN_CAT));
			Log.d(TAG, "Down");
			break;
		case PLAY_PAUSE_CODE:
			startService(new Intent(PowerampAPI.ACTION_API_COMMAND)
					.putExtra(PowerampAPI.COMMAND,
							PowerampAPI.Commands.TOGGLE_PLAY_PAUSE));
			Log.d(TAG, "Play/Pause");
			break;
		case EQ_CODE:
			shuffle_state = !shuffle_state;
			if (shuffle_state) {
				startService(new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.COMMAND,
								PowerampAPI.Commands.SHUFFLE).putExtra(
								PowerampAPI.SHUFFLE,
								PowerampAPI.ShuffleMode.SHUFFLE_ALL));
				Log.d(TAG, "Shuffle On");
			} else {
				startService(new Intent(PowerampAPI.ACTION_API_COMMAND)
						.putExtra(PowerampAPI.COMMAND,
								PowerampAPI.Commands.SHUFFLE).putExtra(
								PowerampAPI.SHUFFLE,
								PowerampAPI.ShuffleMode.SHUFFLE_NONE));
				Log.d(TAG, "Shuffle Off");
			}
			break;
		}
	}

}
