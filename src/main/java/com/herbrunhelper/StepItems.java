package com.herbrunhelper;

/**
 * Which inventory item the stop you're at needs next, so it can be highlighted.
 * Order at a patch: rake weeds / dig up dead or stump / coins for the gardener to clear a
 * grown tree, then the seed or sapling, then compost, then the gardener's protection payment.
 * Pure Java so it can be tested.
 */
public final class StepItems
{
	/** What's happened at one stop since you arrived. */
	public static final class Progress
	{
		public final long plantedAt;
		public boolean composted;
		public boolean paid;
		/** How many of the compost / payment you carried when you planted (to spot them being used). */
		public final int compostBaseline;
		public final int paymentBaseline;

		public Progress(long plantedAt, int compostBaseline, int paymentBaseline)
		{
			this.plantedAt = plantedAt;
			this.compostBaseline = compostBaseline;
			this.paymentBaseline = paymentBaseline;
		}
	}

	/** The item to highlight and a short label for it. */
	public static final class Next
	{
		public final String item;
		public final String action;

		Next(String item, String action)
		{
			this.item = item;
			this.action = action;
		}
	}

	/** How long after planting we keep pointing at compost/payment for that stop. */
	public static final long PROGRESS_WINDOW_MILLIS = 15 * 60_000L;

	private StepItems()
	{
	}

	/** Null when nothing in the inventory is needed right now (e.g. harvesting, or the stop is finished). */
	public static Next next(RunPlan.RouteStep step, PatchTimers.Planting planting, PatchTimers.Status status,
		Progress progress, long now)
	{
		return next(step, planting, status, progress, now, false);
	}

	/**
	 * @param checkHealthDue the game says this grown tree's health hasn't been checked yet: do
	 *     that first (it's where the XP is), then pay the gardener to clear it
	 */
	public static Next next(RunPlan.RouteStep step, PatchTimers.Planting planting, PatchTimers.Status status,
		Progress progress, long now, boolean checkHealthDue)
	{
		PatchType type = step.patch.getType();
		boolean planted = progress != null && now - progress.plantedAt < PROGRESS_WINDOW_MILLIS;

		if (planted)
		{
			if (step.compost != null && !progress.composted)
			{
				return new Next(step.compost, "Add compost");
			}
			if (type.hasGardenerPayment() && step.paymentItem != null && !progress.paid)
			{
				return new Next(step.paymentItem, "Pay the gardener");
			}
			return null;
		}

		if (status == PatchTimers.Status.WEEDS)
		{
			return new Next("Rake", "Rake the weeds");
		}
		if (status == PatchTimers.Status.DEAD || status == PatchTimers.Status.STUMP)
		{
			return new Next("Spade", "Dig it up");
		}
		if (checkHealthDue && type.isTreeLike())
		{
			return new Next(null, "Check the " + StopWork.plantName(type, planting != null ? planting.crop : null) + "'s health");
		}
		boolean ready = planting != null && planting.isReady(now);
		if (ready && !type.isTreeLike())
		{
			return null; // harvest by clicking the patch
		}
		if (ready && type.isTreeLike())
		{
			// Nothing to replant: just check health, don't clear it.
			if (step.crop == null)
			{
				return null;
			}
			return type.getClear() == PatchType.Clear.GARDENER ? new Next("Coins", "Pay the gardener to clear it")
				: new Next("Spade", "Dig it up");
		}
		if (step.crop != null)
		{
			return new Next(step.crop.getSeedName(), "Plant");
		}
		return null;
	}
}
