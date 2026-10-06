package com.almalence.util;

import java.util.HashMap;
import java.util.Map;

import android.util.Log;

/**
 * Loads native libraries without crashing when one is missing.
 *
 * The 64-bit (arm64-v8a) build only ships the open-source libraries; the
 * Almalence processing core exists only as a 32-bit prebuilt. Modes that need
 * it are hidden when {@link #isAlmalibAvailable()} returns false.
 */
public final class NativeLibs
{
	private static final String					TAG		= "NativeLibs";
	private static final Map<String, Boolean>	loaded	= new HashMap<String, Boolean>();

	private NativeLibs()
	{
	}

	public static synchronized boolean load(String name)
	{
		Boolean result = loaded.get(name);
		if (result != null)
			return result;

		try
		{
			System.loadLibrary(name);
			result = true;
		} catch (UnsatisfiedLinkError e)
		{
			Log.w(TAG, "Native library not available: " + name);
			result = false;
		}
		loaded.put(name, result);
		return result;
	}

	public static boolean isAlmalibAvailable()
	{
		return load("almalib");
	}
}
