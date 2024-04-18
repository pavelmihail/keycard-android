package ism.ase.ro.keycardlocal.util.android;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;

import ism.ase.ro.keycardlocal.util.globalplatform.Crypto;
import ism.ase.ro.keycardlocal.util.io.CardListener;

import java.util.UUID;

public class LedgerBLEManager {
  private static final int REQUEST_ENABLE_BT = 1;

  final private BluetoothAdapter bluetoothAdapter;
  final private Activity activity;
  private CardListener cardListener;

  static {
    Crypto.addBouncyCastleProvider();
  }

  public LedgerBLEManager(Activity context) {
    this.activity = context;
    final BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
    this.bluetoothAdapter = bluetoothManager.getAdapter();
  }

  public void ensureBLEEnabled(Context context) {
    if (!bluetoothAdapter.isEnabled()) {
      Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        // TODO: Consider calling
        //    ActivityCompat#requestPermissions
        // here to request the missing permissions, and then overriding
        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
        //                                          int[] grantResults)
        // to handle the case where the user grants the permission. See the documentation
        // for ActivityCompat#requestPermissions for more details.
        return;
      }
      activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }
  }

  public void startScan(BluetoothAdapter.LeScanCallback cb) {
    if (ActivityCompat.checkSelfPermission(this.activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return;
    }
    bluetoothAdapter.startLeScan(new UUID[]{LedgerBLEChannel.LEDGER_UUID}, cb);
  }

  public void stopScan(BluetoothAdapter.LeScanCallback cb) {
    if (ActivityCompat.checkSelfPermission(this.activity, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return;
    }
    bluetoothAdapter.stopLeScan(cb);
  }

  public void connectDevice(BluetoothDevice device) {
    if (ActivityCompat.checkSelfPermission(this.activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
      // TODO: Consider calling
      //    ActivityCompat#requestPermissions
      // here to request the missing permissions, and then overriding
      //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
      //                                          int[] grantResults)
      // to handle the case where the user grants the permission. See the documentation
      // for ActivityCompat#requestPermissions for more details.
      return;
    }
    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
      final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
      activity.registerReceiver(new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          final BluetoothDevice d = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
          final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

          if (!d.getAddress().equals(device.getAddress())) {
            return;
          }

          if (bondState == BluetoothDevice.BOND_BONDED) {
            activity.unregisterReceiver(this);
            // connect/disconnect to make bond permanent
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
              // TODO: Consider calling
              //    ActivityCompat#requestPermissions
              // here to request the missing permissions, and then overriding
              //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
              //                                          int[] grantResults)
              // to handle the case where the user grants the permission. See the documentation
              // for ActivityCompat#requestPermissions for more details.
              return;
            }
            device.connectGatt(activity, false, new BluetoothGattCallback() {
              @Override
              public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                  if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                  }
                  gatt.disconnect();
                  onConnected(device);
                }
              }
            });
          }
        }
      }, filter);

      device.createBond();
    } else {
      onConnected(device);
    }
  }

  private void onConnected(BluetoothDevice device) {
    if (cardListener != null) {
      new LedgerBLEChannel(activity, device, cardListener);
    }
  }

  public void setCardListener(CardListener cardListener) {
    this.cardListener = cardListener;
  }
}
