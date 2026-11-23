package com.herbrunhelper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.plugins.banktags.BankTag;

/**
 * The "Farm run" bank tag: the bank items this run needs. Opened through RuneLite's Bank Tags,
 * so the bank shows just these items and you withdraw them with normal clicks.
 */
final class RunBankTag implements BankTag
{
	static final String NAME = "farm run optimizer";

	private volatile Set<Integer> ids = Collections.emptySet();

	/** True if the items changed. */
	boolean set(Set<Integer> next)
	{
		if (ids.equals(next))
		{
			return false;
		}
		ids = Collections.unmodifiableSet(new HashSet<>(next));
		return true;
	}

	@Override
	public boolean contains(int itemId)
	{
		return ids.contains(itemId);
	}
}
