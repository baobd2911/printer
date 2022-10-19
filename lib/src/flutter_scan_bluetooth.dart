import 'dart:async';

import 'package:collection/collection.dart';
import 'package:flutter/services.dart';

class BluetoothDeviceScan {
  final String name;
  final String address;
  final bool paired;
  final bool nearby;

  const BluetoothDeviceScan(this.name, this.address, {this.nearby = false, this.paired = false});

  @override
  bool operator ==(Object other) =>
      identical(this, other) || other is BluetoothDeviceScan && runtimeType == other.runtimeType && name == other.name && address == other.address;

  @override
  int get hashCode => name.hashCode ^ address.hashCode;

  Map<String, dynamic> toMap() {
    return {'name': name, 'address': address};
  }

  @override
  String toString() {
    return 'BluetoothDevice{name: $name, address: $address, paired: $paired, nearby: $nearby}';
  }
}

class FlutterScanBluetooth {
  static final _singleton = FlutterScanBluetooth._();
  final MethodChannel _channel = const MethodChannel('flutter_scan_bluetooth');
  final List<BluetoothDeviceScan> _pairedDevices = [];
  final StreamController<BluetoothDeviceScan> _controller = StreamController.broadcast();
  final StreamController<bool> _scanStopped = StreamController.broadcast();

  factory FlutterScanBluetooth() => _singleton;

  FlutterScanBluetooth._() {
    _channel.setMethodCallHandler((methodCall) async {
      switch (methodCall.method) {
        case 'action_new_device':
          _newDevice(methodCall.arguments);
          break;
        case 'action_scan_stopped':
          _scanStopped.add(true);
          break;
        case 'action_no_printer':
          _noDevice(methodCall.arguments);
          break;
      }
      return null;
    });
  }

  Stream<BluetoothDeviceScan> get devices => _controller.stream;

  Stream<bool> get scanStopped => _scanStopped.stream;

  Future<void> requestPermissions() async {
    await _channel.invokeMethod('action_request_permissions');
  }

  Future<void> startScan() async {
    final bondedDevices = await _channel.invokeMethod('action_start_scan');
    for (var device in bondedDevices) {
      final d = BluetoothDeviceScan(device['name'], device['address'], paired: true);
      _pairedDevices.add(d);
      _controller.add(d);
    }
  }

  Future<void> close() async {
    await _scanStopped.close();
    await _controller.close();
  }

  Future<void> stopScan() => _channel.invokeMethod('action_stop_scan');

  void _newDevice(device) {
    _controller.add(BluetoothDeviceScan(
      device['name'],
      device['address'],
      nearby: true,
      paired: _pairedDevices.firstWhereOrNull((item) => item.address == device['address']) != null,
    ));
  }

  void _noDevice(device) {
    _controller.add(BluetoothDeviceScan(
      "_noDevice",
      "_noDevice",
      nearby: true,
      paired: false,
    ));
  }
}
