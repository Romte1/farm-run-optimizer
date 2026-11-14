package com.herbrunhelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Run this main() to launch RuneLite with the plugin loaded (developer mode). */
public class HerbRunPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HerbRunPlugin.class);
		RuneLite.main(args);
	}
}
