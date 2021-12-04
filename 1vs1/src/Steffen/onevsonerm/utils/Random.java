package com.sero583.onevsonerm.utils;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serhat G. (sero583)
 */
public final class Random {
    private static final java.util.Random instance = new java.util.Random();

    public static void main(String[] args) {
        System.out.println("Testing randomizer, testing 100 times generation between 1-100...");

        for(int i = 0; i < 100; i++) {
            System.out.println("Result #" + (i+1) + ": " + getRandomNumber(1, 100));
        }

        System.out.println("Test done.");

        System.out.println("Testing random pick...");

        Map<String, Object> points = new HashMap<>();
        points.put("Map1", 1);
        points.put("Map2", 1);
        points.put("Map3", 1);
        points.put("Map4", 1);
        points.put("Map5", 1);

        String[] maps = points.keySet().toArray(new String[] {});

        for(int i = 0; i < 50; i++) {
            System.out.println("Generated for #" + i + ": " + maps[getRandomNumber(0, maps.length-1)]);
        }
        System.out.println("Test done.");
    }

    private Random() {} // disallow creation of instances

    public static int getRandomNumber(int from, int to) {
        return instance.nextInt(to - from + 1) + from;
    }

    public static final String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public static final String numbers = "0123456789";
    public static final String mixed = abc + numbers;

    public static char getRandomCharacter() {
        return getRandomCharacter(true);
    }

    public static char getRandomCharacter(boolean mix) {
        return mix == true ? pickRandomChar(mixed) : pickRandomChar(abc);
    }

    public static String randomString(int length) {
        return randomString(length, true);
    }

    public static String randomString(int length, boolean mix) {
        StringBuilder builder = new StringBuilder(length);

        for(int i = 0; i < length; i++) {
            builder.append(pickRandomChar(mix == true ? mixed : abc));
        }

        return builder.toString();
    }

    public static char pickRandomChar(String str) {
        int len = str.length()-1;
        return str.charAt(getRandomNumber(0, len));
    }
}
