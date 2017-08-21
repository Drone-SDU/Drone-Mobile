package ucsd.personal.litao.dji;

import android.support.annotation.NonNull;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.List;

import dji.common.battery.BatteryState;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.GPSSignalLevel;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.gimbal.GimbalState;
import dji.sdk.battery.Battery;
import dji.sdk.products.Aircraft;

import static java.lang.Thread.sleep;

public class Client {
    private static final String TAG = "Client";
    private static final String HOST_NAME = "172.20.10.4";
    private static final int PORT_NUM = 6666;

    private Aircraft mProduct;
    private boolean mStart = true;

    public Client(Aircraft product) {
        mProduct = product;

        final JSONObject jsonObject = new JSONObject();

        mProduct.getFlightController().setStateCallback(new FlightControllerState.Callback(){
            @Override
            public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                JSONObject GPSJson = new JSONObject();
                try {
                    if(flightControllerState.getGPSSignalLevel() != null) {
                        GPSSignalLevel gpsLevel = flightControllerState.getGPSSignalLevel();
                        GPSJson.put("gpsLevel", gpsLevel.toString());
                    }
                    if(flightControllerState.getAircraftLocation() != null) {
                        LocationCoordinate3D location = flightControllerState.getAircraftLocation();
                        GPSJson.put("longitude", String.valueOf(location.getLongitude()));
                        GPSJson.put("latitude", String.valueOf(location.getLatitude()));
                        GPSJson.put("altitude", String.valueOf(location.getAltitude()));
                    }
                    if(flightControllerState.getAttitude() != null) {
                        Attitude attitude = flightControllerState.getAttitude();
                        GPSJson.put("pitch", String.valueOf(attitude.pitch));
                        GPSJson.put("roll", String.valueOf(attitude.roll));
                        GPSJson.put("yaw", String.valueOf(attitude.yaw));
                    }

                    GPSJson.put("velocityX", String.valueOf(flightControllerState.getVelocityX()));
                    GPSJson.put("velocityY", String.valueOf(flightControllerState.getVelocityY()));
                    GPSJson.put("velocityZ", String.valueOf(flightControllerState.getVelocityZ()));

                    // Update the values in GPS key
                    jsonObject.put("GPS", GPSJson);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        List<Battery> batteries = mProduct.getBatteries();
        for(final Battery battery : batteries) {
            battery.setStateCallback(new BatteryState.Callback() {
                @Override
                public void onUpdate(BatteryState batteryState) {
                    JSONObject batteryJson = new JSONObject();
                    try {
                        batteryJson.put("BatteryEnergyRemainingPercent", batteryState.getChargeRemainingInPercent());
                        batteryJson.put("Voltage", batteryState.getVoltage());
                        batteryJson.put("Current", batteryState.getCurrent());

                        // Update the values in Battery key
                        jsonObject.put("Battery" + battery.getIndex(), batteryJson);
                    }
                    catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        mProduct.getGimbal().setStateCallback(new GimbalState.Callback() {
            @Override
            public void onUpdate(@NonNull GimbalState gimbalState) {
                JSONObject gimbalJson = new JSONObject();
                try {
                    if(gimbalState.getAttitudeInDegrees() != null) {
                        dji.common.gimbal.Attitude attitude = gimbalState.getAttitudeInDegrees();
                        gimbalJson.put("pitch", String.valueOf(attitude.getPitch()));
                        gimbalJson.put("roll", String.valueOf(attitude.getRoll()));
                        gimbalJson.put("yaw", String.valueOf(attitude.getYaw()));
                    }

                    // Update the values in Gimbal key
                    jsonObject.put("Gimbal", gimbalJson);
                }
                catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Set up client socket
                    Socket socket = new Socket(HOST_NAME, PORT_NUM);

                    // Input and Output Streams
                    final DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    while(mStart) {
                        try {
                            // Send the JsonObject every 2s
                            Log.i(TAG, jsonObject.toString());
                            out.writeUTF(jsonObject.toString());

                            sleep(2000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Error Handling
                catch (UnknownHostException e) {
                    Log.e(TAG, "Don't know about host " + HOST_NAME);
                }
                catch (IOException e) {
                    Log.e(TAG, "Couldn't get I/O for the connection to " + HOST_NAME);
                    Log.e(TAG, "Maybe the Server is not online");
                    e.printStackTrace();
                }
            }
        }).start();
    }

    void stop() {
        mStart = false;
    }
}
