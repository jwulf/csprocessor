package com.redhat.contentspec.utils;

import java.security.MessageDigest;

import org.jboss.resteasy.util.Hex;

public class HashUtilities {
	
	/**
	 * Generates a MD5 Hash for a specific string
	 * 
	 * @param input The string to be converted into an MD5 hash.
	 * @return The MD5 Hash string of the input string.
	 */
	public static String generateMD5(String input) {
		try {
			MessageDigest messageDigest = MessageDigest.getInstance("MD5");
			messageDigest.reset();
			byte[] digest = messageDigest.digest(input.getBytes());
			return new String(Hex.encodeHex(digest));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
}
