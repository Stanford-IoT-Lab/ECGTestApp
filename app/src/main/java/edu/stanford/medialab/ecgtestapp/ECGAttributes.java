package edu.stanford.medialab.ecgtestapp;

import java.util.HashMap;
import java.util.UUID;

/**
 * Created by prpl on 9/1/15.
 */

public class ECGAttributes {

    public static final String SERVICE_UUID = "00001820-0000-1000-8000-00805F9B34FB";
    public static final String CHARACTERISTIC_UUID = "00002A80-0000-1000-8000-00805F9B34FB";

    private static HashMap<String, String> attributes = new HashMap<String, String>();
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";


    static {
        attributes.put(SERVICE_UUID, "Tethys Service");
        attributes.put(CHARACTERISTIC_UUID, "Characteristic");
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name String");

    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}