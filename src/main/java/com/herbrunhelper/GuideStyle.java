package com.herbrunhelper;

/** How the guide line is drawn. */
public enum GuideStyle
{
	LINE("Line"),
	TILES("Tiles"),
	BOTH("Line and tiles");

	private final String label;

	GuideStyle(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
