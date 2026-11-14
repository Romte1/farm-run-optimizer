package com.herbrunhelper;

/**
 * Everything the runs plant: herbs, trees, fruit trees and hardwood trees.
 *
 * Constant names match the crop names in patch-states.tsv. Growth stages follow RuneLite's
 * Time Tracking plugin (via Larry: Farming Runs): a crop finishes after (stages - 1) growth
 * ticks of its patch type's tick length. Gardener payments are from the OSRS Wiki and can be
 * handed over noted.
 */
public enum Crop
{
	// Herbs: 20-minute ticks
	GUAM(PatchType.HERB, "Guam", 9, "Guam seed", 5, null, 0, 249),
	MARRENTILL(PatchType.HERB, "Marrentill", 14, "Marrentill seed", 5, null, 0, 251),
	TARROMIN(PatchType.HERB, "Tarromin", 19, "Tarromin seed", 5, null, 0, 253),
	HARRALANDER(PatchType.HERB, "Harralander", 26, "Harralander seed", 5, null, 0, 255),
	RANARR(PatchType.HERB, "Ranarr", 32, "Ranarr seed", 5, null, 0, 257),
	TOADFLAX(PatchType.HERB, "Toadflax", 38, "Toadflax seed", 5, null, 0, 2998),
	IRIT(PatchType.HERB, "Irit", 44, "Irit seed", 5, null, 0, 259),
	AVANTOE(PatchType.HERB, "Avantoe", 50, "Avantoe seed", 5, null, 0, 261),
	KWUARM(PatchType.HERB, "Kwuarm", 56, "Kwuarm seed", 5, null, 0, 263),
	HUASCA(PatchType.HERB, "Huasca", 58, "Huasca seed", 5, null, 0, 30097),
	SNAPDRAGON(PatchType.HERB, "Snapdragon", 62, "Snapdragon seed", 5, null, 0, 3000),
	CADANTINE(PatchType.HERB, "Cadantine", 67, "Cadantine seed", 5, null, 0, 265),
	LANTADYME(PatchType.HERB, "Lantadyme", 73, "Lantadyme seed", 5, null, 0, 2481),
	DWARF_WEED(PatchType.HERB, "Dwarf weed", 79, "Dwarf weed seed", 5, null, 0, 267),
	TORSTOL(PatchType.HERB, "Torstol", 85, "Torstol seed", 5, null, 0, 269),

	// Trees: 40-minute ticks
	OAK(PatchType.TREE, "Oak", 15, "Oak sapling", 5, "Tomatoes(5)", 1, 1521),
	WILLOW(PatchType.TREE, "Willow", 30, "Willow sapling", 7, "Apples(5)", 1, 1519),
	MAPLE(PatchType.TREE, "Maple", 45, "Maple sapling", 9, "Oranges(5)", 1, 1517),
	YEW(PatchType.TREE, "Yew", 60, "Yew sapling", 11, "Cactus spine", 10, 1515),
	MAGIC(PatchType.TREE, "Magic", 75, "Magic sapling", 13, "Coconut", 25, 1513),

	// Fruit trees: 160-minute ticks (16 hours)
	APPLE(PatchType.FRUIT_TREE, "Apple", 27, "Apple sapling", 7, "Sweetcorn", 9, 1955),
	BANANA(PatchType.FRUIT_TREE, "Banana", 33, "Banana sapling", 7, "Apples(5)", 4, 1963),
	ORANGE(PatchType.FRUIT_TREE, "Orange", 39, "Orange sapling", 7, "Strawberries(5)", 3, 2108),
	CURRY(PatchType.FRUIT_TREE, "Curry", 42, "Curry sapling", 7, "Bananas(5)", 5, 5970),
	PINEAPPLE(PatchType.FRUIT_TREE, "Pineapple", 51, "Pineapple sapling", 7, "Watermelon", 10, 2114),
	PAPAYA(PatchType.FRUIT_TREE, "Papaya", 57, "Papaya sapling", 7, "Pineapple", 10, 5972),
	PALM(PatchType.FRUIT_TREE, "Palm", 68, "Palm sapling", 7, "Papaya fruit", 15, 5974),
	DRAGONFRUIT(PatchType.FRUIT_TREE, "Dragonfruit", 81, "Dragonfruit sapling", 7, "Coconut", 15, 22929),

	// Hardwood: 640-minute ticks
	TEAK(PatchType.HARDWOOD, "Teak", 35, "Teak sapling", 8, "Limpwurt root", 15, 6333),
	MAHOGANY(PatchType.HARDWOOD, "Mahogany", 55, "Mahogany sapling", 9, "Yanillian hops", 25, 6332);

	private final PatchType type;
	private final String displayName;
	private final int level;
	private final String seedName;
	private final int stages;
	private final String paymentItem;
	private final int paymentQuantity;
	/** Item id of the produce, used as the crop's picture (ids from RuneLite's ItemID via Larry). */
	private final int iconItemId;

	Crop(PatchType type, String displayName, int level, String seedName, int stages,
		String paymentItem, int paymentQuantity, int iconItemId)
	{
		this.iconItemId = iconItemId;
		this.type = type;
		this.displayName = displayName;
		this.level = level;
		this.seedName = seedName;
		this.stages = stages;
		this.paymentItem = paymentItem;
		this.paymentQuantity = paymentQuantity;
	}

	public PatchType getType()
	{
		return type;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int getLevel()
	{
		return level;
	}

	/** Herb seed or tree sapling name, as it appears in the bank. */
	public String getSeedName()
	{
		return seedName;
	}

	/** Growth stages including the finished one. */
	public int getStages()
	{
		return stages;
	}

	public int getGrowthMinutes()
	{
		return (stages - 1) * type.getTickMinutes();
	}

	public int getIconItemId()
	{
		return iconItemId;
	}

	public String getPaymentItem()
	{
		return paymentItem;
	}

	public int getPaymentQuantity()
	{
		return paymentQuantity;
	}

	/** Crop by its patch-states.tsv name, or null for weeds/crops the plugin doesn't plant. */
	public static Crop fromTableName(String name)
	{
		try
		{
			return valueOf(name);
		}
		catch (IllegalArgumentException e)
		{
			return null;
		}
	}

	/**
	 * The crop whose seed/sapling appears in a chat message like "You plant a Ranarr seed...".
	 * Picks the longest match, so "pineapple sapling" isn't read as "apple sapling".
	 */
	public static Crop fromPlantMessage(String message)
	{
		String lower = message.toLowerCase();
		if (!lower.contains("you plant"))
		{
			return null;
		}
		Crop best = null;
		for (Crop crop : values())
		{
			String seed = crop.seedName.toLowerCase();
			if (lower.contains(seed) && (best == null || seed.length() > best.seedName.length()))
			{
				best = crop;
			}
		}
		return best;
	}
}
