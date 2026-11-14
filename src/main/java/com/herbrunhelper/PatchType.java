package com.herbrunhelper;

public enum PatchType
{
	HERB("Herbs", "HERB", 20),
	ALLOTMENT("Allotments", "ALLOTMENT", 10),
	TREE("Trees", "TREE", 40),
	FRUIT_TREE("Fruit trees", "FRUIT_TREE", 160),
	HARDWOOD("Hardwood", "HARDWOOD_TREE", 640);

	private final String displayName;
	/** Name used for this type in patch-states.tsv. */
	private final String tableName;
	/** Minutes between growth ticks. */
	private final int tickMinutes;

	PatchType(String displayName, String tableName, int tickMinutes)
	{
		this.displayName = displayName;
		this.tableName = tableName;
		this.tickMinutes = tickMinutes;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public String getTableName()
	{
		return tableName;
	}

	public int getTickMinutes()
	{
		return tickMinutes;
	}

	/** Trees of any kind need saplings, a gardener payment and clearing when grown. */
	public boolean isTreeLike()
	{
		return this == TREE || this == FRUIT_TREE || this == HARDWOOD;
	}

	/** Gardeners can be paid to protect these crops. */
	public boolean hasGardenerPayment()
	{
		return isTreeLike() || this == ALLOTMENT;
	}

	/** Seeds that stack in one inventory slot (not saplings). */
	public boolean seedsStack()
	{
		return this == HERB || this == ALLOTMENT;
	}
}
