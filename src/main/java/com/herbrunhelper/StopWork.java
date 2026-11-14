package com.herbrunhelper;

import java.util.ArrayList;
import java.util.List;

/** The to-do list for one stop, from what the plugin knows about the patch. Pure Java. */
public final class StopWork
{
	private StopWork()
	{
	}

	public static List<String> describe(RunPlan.RouteStep step, PatchTimers.Planting planting,
		PatchTimers.Status status, long now)
	{
		List<String> out = new ArrayList<>();
		PatchType type = step.patch.getType();
		boolean ready = planting != null && planting.isReady(now);
		String seed = step.crop == null ? null : step.crop.getSeedName().toLowerCase();

		if (!type.isTreeLike())
		{
			if (ready)
			{
				out.add("Harvest the " + planting.crop.getDisplayName().toLowerCase() + " (note them at the leprechaun)");
			}
			else if (status == PatchTimers.Status.DISEASED)
			{
				out.add("Diseased: cure with plant cure, or dig it up");
			}
			else if (status == PatchTimers.Status.DEAD)
			{
				out.add("Dead: dig it up with your spade");
			}
			else if (status == PatchTimers.Status.WEEDS)
			{
				out.add("Rake the weeds");
			}
			else if (planting == null && status == null)
			{
				out.add("Harvest if anything's grown");
			}
		}
		else
		{
			String tree = planting != null ? planting.crop.getDisplayName().toLowerCase() + " tree" : "tree";
			if (ready)
			{
				out.add("Check the " + tree + "'s health");
				if (type == PatchType.FRUIT_TREE)
				{
					out.add("Pick the fruit if you want it (note at the leprechaun)");
				}
				if (seed != null)
				{
					out.add("Pay the gardener " + RunPlanner.TREE_REMOVAL_COINS + " coins to chop it down");
				}
				else
				{
					out.add("Leave it standing: no sapling to replant (it won't die)");
				}
			}
			else if (status == PatchTimers.Status.STUMP)
			{
				out.add("Dig up the stump with your spade");
			}
			else if (status == PatchTimers.Status.DISEASED)
			{
				out.add("Diseased: prune it with secateurs");
			}
			else if (status == PatchTimers.Status.DEAD)
			{
				out.add("Dead: dig it up with your spade");
			}
			else if (status == PatchTimers.Status.WEEDS)
			{
				out.add("Rake the weeds");
			}
			else if (planting == null && status == null)
			{
				out.add(seed != null ? "If a grown tree is there: check health, pay "
					+ RunPlanner.TREE_REMOVAL_COINS + " coins to clear it" : "If a grown tree is there: check its health");
			}
		}

		if (seed != null)
		{
			if (step.crop.getSeedsPerPatch() > 1)
			{
				out.add("Plant " + step.crop.getSeedsPerPatch() + " " + seed + "s");
			}
			else
			{
				String article = type != PatchType.HERB ? "the " : "aeiou".indexOf(seed.charAt(0)) >= 0 ? "an " : "a ";
				out.add("Plant " + article + seed);
			}
		}
		else if (!type.isTreeLike())
		{
			out.add("No seed left to plant here");
		}
		if (step.compost != null && seed != null)
		{
			out.add(step.compost.startsWith("Bottomless") ? "Use your bottomless compost bucket" : "Add " + step.compost.toLowerCase());
		}
		if (step.paymentItem != null && seed != null)
		{
			out.add("Pay the gardener " + step.paymentQuantity + " x " + step.paymentItem + " to protect it");
		}
		return out;
	}
}
