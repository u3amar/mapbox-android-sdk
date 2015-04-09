package com.mapbox.mapboxsdk.util;

import com.mapbox.mapboxsdk.geometry.LatLng;

/**
 * Created by nitrog42 on 09/04/15.
 */
public class MathUtils {
    public static double getDistance(LatLng cluster, LatLng marker) {
        return Math.pow(marker.getLatitude() - cluster.getLatitude(), 2) + Math.pow(marker.getLongitude() - cluster.getLongitude(), 2);
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
