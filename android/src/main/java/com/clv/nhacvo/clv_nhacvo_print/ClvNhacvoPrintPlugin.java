package com.clv.nhacvo.clv_nhacvo_print;


import androidx.annotation.NonNull;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.*;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.RequestPermissionsResultListener;
import io.flutter.plugin.common.EventChannel.EventSink;
import io.flutter.plugin.common.EventChannel.StreamHandler;

import com.clv.nhacvo.printer.EscPosPrinter;
import com.clv.nhacvo.printer.connection.bluetooth.BluetoothConnection;
import com.clv.nhacvo.printer.connection.bluetooth.BluetoothConnections;
import com.clv.nhacvo.printer.connection.bluetooth.BluetoothPrintersConnections;
import com.clv.nhacvo.printer.textparser.PrinterTextParserImg;
import com.clv.nhacvo.printer.exceptions.EscPosConnectionException;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.widget.Toast;

/** ClvNhacvoPrintPlugin */
public class ClvNhacvoPrintPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler, RequestPermissionsResultListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private static final String TAG = "BluetoothPrintPlugin";

  private MethodChannel channel;
  private MethodChannel _channel;
  private MethodChannel channelConnect;
  private MethodChannel channelPrint;
  private MethodChannel getListBluetoothPrinters;
  private MethodChannel checkState;
  private EventChannel stateChannel;
  private LocationRequest locationRequest;
  private static final int REQUEST_CHECK_SETTINGS = 10001;
  private ArrayList<String> mDeviceList = new ArrayList<String>();
  private BluetoothAdapter mBluetoothAdapter;
  Set<BluetoothDevice> pairedDevices;
  ArrayList<DevicesModel> deviceResult = new ArrayList<DevicesModel>();
  ArrayList<DevicesModel> scanDevice = new ArrayList<DevicesModel>();
  ArrayList<DevicesModel> connectedDevice = new ArrayList<DevicesModel>();
  public static final int PERMISSION_BLUETOOTH = 1;
  private Object initializationLock = new Object();
  private MethodChannel.Result globalChannelResult;
  private FlutterEngine flutterEngine;
  private ThreadPool threadPool;


  private FlutterPluginBinding pluginBinding;
  private ActivityPluginBinding activityBinding;
  private Application application;
  private Activity activity;
  private Application context;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    pluginBinding = flutterPluginBinding;

  }
  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding binding) {
    onAttachedToActivity(binding);
  }
  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    checkPermission();
    globalChannelResult = result;
    try {
      Map<String, Object> arguments = call.arguments();
      System.out.println("NHACVO_DEMO: "+ call.method);
      if (call.method.toString() == "getPlatformVersion") {
        result.success("Android ${android.os.Build.VERSION.RELEASE}");
      } else  if (call.method.equals("getMessage")) {
        String message = "Android say hi!";
        result.success(message);
      } else if (call.method.equals("getDevices")) {
        ArrayList<DevicesModel> arrDevice = onGetDevicesBluetooth();
        result.success(arrDevice);
      } else if (call.method.equals("onPrint")) {
        System.out.println("I am here onPrint");
        byte[] bitmapInput = (byte[]) arguments.get("bitmapInput");
        int printerDpi = (int) arguments.get("printerDpi");
        int heightMax = (int) arguments.get("heightMax");
        int widthMax = (int) arguments.get("widthMax");
        int countPage = (int) arguments.get("countPage");
        Map<String, Object> arrStatus = onPrint(bitmapInput, printerDpi, widthMax, heightMax, 0, countPage);
        result.success(arrStatus);
      }else if (call.method.equals("onBluetooth")) {
        turnOnBluetooth();
      }else if (call.method.equals("offBluetooth")) {
        turnOffBluetooth();
      }
      else if (call.method.equals("checkStateBluetooth")) {
        boolean checkbluetooth = checkState();
        result.success(checkbluetooth);
      }
      else if (call.method.equals("scanDeviceBluetooth")) {
        bluetoothScanning();
      }
      else if (call.method.equals("action_start_scan")) {
        scan(result);
      }
      else if (call.method.equals("connectDevice")) {
        connect(result,arguments);
      }
      else if (call.method.equals("state")) {
        state(result);
      }
      else if (call.method.equals("isConnected")) {
        result.success(threadPool != null);
      }
    } catch (Exception e) {
      result.error("500", "Server Error", e.getMessage());
    }
  }


  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channelPrint.setMethodCallHandler(null);
    channel.setMethodCallHandler(null);
    getListBluetoothPrinters.setMethodCallHandler(null);
    _channel.setMethodCallHandler(null);
    checkState.setMethodCallHandler(null);
    channelConnect.setMethodCallHandler(null);
    pluginBinding = null;

  }


  public ClvNhacvoPrintPlugin(){
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding binding) {
    activityBinding = binding;
    setup(
            pluginBinding.getBinaryMessenger(),
            (Application) pluginBinding.getApplicationContext(),
            activityBinding.getActivity(),
            null,
            activityBinding);
    flutterEngine = new FlutterEngine(application);
  }

  @Override
  public void onDetachedFromActivity() {
    Log.i(TAG, "onDetachedFromActivity");
    context = null;
    activityBinding.removeRequestPermissionsResultListener(this);
    activityBinding = null;
    channel.setMethodCallHandler(null);
    _channel.setMethodCallHandler(null);
    channelPrint.setMethodCallHandler(null);
    getListBluetoothPrinters.setMethodCallHandler(null);
    checkState.setMethodCallHandler(null);
    channelConnect.setMethodCallHandler(null);
    stateChannel.setStreamHandler(null);
    channel = null;
    _channel = null;
    channelPrint = null;
    getListBluetoothPrinters = null;
    checkState = null;
    channelConnect = null;
    stateChannel = null;
    mBluetoothAdapter = null;
    application = null;
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity();
  }

  private void setup(
          final BinaryMessenger messenger,
          final Application application,
          final Activity activity,
          final PluginRegistry.Registrar registrar,
          final ActivityPluginBinding activityBinding) {
    synchronized (initializationLock) {
      Log.i(TAG, "setup");
      this.activity = activity;
      this.application = application;
      this.context = application;
      channel = new MethodChannel(messenger, "com.clv.demo/print");
      _channel = new MethodChannel(messenger, "flutter_scan_bluetooth");
      channelPrint = new MethodChannel(messenger, "com.clv.demo/print");
      getListBluetoothPrinters = new MethodChannel(messenger, "com.clv.demo/getListBluetoothPrinters");
      checkState = new MethodChannel(messenger, "com.clv.demo/checkState");
      channelConnect = new MethodChannel(messenger, "com.clv.demo/connect");
      stateChannel = new EventChannel(messenger, "stateBluetooth");
      stateChannel.setStreamHandler(stateHandler);
      channel.setMethodCallHandler(this);
      _channel.setMethodCallHandler(this);
      channelPrint.setMethodCallHandler(this);
      getListBluetoothPrinters.setMethodCallHandler(this);
      checkState.setMethodCallHandler(this);
      channelConnect.setMethodCallHandler(this);
      mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
      mBluetoothAdapter.startDiscovery();
      if (registrar != null) {
        // V1 embedding setup for activity listeners.
        registrar.addRequestPermissionsResultListener(this);
      } else {
        // V2 embedding setup for activity listeners.
        activityBinding.addRequestPermissionsResultListener(this);
      }
    }
  }


  private static final int REQUEST_FINE_LOCATION_PERMISSIONS = 1452;


  private ArrayList<DevicesModel> onGetDevicesBluetooth() {
    pairedDevices = mBluetoothAdapter.getBondedDevices();
    deviceResult = new ArrayList<>();

    for (BluetoothDevice bt : pairedDevices) {
//      devices.add(new DevicesModel(bt.getName(), bt.getAddress()));
    }
    return deviceResult;
  }

  private Map<String, Object> onPrint(
          byte[] bitmapInput,
          int printerDpi ,
          int heightMax ,
          int widthMax,
          int callback,
          int countPage){
    Map<String, Object>  dataMap = new HashMap<>();
    String _message = "";
//    if(callback < 3){
//      callback +=1;
//    }else{
//      _message = "Callback Function Error";
//      dataMap.put("message",_message);
//      return dataMap;
//    }
    for(int i = 0 ; i < 3 ; i++){
      try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
          ActivityCompat.requestPermissions( activityBinding.getActivity(), new String[]{Manifest.permission.BLUETOOTH}, PERMISSION_BLUETOOTH);
        } else {
          BluetoothConnection connection = BluetoothPrintersConnections.selectFirstPaired();
          if (connection != null) {
            EscPosPrinter printer = new EscPosPrinter(connection, printerDpi, 80f, 32);

            byte[]  bitMapData = bitmapInput;// stream.toByteArray()
            Bitmap decodedByte = BitmapFactory.decodeByteArray(bitMapData, 0, bitMapData.length);
            double width1 = decodedByte.getWidth();
            double height1 = decodedByte.getHeight();
            System.out.println(width1);
            System.out.println(height1);
            double widthTemp = 570;
            double heightTemp = 570.0*(height1/width1);
            System.out.println(widthTemp);
            System.out.println(heightTemp);


//            widthTemp = widthMax < 580 ? 580 : widthMax;

//          if(heightTemp > 900){
//            heightTemp = 900; // 900
//          }else if(heightTemp <200){
//            heightTemp = 200; // 200
//          }
//          else
//          {
//            heightTemp = heightTemp * countPage;
//          }

            System.out.println( "-----------------Start--------------------");
            System.out.println( "Input:  " + widthMax + " || " + heightMax);
            System.out.println( "Current:  " + (int)widthTemp + " || " + (int)heightTemp);
            System.out.println( "------------------End---------------------");

            Bitmap resizedBitmap = Bitmap.createScaledBitmap(decodedByte, (int)widthTemp, (int)heightTemp, false);
            decodedByte.recycle();
            int width = resizedBitmap.getWidth();
            int height = resizedBitmap.getHeight();

            StringBuilder textToPrint = new StringBuilder();
            for(int y = 0; y < height; y += 256) {
              Bitmap bitmap = Bitmap.createBitmap(resizedBitmap, 0, y, width, (y + 256 >= height) ? height - y : 256);
              textToPrint.append("[C]<img>" + PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap) + "</img>\n");
            }
            printer.printFormattedTextAndCut(textToPrint.toString());
            _message = "Success !!!";
            break;
          } else {
            // println("\"No printer was connected!\"");
            _message = "No printer was connected !!!";
//          onPrint(bitmapInput, printerDpi, widthMax, heightMax, callback, countPage);
          }
        }
      }
      catch (Exception e) {
        _message = "Error";
        // println(e.getMessage());
      }
    }
    dataMap.put("message",_message);
    return dataMap;
  }

  private void turnOnBluetooth (){
    LocationManager manager = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );
    if (!manager.isProviderEnabled( LocationManager.GPS_PROVIDER ) ) {
      turnOnGPS();
      IntentFilter filter = new IntentFilter();
      filter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
      context.registerReceiver(getReceiver, filter);
    }
    else{
      try {
        if(mBluetoothAdapter == null)
        {
          Toast.makeText(context,"Bluetooth Not Supported",Toast.LENGTH_SHORT).show();
        }
        else{
          if(!mBluetoothAdapter.isEnabled()){
            activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),1);
            Toast.makeText(context,"Bluetooth Turned ON",Toast.LENGTH_SHORT).show();
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(mBroadcastReceiver1, filter);
          }
        }
      }catch (Exception ex){
        System.out.println(ex.getMessage());
      }
    }
  }

  private boolean checkState(){
    boolean check = false;
    if(mBluetoothAdapter.isEnabled()){
      System.out.println("State 1: Bluetooth turn on !!");
      check = true;
    }else{
      System.out.println("State 1: Bluetooth turn off !!");
      check = false;
    }
    return check;
  }

//  private final BroadcastReceiver brCheckState = new BroadcastReceiver() {
//
//    @Override
//    public void onReceive(Context context, Intent intent) {
//      final String action = intent.getAction();
//      if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
//        if(mBluetoothAdapter.isEnabled()){
//          System.out.println("State 1: Bluetooth turn on !!");
//        }else{
//          System.out.println("State 1: Bluetooth turn off !!");
//        }
//      }
//    }
//  };

  private final BroadcastReceiver getReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive( Context context, Intent intent )
    {
      LocationManager manager = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );
      if (intent.getAction().matches(LocationManager.PROVIDERS_CHANGED_ACTION)) {
        try {
          if(mBluetoothAdapter == null)
          {
            Toast.makeText(context,"Bluetooth Not Supported",Toast.LENGTH_SHORT).show();
          }
          else{
            if(!mBluetoothAdapter.isEnabled() && manager.isProviderEnabled( LocationManager.GPS_PROVIDER )){
              activity.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),1);
              Toast.makeText(context,"Bluetooth Turned ON",Toast.LENGTH_SHORT).show();
              context.unregisterReceiver(getReceiver);
              IntentFilter filter = new IntentFilter();
              filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
              context.registerReceiver(mBroadcastReceiver1, filter);
            }
          }
        }catch (Exception ex){
          System.out.println(ex.getMessage());
        }
      }
    }
  };


  private final BroadcastReceiver mBroadcastReceiver1 = new BroadcastReceiver() {

    @Override
    public void onReceive(Context context, Intent intent) {
      final String action = intent.getAction();

      if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) && mBluetoothAdapter.isEnabled()) {
        globalChannelResult.success("scan");
        context.unregisterReceiver(mBroadcastReceiver1);
      }
    }
  };

  private void turnOffBluetooth (){
    deviceResult = new ArrayList<>();
    scanDevice = new ArrayList<>();
    connectedDevice = new ArrayList<>();
    try {
      mBluetoothAdapter.disable();
      Toast.makeText(context,"Bluetooth Turned OFF", Toast.LENGTH_SHORT).show();
    }catch (Exception ex){
      System.out.println(ex.getMessage());
    }
  }

  private void turnOnGPS(){
    locationRequest = LocationRequest.create();
    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    locationRequest.setInterval(5000);
    locationRequest.setFastestInterval(2000);

    LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest);
    builder.setAlwaysShow(true);

    Task<LocationSettingsResponse> result = LocationServices.getSettingsClient(context.getApplicationContext())
            .checkLocationSettings(builder.build());

    result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
      @Override
      public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
        try {
          LocationSettingsResponse response = task.getResult(ApiException.class);
          Toast.makeText(context, "GPS is already tured on", Toast.LENGTH_SHORT).show();
        } catch (ApiException e) {
          switch (e.getStatusCode()) {
            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
              try {
                ResolvableApiException resolvableApiException = (ResolvableApiException)e;
                resolvableApiException.startResolutionForResult(activity,REQUEST_CHECK_SETTINGS);
              } catch (IntentSender.SendIntentException ex) {
                ex.printStackTrace();
              }
              break;
            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
              //Device does not have location
              break;
          }
        }
      }
    });
  }


  private void bluetoothScanning(){
    LocationManager manager = (LocationManager) context.getSystemService( Context.LOCATION_SERVICE );
    deviceResult = new ArrayList<>();
    scanDevice = new ArrayList<>();
    connectedDevice = new ArrayList<>();
    if(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
      IntentFilter filter = new IntentFilter();
      checkPermission();
      filter.addAction(BluetoothDevice.ACTION_FOUND);
      filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
      context.registerReceiver(receiver, filter);
    }else{
      globalChannelResult.success("disable");
    }
  }


  public void checkPermission() {
    if (Build.VERSION.SDK_INT >= 23) {
      if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothAdapter.startDiscovery();
      } else {
        ActivityCompat.requestPermissions(activity, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,}, 1);
      }
    }
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == REQUEST_FINE_LOCATION_PERMISSIONS) {
      if (requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
//        mBluetoothAdapter.startDiscovery();
      } else {
        checkPermission();
      }
//      onRequestPermissionsResult(requestCode, permissions, grantResults);
      return true;
    }
    return false;
  }

//  private final BroadcastReceiver receiver = new BroadcastReceiver() {
//    public void onReceive(Context context, Intent intent) {
//      String action = intent.getAction();
//      // scan device
//      if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//        if(scanDevice.size()>0){
//          boolean check = false;
//          for (int i=0;i<scanDevice.size();i++){
//            if(scanDevice.get(i).getDeviceAddress().equals(device.getAddress())){
//              check = true;
//              return;
//            }
//          }
//          if(!check && device.getBluetoothClass().getDeviceClass() == 1664){
//            scanDevice.add(new DevicesModel(device.getName(),device.getAddress(),false));
//            System.out.println("Printer: " + device.getName() + " | "+ device.getAddress() + " | " + device.getUuids() + " | " + device.getBluetoothClass().getDeviceClass());
//          }
//        }else{
//          if(device.getBluetoothClass().getDeviceClass() == 1664){
//            scanDevice.add(new DevicesModel(device.getName(),device.getAddress(),false));
//            System.out.println("Printer: " + device.getName() + " | "+ device.getAddress() + " | " + device.getUuids() + " | " + device.getBluetoothClass().getDeviceClass());
//          }
////          scanDevice.add(new DevicesModel(device.getName(),device.getAddress(),false));
//        }
//      }
//      else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//        String finalString = "";
//        // device connected
//        System.out.println(scanDevice);
//        pairedDevices = mBluetoothAdapter.getBondedDevices();
//        for (BluetoothDevice bt : pairedDevices) {
//          if (bt.getBluetoothClass().getDeviceClass() == 1664) {
//            connectedDevice.add(new DevicesModel(bt.getName(), bt.getAddress(),true));
//          }
////          for(int i = 0; i<scanDevice.size();i++){
////            if(scanDevice.get(i).getDeviceAddress().equals(bt.getAddress())){
////              if (bt.getBluetoothClass().getDeviceClass() == 1664) {
////                connectedDevice.add(new DevicesModel(bt.getName(), bt.getAddress(),true));
////              }
////            }
////          }
//        }
//        System.out.println("scanDevice: " + scanDevice.size());
//        System.out.println("connectedDevice: " + connectedDevice.size());
//
//        deviceResult.addAll(scanDevice);
//        deviceResult.addAll(connectedDevice);
//
//        for (int i = 0; i < deviceResult.size(); i++) {
//          for (int j=i+1; j < deviceResult.size(); j++) {
//            System.out.println("01: " + deviceResult.get(i).getDeviceAddress());
//            System.out.println("02: " + deviceResult.get(j).getDeviceAddress());
//            if(deviceResult.get(i).getDeviceAddress().equals(deviceResult.get(j).getDeviceAddress())) {
//              deviceResult.remove(i);
//            }
//          }
//        }
//
//        if(deviceResult.size() > 0){
//          for (int i=0;i<deviceResult.size();i++){
//            finalString = finalString + deviceResult.get(i).toDescription() + "&";
//          }
//          finalString = finalString.substring(0, finalString.length() - 1);
//          System.out.println("Device 3: " + finalString);
//          globalChannelResult.success(finalString);
//          context.unregisterReceiver(receiver);
//          mBluetoothAdapter.cancelDiscovery();
//        }else{
//          System.out.println("Khong co may in !!!");
//          globalChannelResult.success(finalString);
//          context.unregisterReceiver(receiver);
//          mBluetoothAdapter.cancelDiscovery();
//        }
//      }
//    }
//  };

  private static final String ACTION_NEW_DEVICE = "action_new_device";
  private static final String ACTION_SCAN_STOPPED = "action_scan_stopped";
  private static final String ACTION_NO_PRINTER = "action_no_printer";
  private boolean check = false;
  private List<Map<String,String>> listBondedDevice = new ArrayList<>();

  private final BroadcastReceiver receiver = new BroadcastReceiver() {
    public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
      if (BluetoothDevice.ACTION_FOUND.equals(action)) {
        if(device != null){
          if(device.getBluetoothClass().getDeviceClass() == 1664){
            _channel.invokeMethod(ACTION_NEW_DEVICE,toMap(device));
            check = true;
          }
//          _channel.invokeMethod(ACTION_NEW_DEVICE,toMap(device));
        }
      }else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
        if(!check){
          _channel.invokeMethod(ACTION_NO_PRINTER,null);
        }
        check = false;
        context.unregisterReceiver(receiver);
      }
    }
  };

  private Map<String, String> toMap(BluetoothDevice device) {
    Map<String, String> map = new HashMap<>();
    String name = device.getName();
    String address = device.getAddress();

    map.put("name", name);
    map.put("address",address);

    System.out.println(map);

    return map;
  }


  private void scan(Result result) {
    if(!mBluetoothAdapter.isDiscovering()){
      mBluetoothAdapter.cancelDiscovery();
    }
    mBluetoothAdapter.startDiscovery();

    IntentFilter filter = new IntentFilter();
    filter.addAction(BluetoothDevice.ACTION_FOUND);
    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
    context.registerReceiver(receiver, filter);

    pairedDevices = mBluetoothAdapter.getBondedDevices();
    List<Map<String,String>> mapList = new ArrayList<>();
    for (BluetoothDevice bt : pairedDevices) {
      if(bt.getBluetoothClass().getDeviceClass() == 1664){
        Map<String, String> map = new HashMap<String, String>();
        map.put("name", bt.getName());
        map.put("address",bt.getAddress());
        mapList.add(map);
        toMap(bt);
        System.out.println(bt);
      }
    }

    result.success(mapList);
  }

  private void connect(final Result result, Map<String, Object> args) {
    if (args.containsKey("address")) {
      String address = (String) args.get("address");
      BluetoothDevice device;
      device = mBluetoothAdapter.getRemoteDevice(address);
      BluetoothConnection connection = new BluetoothConnection(device);
      try {
        connection.connect();
      }catch (Exception e){
        System.out.println(e);
      }
      boolean checkState = connection.isCheck();
      if(checkState){
        result.success(true);
      }else{
        result.success(false);
      }
    }
  }

  // trạng thái bluetooth
  private void state(Result result){
    try {
      switch(mBluetoothAdapter.getState()) {
        case BluetoothAdapter.STATE_OFF:
          result.success(BluetoothAdapter.STATE_OFF);
          break;
        case BluetoothAdapter.STATE_ON:
          result.success(BluetoothAdapter.STATE_ON);
          break;
        case BluetoothAdapter.STATE_TURNING_OFF:
          result.success(BluetoothAdapter.STATE_TURNING_OFF);
          break;
        case BluetoothAdapter.STATE_TURNING_ON:
          result.success(BluetoothAdapter.STATE_TURNING_ON);
          break;
        default:
          result.success(0);
          break;
      }
    } catch (SecurityException e) {
      result.error("invalid_argument", "argument 'address' not found", null);
    }

  }

  // bắt sự kiên bluetooth thay đổi
  private final StreamHandler stateHandler = new StreamHandler() {
    private EventSink sink;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        Log.d(TAG, "stateStreamHandler, current action: " + action);

        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
          threadPool = null;
          sink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
        } else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
          sink.success(1);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
          threadPool = null;
          sink.success(0);
        }
      }
    };

    @Override
    public void onListen(Object o, EventSink eventSink) {
      sink = eventSink;
      IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
      filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
      filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
      filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
      context.registerReceiver(mReceiver, filter);
    }

    @Override
    public void onCancel(Object o) {
      sink = null;
      context.unregisterReceiver(mReceiver);
    }
  };

}

class DevicesModel{
  //  val id: String?, val deviceName: String?, val deviceAddress: String?
  String id;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getDeviceName() {
    return deviceName;
  }

  public void setDeviceName(String deviceName) {
    this.deviceName = deviceName;
  }

  public String getDeviceAddress() {
    return deviceAddress;
  }

  public void setDeviceAddress(String deviceAddress) {
    this.deviceAddress = deviceAddress;
  }

  public boolean isStateDevice() {
    return stateDevice;
  }

  public void setStateDevice(boolean stateDevice) {
    this.stateDevice = stateDevice;
  }

  String deviceName;
  String deviceAddress;
  boolean stateDevice;

  public DevicesModel(String deviceName, String deviceAddress, boolean stateDevice) {
    this.deviceName = deviceName;
    this.deviceAddress = deviceAddress;
    this.stateDevice = stateDevice;
  }

  String toDescription(){
    return deviceName + "|" + deviceAddress + "|" + stateDevice;
  }
}
