package com.herbrunhelper;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.TileObject;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Farm Run Optimizer",
	description = "Plans herb, tree, fruit tree and hardwood farm runs: what to bring, the fastest route, what to do at each patch, and every growth timer",
	tags = {"farming", "farm run", "herb run", "tree run", "fruit tree", "hardwood", "herbs", "patches", "timers", "fairy ring", "route", "planner"}
)
public class HerbRunPlugin extends Plugin
{
	// Raw container IDs (stable in the game cache): inventory, worn equipment, bank
	private static final int INVENTORY = 93;
	private static final int EQUIPMENT = 94;
	private static final int BANK = 95;

	private static final int QUEST_CHECK_INTERVAL_TICKS = 100;
	private static final int PATCH_SYNC_INTERVAL_TICKS = 2;
	/** Hint arrows only render inside the loaded area. */
	private static final int HINT_ARROW_RANGE = 50;
	private static final String TIMERS_KEY = "timers";
	private static final String CLOCK_KEY = "growthClock";
	private static final String TIME_TRACKING_GROUP = "timetracking";
	/** Standard spellbook = 0. */
	private static final int SPELLBOOK_VARBIT = 4070;
	private static final String[] TELEPORT_QUESTS = {"Plague City", "Eadgar's Ruse", "Client of Kourend", "Twilight's Promise"};
	private static final String FAIRYTALE_II = "Fairytale II - Cure a Queen";

	@Inject
	private Client client;

	@Inject
	private HerbRunConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private Notifier notifier;

	@Inject
	private HerbRunBankOverlay bankOverlay;

	@Inject
	private PatchNavigationOverlay navigationOverlay;

	@Inject
	private HerbRunInventoryOverlay inventoryOverlay;

	@Inject
	private RunReadyOverlay readyOverlay;

	@Inject
	private LeadHintOverlay leadHintOverlay;

	@Inject
	private SpellHighlightOverlay spellHighlightOverlay;

	@Inject
	private MinimapArrowOverlay minimapArrowOverlay;

	@Inject
	private net.runelite.client.game.SpriteManager spriteManager;

	private static final String HISTORY_KEY = "runHistory";
	private static final String CURRENT_RUN_KEY = "currentRun";
	private final RunHistory history = new RunHistory();
	private volatile FarmingContract contract;
	private final Map<FarmPatch, PatchTimers.Status> lastStatus = new EnumMap<>(FarmPatch.class);
	private boolean statusesPrimed;
	private int lastBankTick = -10;
	private int lastFarmingXp;
	private final Map<Crop, Integer> herbProfit = new EnumMap<>(Crop.class);
	private volatile List<RunReadyOverlay.Line> readyLines = Collections.emptyList();

	private HerbRunPanel panel;
	private NavigationButton navButton;

	// name -> qty snapshots and name -> item id, refreshed whenever a container changes
	private final Map<String, Integer> bankItems = new HashMap<>();
	private final Map<String, Integer> inventoryItems = new HashMap<>();
	private final Map<String, Integer> equipmentItems = new HashMap<>();
	private final Map<String, Integer> bankNameToId = new HashMap<>();
	private final Map<String, Integer> inventoryNameToId = new HashMap<>();
	private final Map<String, Integer> equipmentNameToId = new HashMap<>();
	private boolean bankSeen;

	private final Set<String> completedQuests = new HashSet<>();
	private boolean fairyRingsUnlocked;
	private int ticksSinceQuestCheck = QUEST_CHECK_INTERVAL_TICKS;
	private int ticksSincePatchSync;
	private boolean dirty = true;
	private boolean timersLoaded;

	private final PatchTimers timers = new PatchTimers();
	private final GrowthClock clock = new GrowthClock();
	private final Map<FarmPatch, Integer> lastSeenValue = new EnumMap<>(FarmPatch.class);
	private final Set<FarmPatch> notifiedReady = EnumSet.noneOf(FarmPatch.class);

	/** Farming patch objects in the loaded area, with the varbit each one shows. */
	private final Map<GameObject, Integer> patchObjects = new HashMap<>();
	/** Exact patch tiles learned from the game (saved, so the arrow/path are exact next time). */
	private final Map<FarmPatch, WorldPoint> learnedTiles = new EnumMap<>(FarmPatch.class);
	private static final String TILES_KEY = "patchTiles";
	/** The game's farming patch varbits (FARMING_TRANSMIT_A..N). */
	private static final Set<Integer> FARMING_VARBITS = new HashSet<>(java.util.Arrays.asList(
		4771, 4772, 4773, 4774, 4775, 7904, 7905, 7906, 7907, 7908, 7909, 7910, 7911, 7912));
	private volatile Set<Integer> highlightIds = Collections.emptySet();
	private volatile FarmPatch nextPatch;
	private WorldPoint shortestPathTarget;
	private WorldPoint lastPlayerTile;
	/** Where to go right now: the patch, or the fairy ring to use first. */
	private volatile WorldPoint navTarget;
	private volatile String navRingLabel;
	/** Gardeners (NPCs with a "Pay" option) currently loaded. */
	private final Set<NPC> gardeners = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile FarmPatch payPatch;
	private volatile String payLabel;
	/** Fairy ring objects currently loaded, to outline the real ring. */
	private final Set<TileObject> fairyRingObjects = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final Set<TileObject> spiritTreeObjects = java.util.concurrent.ConcurrentHashMap.newKeySet();
	/** What to click first (fairy ring or spirit tree) and what to pick there. */
	private volatile TileObject navLeadObject;
	private volatile String navLeadHint;
	/** A walkable tile beside each tree patch (where the gardener stands), for the walking path. */
	private final Map<FarmPatch, WorldPoint> approachTiles = new EnumMap<>(FarmPatch.class);
	private static final String APPROACH_KEY = "gardenerTiles";
	private boolean ourHintArrow;
	private RunPlan lastPlan;

	/** What's happened at each stop since you planted there (for the step highlight). */
	private final Map<FarmPatch, StepItems.Progress> progress = new EnumMap<>(FarmPatch.class);
	private volatile String stepItem;
	private FarmPatch stepPatch;
	private String stepAction;

	@Provides
	HerbRunConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HerbRunConfig.class);
	}

	@Override
	protected void startUp()
	{
		loadWorldMap();
		panel = new HerbRunPanel(itemManager, timers, this::resetTimers, this::setRun,
			this::toggleTeleport, this::setUseSpells, this::setUseTabs, this::toggleCrop, this::togglePatch,
			this::setSetting);
		panel.setOnClearHistory(this::clearHistory);
		panel.setSpriteManager(spriteManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Farm Run Optimizer")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(bankOverlay);
		overlayManager.add(navigationOverlay);
		overlayManager.add(inventoryOverlay);
		overlayManager.add(readyOverlay);
		overlayManager.add(leadHintOverlay);
		overlayManager.add(spellHighlightOverlay);
		overlayManager.add(minimapArrowOverlay);
		dirty = true;
		timersLoaded = false;
		ticksSinceQuestCheck = QUEST_CHECK_INTERVAL_TICKS;
		loadTiles();
		loadTileMap(APPROACH_KEY, approachTiles);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(bankOverlay);
		overlayManager.remove(navigationOverlay);
		overlayManager.remove(inventoryOverlay);
		overlayManager.remove(readyOverlay);
		overlayManager.remove(leadHintOverlay);
		overlayManager.remove(spellHighlightOverlay);
		overlayManager.remove(minimapArrowOverlay);
		saveHistory(); // keeps the run in progress for next time
		lastStatus.clear();
		statusesPrimed = false;
		lastFarmingXp = 0;
		contract = null;
		progress.clear();
		stepItem = null;
		panel.stop();
		clearNavigation();
		bankItems.clear();
		inventoryItems.clear();
		equipmentItems.clear();
		bankNameToId.clear();
		inventoryNameToId.clear();
		equipmentNameToId.clear();
		patchObjects.clear();
		gardeners.clear();
		fairyRingObjects.clear();
		spiritTreeObjects.clear();
		lastSeenValue.clear();
		bankSeen = false;
		highlightIds = Collections.emptySet();
		nextPatch = null;
	}

	// ================================================================ events

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				ticksSinceQuestCheck = QUEST_CHECK_INTERVAL_TICKS;
				dirty = true;
				break;
			case LOADING:
				patchObjects.clear();
				lastSeenValue.clear();
				break;
			case LOGIN_SCREEN:
				// Another account may log in next.
				bankItems.clear();
				bankNameToId.clear();
				bankSeen = false;
				timersLoaded = false;
				notifiedReady.clear();
				clearNavigation();
				break;
			default:
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() == Skill.FARMING || event.getSkill() == Skill.MAGIC)
		{
			dirty = true;
		}
		if (event.getSkill() == Skill.FARMING)
		{
			int xp = event.getXp();
			// Count Farming XP gained at a patch towards the run (not Tithe Farm, contracts, etc.).
			if (lastFarmingXp > 0 && xp > lastFarmingXp && timersLoaded && patchNearPlayer() != null)
			{
				history.gainedXp(xp - lastFarmingXp, System.currentTimeMillis());
				saveHistory();
			}
			lastFarmingXp = xp;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == BANK)
		{
			snapshot(event.getItemContainer(), bankItems, bankNameToId);
			lastBankTick = client.getTickCount();
			bankSeen = true;
			dirty = true;
		}
		else if (id == INVENTORY)
		{
			Map<String, Integer> before = new HashMap<>(inventoryItems);
			snapshot(event.getItemContainer(), inventoryItems, inventoryNameToId);
			noticeHarvest(before);
			noticeItemsUsed();
			dirty = true;
		}
		else if (id == EQUIPMENT)
		{
			snapshot(event.getItemContainer(), equipmentItems, equipmentNameToId);
			dirty = true;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String lower = event.getMessage().toLowerCase();
		if (lower.contains("you treat the"))
		{
			// "You treat the herb patch with ultracompost."
			for (StepItems.Progress p : progress.values())
			{
				p.composted = true;
			}
			return;
		}
		Crop crop = Crop.fromPlantMessage(event.getMessage());
		Player player = client.getLocalPlayer();
		if (crop == null || player == null)
		{
			return;
		}
		WorldPoint where = player.getWorldLocation();
		FarmPatch patch = patchAt(where, crop.getType());
		if (patch == null)
		{
			log.debug("Planted {} but no known patch at {}", crop, where);
			return;
		}
		timers.planted(patch, crop, System.currentTimeMillis());
		history.planted(patch, System.currentTimeMillis());
		saveHistory();
		RunPlan.RouteStep step = stepFor(patch);
		progress.clear();
		progress.put(patch, new StepItems.Progress(System.currentTimeMillis(),
			step == null || step.compost == null ? 0 : inventoryItems.getOrDefault(step.compost, 0),
			step == null || step.paymentItem == null ? 0 : inventoryItems.getOrDefault(step.paymentItem, 0)));
		notifiedReady.remove(patch);
		saveTimers();
		dirty = true;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (Gardeners.ALL.contains(npc.getId()) || hasPayOption(npc))
		{
			gardeners.add(npc);
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		gardeners.remove(event.getNpc());
	}

	private static boolean hasPayOption(NPC npc)
	{
		NPCComposition comp = npc.getComposition();
		if (comp == null || comp.getActions() == null)
		{
			return false;
		}
		for (String a : comp.getActions())
		{
			if (a != null && a.startsWith("Pay"))
			{
				return true;
			}
		}
		return false;
	}

	/** The gardener to pay right now (closest one with a Pay option to the patch), or null. */
	NPC getGardenerToPay()
	{
		FarmPatch patch = payPatch;
		if (patch == null)
		{
			return null;
		}
		WorldPoint t = tileFor(patch);
		int wanted = Gardeners.idFor(patch);
		for (NPC npc : gardeners)
		{
			if (npc.getId() == wanted)
			{
				return npc; // this patch's own gardener
			}
		}
		NPC best = null;
		int bestDist = 20;
		for (NPC npc : gardeners)
		{
			WorldPoint w = npc.getWorldLocation();
			if (w == null || w.getPlane() != t.getPlane())
			{
				continue;
			}
			int d = dist(w, t.getX(), t.getY());
			if (d <= bestDist)
			{
				bestDist = d;
				best = npc;
			}
		}
		return best;
	}

	String getPayLabel()
	{
		return payLabel;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject obj = event.getGameObject();
		ObjectComposition def = client.getObjectDefinition(obj.getId());
		if (noteTravelObject(obj, def))
		{
			return;
		}
		if (def == null || !FARMING_VARBITS.contains(def.getVarbitId()))
		{
			return;
		}
		int varbit = def.getVarbitId();
		patchObjects.put(obj, varbit);
		WorldPoint loc = obj.getWorldLocation();
		FarmPatch patch = patchFor(loc, varbit);
		if (patch != null && !loc.equals(learnedTiles.get(patch)))
		{
			learnedTiles.put(patch, loc);
			saveTiles();
		}
	}

	/** The patch an object belongs to: same varbit and one of the patch's regions. */
	private static FarmPatch patchFor(WorldPoint loc, int varbit)
	{
		int region = FarmPatch.regionOf(loc.getX(), loc.getY());
		FarmPatch best = null;
		int bestDist = Integer.MAX_VALUE;
		for (FarmPatch p : FarmPatch.values())
		{
			if (p.getVarbit() != varbit || !p.inRegion(region))
			{
				continue;
			}
			int d = Math.max(Math.abs(p.getX() - loc.getX()), Math.abs(p.getY() - loc.getY()));
			if (d < bestDist)
			{
				best = p;
				bestDist = d;
			}
		}
		return best;
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		patchObjects.remove(event.getGameObject());
		fairyRingObjects.remove(event.getGameObject());
		spiritTreeObjects.remove(event.getGameObject());
	}

	// Fairy rings are flat on the ground, so the game sends them as ground objects.
	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		GroundObject obj = event.getGroundObject();
		noteTravelObject(obj, client.getObjectDefinition(obj.getId()));
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		fairyRingObjects.remove(event.getGroundObject());
		spiritTreeObjects.remove(event.getGroundObject());
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		DecorativeObject obj = event.getDecorativeObject();
		noteTravelObject(obj, client.getObjectDefinition(obj.getId()));
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		fairyRingObjects.remove(event.getDecorativeObject());
		spiritTreeObjects.remove(event.getDecorativeObject());
	}

	/** Remembers fairy rings and spirit trees (by name, following "impostor" objects). */
	private boolean noteTravelObject(TileObject obj, ObjectComposition def)
	{
		if (def == null)
		{
			return false;
		}
		String name = def.getName();
		if (def.getImpostorIds() != null)
		{
			ObjectComposition real = def.getImpostor();
			if (real != null)
			{
				name = real.getName();
			}
		}
		if (name == null)
		{
			return false;
		}
		if (name.toLowerCase().startsWith("fairy ring"))
		{
			// Only real rings, at a known ring spot (some scenery shares the name).
			if (FairyRings.isRingAt(obj.getWorldLocation().getX(), obj.getWorldLocation().getY()))
			{
				fairyRingObjects.add(obj);
			}
			return true;
		}
		if (name.equalsIgnoreCase("Spirit tree"))
		{
			spiritTreeObjects.add(obj);
			return true;
		}
		return false;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!HerbRunConfig.GROUP.equals(event.getGroup()) || TIMERS_KEY.equals(event.getKey())
			|| CLOCK_KEY.equals(event.getKey()) || TILES_KEY.equals(event.getKey()) || APPROACH_KEY.equals(event.getKey()))
		{
			return;
		}
		dirty = true;
		if (!config.useShortestPath())
		{
			clearShortestPath();
		}
		if ("pathMinigameTeleports".equals(event.getKey()))
		{
			shortestPathTarget = null; // send the route again with the new setting
		}
		if (!config.hintArrow())
		{
			clearHintArrow();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (!timersLoaded)
		{
			loadTimers();
		}
		if (++ticksSinceQuestCheck >= QUEST_CHECK_INTERVAL_TICKS)
		{
			ticksSinceQuestCheck = 0;
			refreshRequirements();
			if (timersLoaded)
			{
				importTimeTracking();
			}
		}
		if (timersLoaded && ++ticksSincePatchSync >= PATCH_SYNC_INTERVAL_TICKS)
		{
			ticksSincePatchSync = 0;
			syncNearbyPatches();
		}
		checkReadyNotifications();
		trackReadyPatches();
		if (timersLoaded)
		{
			checkDiseaseNotifications();
			readContract();
			if (history.tick(System.currentTimeMillis()))
			{
				saveHistory();
				dirty = true;
			}
			updateReadyLines();
		}
		if (dirty)
		{
			dirty = false;
			recompute();
		}
		updateNavigation();
		updateStepItem();
	}

	// ================================================================ harvest log, disease, contract, overlay

	/** Counts produce that lands in your inventory while you're standing at a patch. */
	private void noticeHarvest(Map<String, Integer> before)
	{
		if (!timersLoaded || client.getTickCount() - lastBankTick <= 1)
		{
			return; // bank withdrawals aren't harvests
		}
		FarmPatch patch = patchNearPlayer();
		if (patch == null)
		{
			return;
		}
		Set<Integer> produceIds = new HashSet<>();
		for (Crop c : Crop.values())
		{
			if (c.getType() == patch.getType())
			{
				produceIds.add(HerbPrices.produceId(c));
			}
		}
		long now = System.currentTimeMillis();
		boolean changed = false;
		for (Map.Entry<String, Integer> e : inventoryItems.entrySet())
		{
			int gained = e.getValue() - before.getOrDefault(e.getKey(), 0);
			Integer id = inventoryNameToId.get(e.getKey());
			if (gained <= 0 || id == null)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(id);
			if (produceIds.contains(canonical))
			{
				history.harvested(patch, e.getKey(), gained, itemManager.getItemPrice(canonical),
					patch.getType() == PatchType.HERB, seedPriceForProduce(canonical), now);
				changed = true;
			}
		}
		if (changed)
		{
			saveHistory();
			dirty = true;
		}
	}

	/** GE price of the seed that grew this produce (herb seeds; saplings if you hold one). */
	private long seedPriceForProduce(int produceId)
	{
		for (Crop c : Crop.values())
		{
			if (HerbPrices.produceId(c) == produceId)
			{
				int seedId = HerbPrices.seedId(c) > 0 ? HerbPrices.seedId(c)
					: inventoryNameToId.getOrDefault(c.getSeedName(), bankNameToId.getOrDefault(c.getSeedName(), -1));
				return seedId > 0 ? itemManager.getItemPrice(itemManager.canonicalize(seedId)) : 0;
			}
		}
		return 0;
	}

	/**
	 * The patch of this type you're standing at: the closest by its real (loaded) position, so the
	 * two allotments side by side are told apart.
	 */
	private FarmPatch patchAt(WorldPoint where, PatchType type)
	{
		int region = FarmPatch.regionOf(where.getX(), where.getY());
		FarmPatch best = null;
		int bestDist = Integer.MAX_VALUE;
		for (FarmPatch p : FarmPatch.values())
		{
			if (p.getType() != type || !p.inRegion(region))
			{
				continue;
			}
			WorldPoint t = tileFor(p);
			int d = dist(where, t.getX(), t.getY());
			if (d < bestDist)
			{
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	/** The closest patch within a few tiles of you, or null. */
	private FarmPatch patchNearPlayer()
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return null;
		}
		WorldPoint me = player.getWorldLocation();
		int region = FarmPatch.regionOf(me.getX(), me.getY());
		FarmPatch best = null;
		int bestDist = 12;
		for (FarmPatch p : FarmPatch.values())
		{
			if (!p.inRegion(region))
			{
				continue;
			}
			WorldPoint t = tileFor(p);
			int d = Math.max(Math.abs(t.getX() - me.getX()), Math.abs(t.getY() - me.getY()));
			if (d <= bestDist)
			{
				bestDist = d;
				best = p;
			}
		}
		return best;
	}

	private void checkDiseaseNotifications()
	{
		for (FarmPatch p : FarmPatch.values())
		{
			PatchTimers.Status st = timers.getStatus(p);
			PatchTimers.Status was = lastStatus.get(p);
			if (statusesPrimed && st != was && config.notifyDisease()
				&& (st == PatchTimers.Status.DISEASED || st == PatchTimers.Status.DEAD))
			{
				Crop crop = timers.getStatusCrop(p);
				notifier.notify(p.getDisplayName() + (crop == null ? "" : " (" + crop.getDisplayName() + ")")
					+ (st == PatchTimers.Status.DISEASED ? " is diseased! Cure it before it dies." : " has died."));
			}
			if (st == null)
			{
				lastStatus.remove(p);
			}
			else
			{
				lastStatus.put(p, st);
			}
		}
		statusesPrimed = true;
	}

	private void readContract()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		FarmingContract now = FarmingContract.of(client.getVarbitValue(FarmingContract.TYPE_VARBIT),
			client.getVarbitValue(FarmingContract.COMPLETE_VARBIT));
		FarmingContract was = contract;
		boolean same = was == null ? now == null : now != null && was.value == now.value && was.complete == now.complete;
		if (!same)
		{
			contract = now;
			dirty = true;
		}
	}

	FarmingContract getContract()
	{
		return contract;
	}

	List<RunReadyOverlay.Line> getReadyLines()
	{
		return readyLines;
	}

	boolean showReadyOverlay()
	{
		return config.showReadyOverlay();
	}

	/** What the on-screen "run now?" box says: ready counts per run, disease, contract. */
	private void updateReadyLines()
	{
		long now = System.currentTimeMillis();
		List<RunReadyOverlay.Line> lines = new ArrayList<>();
		Set<PatchType> runs = runs();
		Set<FarmPatch> off = readSet(config.disabledPatches(), FarmPatch.class);
		int sick = 0;
		for (PatchType type : PatchType.values())
		{
			int ready = 0;
			int tracked = 0;
			long soonest = Long.MAX_VALUE;
			for (FarmPatch p : FarmPatch.values())
			{
				if (p.getType() != type || off.contains(p))
				{
					continue;
				}
				PatchTimers.Planting pl = timers.get(p);
				PatchTimers.Status st = timers.getStatus(p);
				if (st == PatchTimers.Status.DISEASED || st == PatchTimers.Status.DEAD)
				{
					sick++;
				}
				if (pl == null)
				{
					continue;
				}
				tracked++;
				if (pl.isReady(now))
				{
					ready++;
				}
				else
				{
					soonest = Math.min(soonest, pl.millisLeft(now));
				}
			}
			if (!runs.contains(type) || tracked == 0)
			{
				continue;
			}
			if (ready > 0)
			{
				lines.add(new RunReadyOverlay.Line(type.getDisplayName(), ready + " of " + tracked + " ready",
					new Color(90, 200, 90)));
			}
			else if (soonest != Long.MAX_VALUE)
			{
				lines.add(new RunReadyOverlay.Line(type.getDisplayName(), "in " + shortTime(soonest), Color.WHITE));
			}
		}
		if (sick > 0)
		{
			lines.add(new RunReadyOverlay.Line("Diseased/dead", String.valueOf(sick), new Color(230, 80, 70)));
		}
		FarmingContract c = contract;
		if (c != null)
		{
			lines.add(new RunReadyOverlay.Line("Contract", c.complete ? "claim reward" : c.name,
				c.complete ? new Color(90, 200, 90) : new Color(255, 200, 60)));
		}
		readyLines = lines;
	}

	private static String shortTime(long ms)
	{
		long min = Math.max(1, (ms + 59_999) / 60_000);
		return min < 60 ? min + "m" : (min / 60) + "h " + (min % 60) + "m";
	}

	private void saveHistory()
	{
		if (configManager.getRSProfileKey() != null)
		{
			configManager.setRSProfileConfiguration(HerbRunConfig.GROUP, HISTORY_KEY, history.serialize());
			configManager.setRSProfileConfiguration(HerbRunConfig.GROUP, CURRENT_RUN_KEY, history.serializeCurrent());
		}
	}

	/** Called from the panel (Swing thread). */
	private void clearHistory()
	{
		history.clear();
		saveHistory();
		dirty = true;
	}

	private static final long DONE_KEEP_MS = 30 * 60_000L;
	private final Map<FarmPatch, Long> doneAt = new EnumMap<>(FarmPatch.class);
	private Set<FarmPatch> lastReady = null;

	/**
	 * Replans when a patch becomes ready, and remembers patches you've just done so they stay
	 * on the list (green) instead of the plan jumping back to a full run.
	 */
	private void trackReadyPatches()
	{
		if (!timersLoaded)
		{
			return;
		}
		long now = System.currentTimeMillis();
		Set<FarmPatch> ready = EnumSet.noneOf(FarmPatch.class);
		for (FarmPatch p : FarmPatch.values())
		{
			if (timers.needsVisit(p, now))
			{
				ready.add(p);
			}
		}
		if (lastReady != null)
		{
			for (FarmPatch p : lastReady)
			{
				if (!ready.contains(p))
				{
					doneAt.put(p, now);
				}
			}
			if (!lastReady.equals(ready))
			{
				dirty = true;
			}
		}
		if (doneAt.entrySet().removeIf(e -> ready.contains(e.getKey()) || now - e.getValue() > DONE_KEEP_MS))
		{
			dirty = true;
		}
		lastReady = ready;
	}

	// ================================================================ accessors for overlays

	Set<Integer> getHighlightIds()
	{
		return highlightIds;
	}

	FarmPatch getNextPatch()
	{
		return nextPatch;
	}

	/** The loaded game object for this patch, or null if it isn't in view. */
	GameObject objectFor(FarmPatch patch)
	{
		for (Map.Entry<GameObject, Integer> e : patchObjects.entrySet())
		{
			if (patchFor(e.getKey().getWorldLocation(), e.getValue()) == patch)
			{
				return e.getKey();
			}
		}
		return null;
	}

	/**
	 * Best known tile for a patch: the patch object itself when it's loaded, else the tile
	 * learned from the game earlier, else the built-in tile.
	 */
	WorldPoint tileFor(FarmPatch patch)
	{
		GameObject obj = objectFor(patch);
		if (obj != null)
		{
			return obj.getWorldLocation();
		}
		WorldPoint learned = learnedTiles.get(patch);
		return learned != null ? learned : new WorldPoint(patch.getX(), patch.getY(), 0);
	}

	private void loadTiles()
	{
		learnedTiles.clear();
		String raw = configManager.getConfiguration(HerbRunConfig.GROUP, TILES_KEY);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		for (String part : raw.split(";"))
		{
			String[] c = part.split(":");
			try
			{
				learnedTiles.put(FarmPatch.valueOf(c[0]),
					new WorldPoint(Integer.parseInt(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3])));
			}
			catch (RuntimeException ignored)
			{
				// old or bad entry
			}
		}
	}

	private void saveTileMap(String key, Map<FarmPatch, WorldPoint> map)
	{
		StringBuilder sb = new StringBuilder();
		map.forEach((p, w) -> sb.append(sb.length() > 0 ? ";" : "").append(p.name()).append(':')
			.append(w.getX()).append(':').append(w.getY()).append(':').append(w.getPlane()));
		configManager.setConfiguration(HerbRunConfig.GROUP, key, sb.toString());
	}

	private void loadTileMap(String key, Map<FarmPatch, WorldPoint> map)
	{
		map.clear();
		String raw = configManager.getConfiguration(HerbRunConfig.GROUP, key);
		if (raw == null || raw.isEmpty())
		{
			return;
		}
		for (String part : raw.split(";"))
		{
			String[] c = part.split(":");
			try
			{
				map.put(FarmPatch.valueOf(c[0]),
					new WorldPoint(Integer.parseInt(c[1]), Integer.parseInt(c[2]), Integer.parseInt(c[3])));
			}
			catch (RuntimeException ignored)
			{
				// old or bad entry
			}
		}
	}

	private void saveTiles()
	{
		StringBuilder sb = new StringBuilder();
		learnedTiles.forEach((p, w) -> sb.append(sb.length() > 0 ? ";" : "").append(p.name()).append(':')
			.append(w.getX()).append(':').append(w.getY()).append(':').append(w.getPlane()));
		configManager.setConfiguration(HerbRunConfig.GROUP, TILES_KEY, sb.toString());
	}

	String getStepItem()
	{
		return stepItem;
	}

	// ================================================================ step highlight

	private RunPlan.RouteStep stepFor(FarmPatch patch)
	{
		if (lastPlan == null)
		{
			return null;
		}
		for (RunPlan.RouteStep s : lastPlan.route)
		{
			if (s.patch == patch)
			{
				return s;
			}
		}
		return null;
	}

	/** Compost or payment leaving the inventory after planting means that step is done. */
	private void noticeItemsUsed()
	{
		for (Map.Entry<FarmPatch, StepItems.Progress> e : progress.entrySet())
		{
			RunPlan.RouteStep step = stepFor(e.getKey());
			StepItems.Progress p = e.getValue();
			if (step == null)
			{
				continue;
			}
			if (!p.composted && step.compost != null && !step.compost.startsWith("Bottomless")
				&& inventoryItems.getOrDefault(step.compost, 0) < p.compostBaseline)
			{
				p.composted = true;
			}
			if (!p.paid && step.paymentItem != null
				&& inventoryItems.getOrDefault(step.paymentItem, 0) < p.paymentBaseline)
			{
				p.paid = true;
			}
		}
	}

	/**
	 * Works out which stop you're at (the route patch in the area you're standing in, else the
	 * next stop) and which inventory item its current step needs.
	 */
	private void updateStepItem()
	{
		Player player = client.getLocalPlayer();
		if (lastPlan == null || player == null)
		{
			stepItem = null;
			return;
		}
		long now = System.currentTimeMillis();
		WorldPoint wp = player.getWorldLocation();
		int region = FarmPatch.regionOf(wp.getX(), wp.getY());

		RunPlan.RouteStep focus = null;
		for (RunPlan.RouteStep s : lastPlan.route)
		{
			if (!s.patch.inRegion(region))
			{
				continue;
			}
			StepItems.Progress p = progress.get(s.patch);
			if (p != null && StepItems.next(s, timers.get(s.patch), timers.getStatus(s.patch), p, now) != null)
			{
				focus = s;
				break;
			}
			if (focus == null && timers.needsVisit(s.patch, now))
			{
				focus = s;
			}
		}
		boolean travelling = false;
		if (focus == null && nextPatch != null)
		{
			focus = stepFor(nextPatch);
			// Not at the next patch yet: the first thing to do is the teleport.
			WorldPoint t = tileFor(nextPatch);
			travelling = focus != null && focus.teleport != null && dist(wp, t.getX(), t.getY()) > 25;
		}

		StepItems.Next next = focus == null ? null
			: StepItems.next(focus, timers.get(focus.patch), timers.getStatus(focus.patch), progress.get(focus.patch), now);
		String spell = null;
		if (travelling)
		{
			Teleport.Kind kind = focus.teleport.getKind();
			if (kind == Teleport.Kind.SPELL)
			{
				spell = focus.teleport.getLabel();
				next = new StepItems.Next(null, "Cast " + spell);
			}
			else if ((kind == Teleport.Kind.ITEM || kind == Teleport.Kind.TABLET) && focus.teleportItem != null)
			{
				next = new StepItems.Next(focus.teleportItem, "Teleport");
			}
			else if (focus.teleport == Teleport.SPIRIT_TREE && nearest(spiritTreeObjects, wp, 40) == null
				&& dist(wp, GE_SPIRIT_TREE.getX(), GE_SPIRIT_TREE.getY()) > 120)
			{
				// First get to the Grand Exchange spirit tree: Varrock Teleport.
				if (config.useSpells() && client.getRealSkillLevel(Skill.MAGIC) >= 25)
				{
					spell = Teleport.VARROCK_SPELL.getLabel();
					next = new StepItems.Next(null, "Cast Varrock Teleport, then the spirit tree by the GE");
				}
				else if (inventoryItems.containsKey("Varrock teleport"))
				{
					next = new StepItems.Next("Varrock teleport", "Teleport to Varrock, then the spirit tree by the GE");
				}
			}
		}
		spellToHighlight = config.highlightInventory() ? spell : null;
		String item = next == null ? null : next.item;
		String action = next == null ? null : next.action;
		FarmPatch patch = next == null ? null : focus.patch;
		stepItem = item;
		if (action != null && action.startsWith("Pay the gardener") && config.highlightGardener())
		{
			payPatch = patch;
			payLabel = action.contains("clear") ? "Pay " + RunPlanner.TREE_REMOVAL_COINS + " coins to chop it down"
				: "Pay: " + (focus.paymentQuantity > 1 ? focus.paymentQuantity + " x " : "") + focus.paymentItem;
		}
		else
		{
			payPatch = null;
			payLabel = null;
		}
		if (patch != stepPatch || !java.util.Objects.equals(action, stepAction))
		{
			stepPatch = patch;
			stepAction = action;
			SwingUtilities.invokeLater(() -> panel.setStepHint(patch, action, item));
		}
	}

	private volatile String spellToHighlight;

	/** The teleport spell to outline in the spellbook (e.g. "Falador Teleport"), or null. */
	String getSpellToHighlight()
	{
		return spellToHighlight;
	}

	// ================================================================ items, quests, planning

	private void snapshot(ItemContainer container, Map<String, Integer> target, Map<String, Integer> nameToId)
	{
		target.clear();
		nameToId.clear();
		if (container == null)
		{
			return;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(item.getId());
			String name = itemManager.getItemComposition(canonical).getName();
			target.merge(name, item.getQuantity(), Integer::sum);
			nameToId.putIfAbsent(name, item.getId());
		}
	}

	/** Must run on the client thread (Quest.getState runs a client script). */
	private void refreshRequirements()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Set<String> wanted = new HashSet<>();
		Collections.addAll(wanted, TELEPORT_QUESTS);
		for (FarmPatch.Requirement r : FarmPatch.Requirement.values())
		{
			if (r.questName() != null)
			{
				wanted.add(r.questName());
			}
		}
		Set<String> before = new HashSet<>(completedQuests);
		boolean fairyBefore = fairyRingsUnlocked;
		completedQuests.clear();
		fairyRingsUnlocked = false;
		// Looked up by name so a renamed enum constant can't break the build.
		for (Quest quest : Quest.values())
		{
			String name = quest.getName();
			if (wanted.contains(name) && quest.getState(client) == QuestState.FINISHED)
			{
				completedQuests.add(name);
			}
			else if (FAIRYTALE_II.equals(name) && quest.getState(client) != QuestState.NOT_STARTED)
			{
				fairyRingsUnlocked = true;
			}
		}
		if (!before.equals(completedQuests) || fairyBefore != fairyRingsUnlocked)
		{
			dirty = true;
		}
	}

	private int farmingLevel()
	{
		return config.useBoostedLevel()
			? client.getBoostedSkillLevel(Skill.FARMING)
			: client.getRealSkillLevel(Skill.FARMING);
	}

	private Set<PatchType> runs()
	{
		Set<PatchType> runs = EnumSet.noneOf(PatchType.class);
		if (config.includeHerbs())
		{
			runs.add(PatchType.HERB);
		}
		if (config.includeTrees())
		{
			runs.add(PatchType.TREE);
		}
		if (config.includeFruitTrees())
		{
			runs.add(PatchType.FRUIT_TREE);
		}
		if (config.includeHardwood())
		{
			runs.add(PatchType.HARDWOOD);
		}
		if (config.includeAllotments())
		{
			runs.add(PatchType.ALLOTMENT);
		}
		return runs;
	}

	private void recompute()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int level = farmingLevel();

		Set<FarmPatch> unlocked = EnumSet.noneOf(FarmPatch.class);
		for (FarmPatch patch : FarmPatch.values())
		{
			FarmPatch.Requirement req = patch.getRequirement();
			boolean ok;
			switch (req)
			{
				case NONE:
					ok = true;
					break;
				case FARMING_45:
					ok = level >= 45;
					break;
				case FARMING_65:
					ok = level >= 65;
					break;
				case FARMING_85:
					ok = level >= 85;
					break;
				case MORYTANIA_ELITE:
					ok = config.harmonyUnlocked();
					break;
				default:
					ok = completedQuests.contains(req.questName());
			}
			if (ok)
			{
				unlocked.add(patch);
			}
		}

		Map<String, Integer> owned = new HashMap<>(bankItems);
		inventoryItems.forEach((k, v) -> owned.merge(k, v, Integer::sum));
		equipmentItems.forEach((k, v) -> owned.merge(k, v, Integer::sum));

		RunPlanner.Options options = new RunPlanner.Options();
		options.runs.clear();
		options.runs.addAll(runs());
		options.payGardeners = config.payGardeners();
		options.useCompost = config.useCompost();
		options.bringRake = config.bringRake();
		options.onlyReady = config.onlyReady();
		options.herbYield = history.averageHerbYield();
		for (FarmPatch p : FarmPatch.values())
		{
			PatchTimers.Planting growing = timers.get(p);
			Crop crop = growing != null ? growing.crop : timers.getStatusCrop(p);
			if (crop != null)
			{
				options.growingCrops.put(p, crop);
			}
		}
		long nowMs = System.currentTimeMillis();
		for (FarmPatch p : FarmPatch.values())
		{
			if (!timers.needsVisit(p, nowMs))
			{
				PatchTimers.Planting pl = timers.get(p);
				options.notReady.put(p, pl == null ? -1L : pl.millisLeft(nowMs));
			}
		}
		options.doneThisRun.addAll(doneAt.keySet());
		for (FarmPatch p : doneAt.keySet())
		{
			PatchTimers.Planting pl = timers.get(p);
			if (pl != null && pl.crop != null)
			{
				options.plantedCrops.put(p, pl.crop);
			}
		}
		for (FarmPatch p : FarmPatch.values())
		{
			if (timers.getStatus(p) == PatchTimers.Status.DISEASED)
			{
				options.diseased.add(p);
			}
		}
		FarmingContract c = contract;
		options.contractCrop = c != null && !c.complete && config.plantContract() ? c.crop : null;
		double herbYield = history.averageHerbYield();
		herbProfit.clear();
		for (Crop crop : Crop.values())
		{
			int herb = HerbPrices.grimyId(crop);
			if (herb > 0)
			{
				long herbPrice = itemManager.getItemPrice(herb);
				long seedPrice = itemManager.getItemPrice(HerbPrices.seedId(crop));
				if (herbPrice > 0)
				{
					herbProfit.put(crop, HerbPrices.profitPerPatch(herbYield, herbPrice, seedPrice));
				}
			}
		}
		if (config.profitFirst())
		{
			options.cropRank = HerbPrices.rankByProfit(herbProfit);
		}
		options.bankKnown = bankSeen;
		options.disabledCrops = readSet(config.disabledCrops(), Crop.class);
		options.disabledPatches = readSet(config.disabledPatches(), FarmPatch.class);
		for (FarmPatch p : FarmPatch.values())
		{
			PatchTimers.Status st = timers.getStatus(p);
			if (timers.get(p) != null || (st != null && st != PatchTimers.Status.EMPTY && st != PatchTimers.Status.WEEDS))
			{
				options.usedPatches.add(p);
			}
		}
		Teleport.Context ctx = options.teleports;
		ctx.magicLevel = client.getRealSkillLevel(Skill.MAGIC);
		ctx.standardSpellbook = client.getVarbitValue(SPELLBOOK_VARBIT) == 0;
		ctx.completedQuests = new HashSet<>(completedQuests);
		ctx.fairyRingsUnlocked = fairyRingsUnlocked;
		ctx.staffless = config.staffless();
		ctx.disabled = readSet(config.disabledTeleports(), Teleport.class);
		ctx.useSpells = config.useSpells();
		ctx.useTabs = config.useTabs();
		ctx.chainFairyRings = config.chainFairyRings();

		RunPlan plan = RunPlanner.plan(level, unlocked, owned, options);
		lastPlan = plan;

		// Highlight what's still to take from the bank (first leg).
		Set<Integer> ids = new HashSet<>();
		for (RunPlan.Withdrawal w : plan.withdrawals)
		{
			int carried = inventoryItems.getOrDefault(w.itemName, 0) + equipmentItems.getOrDefault(w.itemName, 0);
			Integer bankId = bankNameToId.get(w.itemName);
			if (bankId != null && carried < w.quantity)
			{
				ids.add(bankId);
			}
		}
		highlightIds = ids;

		Map<String, Integer> carried = new HashMap<>(inventoryItems);
		equipmentItems.forEach((k, v) -> carried.merge(k, v, Integer::sum));
		Map<String, Integer> nameToId = new HashMap<>(equipmentNameToId);
		inventoryNameToId.forEach(nameToId::putIfAbsent);
		bankNameToId.forEach(nameToId::putIfAbsent);
		Set<String> worn = new HashSet<>(equipmentItems.keySet());
		Set<PatchType> runs = options.runs.isEmpty() ? EnumSet.noneOf(PatchType.class) : EnumSet.copyOf(options.runs);
		Set<Teleport> disabled = EnumSet.noneOf(Teleport.class);
		disabled.addAll(ctx.disabled);
		boolean useSpells = ctx.useSpells;
		boolean useTabs = ctx.useTabs;
		Set<Crop> offCrops = options.disabledCrops;
		Set<FarmPatch> offPatches = options.disabledPatches;
		Map<Crop, Integer> profitCopy = new EnumMap<>(Crop.class);
		profitCopy.putAll(herbProfit);
		List<RunHistory.Run> historyRuns = history.runs();
		RunHistory.Run currentRun = history.current();
		long totalProfit = history.totalProfit();
		FarmingContract contractNow = contract;
		Map<String, Boolean> settings = new java.util.LinkedHashMap<>();
		settings.put("guideLine", config.guideLine());
		settings.put("shortestPathLine", config.useShortestPath());
		settings.put("pathMinigameTeleports", config.pathMinigameTeleports());
		settings.put("highlightPatch", config.highlightPatch());
		settings.put("highlightGardener", config.highlightGardener());
		settings.put("pointerArrow", config.hintArrow());
		settings.put("highlightInventory", config.highlightInventory());
		settings.put("highlightBank", config.highlightBank());
		settings.put("notifyReady", config.notifyReady());
		settings.put("chainFairyRings", config.chainFairyRings());
		settings.put("payGardeners", config.payGardeners());
		settings.put("useCompost", config.useCompost());
		settings.put("onlyReady", config.onlyReady());
		settings.put("activeTimersOnly", config.activeTimersOnly());
		settings.put("profitFirst", config.profitFirst());
		settings.put("plantContract", config.plantContract());
		settings.put("notifyDisease", config.notifyDisease());
		settings.put("showReadyOverlay", config.showReadyOverlay());
		settings.put("showHistory", config.showHistory());
		settings.put("bringRake", config.bringRake());
		settings.put("importTimeTracking", config.importTimeTracking());
		settings.put("useBoostedLevel", config.useBoostedLevel());
		settings.put("staffless", config.staffless());
		settings.put("harmonyUnlocked", config.harmonyUnlocked());
		settings.put("showTimers", config.showTimers());
		settings.put("showTravel", config.showTravel());
		SwingUtilities.invokeLater(() ->
		{
			panel.setSettings(settings);
			panel.setRuns(runs);
			panel.setPicks(offCrops, offPatches);
			panel.setExtras(profitCopy, herbYield, historyRuns, currentRun, totalProfit, contractNow);
			panel.setPlan(plan, carried, worn, nameToId, disabled, useSpells, useTabs);
		});
	}

	// ================================================================ patch state sync

	/**
	 * Reads the state of every farming patch in the area you're standing in, straight from the
	 * game's farming variables (as RuneLite's Time Tracking does), and corrects the timers.
	 * Also learns your account's growth-tick offset when a patch changes stage in front of you.
	 */
	private void syncNearbyPatches()
	{
		Player player = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || player == null)
		{
			return;
		}
		WorldPoint wp = player.getWorldLocation();
		int region = FarmPatch.regionOf(wp.getX(), wp.getY());
		long now = System.currentTimeMillis();
		boolean changed = false;
		boolean clockChanged = false;
		for (FarmPatch patch : FarmPatch.values())
		{
			if (!patch.inRegion(region))
			{
				continue;
			}
			int value = client.getVarbitValue(patch.getVarbit());
			PatchStates.Decoded d = PatchStates.get().decode(patch.getType(), value);

			Integer last = lastSeenValue.put(patch, value);
			if (last != null && last != value && d.crop != null
				&& (d.state == PatchStates.State.GROWING || d.state == PatchStates.State.READY))
			{
				PatchStates.Decoded before = PatchStates.get().decode(patch.getType(), last);
				if (before.crop == d.crop && before.state == PatchStates.State.GROWING && d.stage > before.stage)
				{
					clock.learn(patch.getType(), now);
					clockChanged = true;
				}
			}

			PatchTimers.Planting existing = timers.get(patch);
			boolean justPlanted = existing != null && now - existing.plantedAtMillis < 2 * 60_000L;
			changed |= apply(patch, d, existing, justPlanted, now, -1);
		}
		if (clockChanged)
		{
			configManager.setRSProfileConfiguration(HerbRunConfig.GROUP, CLOCK_KEY, clock.serialize());
		}
		if (changed)
		{
			saveTimers();
			dirty = true;
		}
	}

	/** changedAt < 0 means "seen live right now"; otherwise when that stage started (import). */
	private boolean apply(FarmPatch patch, PatchStates.Decoded d, PatchTimers.Planting existing,
		boolean justPlanted, long now, long changedAt)
	{
		long tolerance = GrowthClock.tickMillis(patch.getType()) + 5 * 60_000L;
		switch (d.state)
		{
			case GROWING:
			{
				if (d.crop == null)
				{
					return setStatusIfNew(patch, PatchTimers.Status.GROWING_UNKNOWN, null);
				}
				long est = changedAt < 0
					? clock.readyFromLive(d.crop, d.stage, now)
					: GrowthClock.readyFromChange(d.crop, d.stage, changedAt);
				if (existing == null || existing.crop != d.crop
					|| Math.abs(existing.readyAtMillis() - est) > tolerance)
				{
					timers.syncReadyAt(patch, d.crop, est);
					return true;
				}
				return false;
			}
			case READY:
			{
				Crop crop = d.crop != null ? d.crop : existing != null ? existing.crop : null;
				if (crop == null)
				{
					return false;
				}
				if (existing == null || existing.crop != crop || !existing.isReady(now))
				{
					timers.syncReadyAt(patch, crop, changedAt < 0 ? now : Math.min(changedAt, now));
					return true;
				}
				return false;
			}
			case STUMP:
				return setStatusIfNew(patch, PatchTimers.Status.STUMP, d.crop);
			case EMPTY:
				// Weeds stage 0 = fully raked; anything above means weeds to rake.
				return !justPlanted && setStatusIfNew(patch,
					d.stage > 0 ? PatchTimers.Status.WEEDS : PatchTimers.Status.EMPTY, null);
			case DISEASED:
				return setStatusIfNew(patch, PatchTimers.Status.DISEASED, d.crop);
			case DEAD:
				return setStatusIfNew(patch, PatchTimers.Status.DEAD, d.crop);
			default:
				return false;
		}
	}

	private boolean setStatusIfNew(FarmPatch patch, PatchTimers.Status status, Crop crop)
	{
		if (timers.getStatus(patch) == status && timers.getStatusCrop(patch) == crop)
		{
			return false;
		}
		timers.setStatus(patch, status, crop);
		return true;
	}

	/**
	 * RuneLite's Time Tracking plugin saves each patch's last seen value and when it changed
	 * ("value:unixSeconds" under "regionId.varbitId"). Use it for patches we know nothing newer about.
	 */
	private void importTimeTracking()
	{
		if (!config.importTimeTracking() || configManager.getRSProfileKey() == null)
		{
			return;
		}
		long now = System.currentTimeMillis();
		boolean changed = false;
		for (FarmPatch patch : FarmPatch.values())
		{
			String raw = configManager.getRSProfileConfiguration(TIME_TRACKING_GROUP,
				patch.getRegionId() + "." + patch.getVarbit());
			if (raw == null)
			{
				continue;
			}
			String[] parts = raw.split(":");
			if (parts.length < 2)
			{
				continue;
			}
			int value;
			long changedAt;
			try
			{
				value = Integer.parseInt(parts[0]);
				changedAt = Long.parseLong(parts[1]) * 1000L;
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			PatchTimers.Planting existing = timers.get(patch);
			if (existing != null && existing.plantedAtMillis >= changedAt - 60_000L)
			{
				continue; // what we have is newer
			}
			PatchStates.Decoded d = PatchStates.get().decode(patch.getType(), value);
			if (d.state == PatchStates.State.UNKNOWN || d.state == PatchStates.State.EMPTY)
			{
				continue;
			}
			changed |= apply(patch, d, existing, false, now, changedAt);
		}
		if (changed)
		{
			saveTimers();
			dirty = true;
		}
	}

	// ================================================================ navigation

	private void updateNavigation()
	{
		if (lastPlan == null || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		long now = System.currentTimeMillis();
		FarmPatch next = null;
		for (RunPlan.RouteStep step : lastPlan.route)
		{
			if (timers.needsVisit(step.patch, now))
			{
				next = step.patch;
				break;
			}
		}
		if (next != nextPatch)
		{
			nextPatch = next;
			FarmPatch shown = next;
			SwingUtilities.invokeLater(() -> panel.setNextPatch(shown));
		}

		WorldPoint target = next == null ? null : tileFor(next);
		WorldPoint patchTarget = target;
		String ringLabel = null;
		TileObject leadObj = null;
		String leadHint = null;
		Player player = client.getLocalPlayer();
		WorldPoint me = player == null ? null : player.getWorldLocation();
		RunPlan.RouteStep step = next == null ? null : stepFor(next);
		if (step != null && step.teleport != null && me != null && me.getPlane() == 0)
		{
			String code = step.teleport.getFairyCode();
			if (code != null)
			{
				// Fairy ring step: lead you to a ring (the nearest one loaded, or the one named in the
				// directions), until you've teleported.
				int[] dest = FairyRings.tile(code);
				if ((dest == null || dist(me, dest[0], dest[1]) > 20) && dist(me, target.getX(), target.getY()) > 25)
				{
					TileObject ringObj = nearest(fairyRingObjects, me, 40);
					int i = lastPlan.route.indexOf(step);
					int[] ring = FairyRings.tile(FairyRings.startRing(step, i > 0 ? lastPlan.route.get(i - 1) : null));
					if (ringObj != null)
					{
						target = ringObj.getWorldLocation();
					}
					else if (ring != null && dist(me, ring[0], ring[1]) <= RING_LEAD_RANGE)
					{
						target = new WorldPoint(ring[0], ring[1], 0);
					}
					if (ringObj != null || (ring != null && dist(me, ring[0], ring[1]) <= RING_LEAD_RANGE))
					{
						leadObj = ringObj;
						ringLabel = "Fairy ring: dial " + code;
						leadHint = "Dial " + code + " for " + next.getDisplayName();
					}
				}
			}
			else if (step.teleport == Teleport.SPIRIT_TREE && dist(me, target.getX(), target.getY()) > 40)
			{
				// Spirit tree step: lead you to the nearest spirit tree (or the one by the Grand
				// Exchange after Varrock Teleport) and say which destination to pick.
				TileObject tree = nearest(spiritTreeObjects, me, 40);
				String where = spiritTreeDestination(next);
				if (tree != null)
				{
					target = tree.getWorldLocation();
					leadObj = tree;
					ringLabel = "Spirit tree: " + where;
					leadHint = "Spirit tree: pick " + where;
				}
				else if (dist(me, GE_SPIRIT_TREE.getX(), GE_SPIRIT_TREE.getY()) <= 120)
				{
					target = GE_SPIRIT_TREE;
					ringLabel = "Spirit tree: " + where;
					leadHint = "Spirit tree: pick " + where;
				}
			}
		}
		navLeadObject = leadObj;
		navLeadHint = leadHint;
		navTarget = target;
		navRingLabel = ringLabel;

		// Shortest Path plugin: always aim it at the patch itself, so it plans the whole trip
		// (including the fairy ring or spirit tree) and draws it. Post again whenever the tile changes.
		// After a teleport, ask Shortest Path again from where you landed: a route worked out
		// from the other side of the map can come back "could not be reached" and isn't retried.
		if (me != null)
		{
			if (lastPlayerTile != null && (lastPlayerTile.getPlane() != me.getPlane()
				|| dist(me, lastPlayerTile.getX(), lastPlayerTile.getY()) > 15))
			{
				shortestPathTarget = null;
			}
			lastPlayerTile = me;
		}
		WorldPoint pathTarget = patchTarget;
		if (next != null && next.getType().hasGardenerPayment())
		{
			learnApproach(next);
			WorldPoint approach = approachTiles.get(next);
			if (approach != null)
			{
				pathTarget = approach; // the patch itself can't be walked on
			}
		}
		target = pathTarget;
		if (config.useShortestPath() && !java.util.Objects.equals(target, shortestPathTarget))
		{
			if (target == null)
			{
				clearShortestPath();
			}
			else
			{
				Map<String, Object> data = new HashMap<>();
				data.put("target", target);
				// Our own settings for the line, applied by Shortest Path to this route only
				// (cleared again when we clear the path).
				Map<String, Object> overrides = new HashMap<>();
				overrides.put("useTeleportationMinigames", config.pathMinigameTeleports());
				data.put("config", overrides);
				eventBus.post(new PluginMessage("shortestpath", "path", data));
				shortestPathTarget = target;
			}
		}

		// The game's own hint arrow isn't used any more (it blinks and can't follow objects
		// well); the overlay draws a steady arrow instead. Clear one left by an older version.
		clearHintArrow();

		updateGuideLine(next, ringLabel != null);
	}

	// ================================================================ built-in guide line

	/** Corners of the line to draw (world tiles), from you to the target. */
	private volatile List<WorldPoint> guidePath = Collections.emptyList();
	/** Every step of the current route (world x, y), kept so the line stays put while you follow it. */
	private List<int[]> guideSteps = Collections.emptyList();
	private String guideKey;
	private WorldPoint guideFrom;
	private int[] guideBox;
	/** Walking map of the whole world, loaded in the background at start-up. */
	private volatile WorldWalkMap worldMap;
	private volatile boolean worldMapLoading;

	List<WorldPoint> getGuidePath()
	{
		return guidePath;
	}

	private void loadWorldMap()
	{
		if (worldMap != null || worldMapLoading)
		{
			return;
		}
		worldMapLoading = true;
		Thread t = new Thread(() ->
		{
			try (java.io.InputStream in = HerbRunPlugin.class.getResourceAsStream("walk-map.zip"))
			{
				if (in != null)
				{
					WorldWalkMap m = WorldWalkMap.load(in);
					try (java.io.InputStream links = HerbRunPlugin.class.getResourceAsStream("walk-links.tsv"))
					{
						if (links != null)
						{
							m.loadLinks(links);
						}
					}
					worldMap = m;
				}
			}
			catch (Exception e)
			{
				log.warn("Couldn't load the walking map; the guide line will use the loaded area only", e);
			}
		}, "farm-run-walk-map");
		t.setDaemon(true);
		t.start();
	}

	/**
	 * Works out the walking route: to the gardener when you need to pay them, to the fairy ring /
	 * spirit tree first, otherwise to a tile beside the patch. Uses the whole-world walking map, so
	 * the route is right even before the target area has loaded.
	 */
	private void updateGuideLine(FarmPatch next, boolean leading)
	{
		WorldPoint target = navTarget;
		Player player = client.getLocalPlayer();
		if (!config.guideLine() || next == null || target == null || player == null)
		{
			clearGuide();
			return;
		}
		WorldPoint me = player.getWorldLocation();
		LocalPoint meLocal = player.getLocalLocation();
		if (me == null || meLocal == null || me.getPlane() != target.getPlane()
			|| dist(me, target.getX(), target.getY()) > MAX_GUIDE_TILES)
		{
			clearGuide(); // far away: teleport first
			return;
		}
		int baseX = me.getX() - meLocal.getSceneX();
		int baseY = me.getY() - meLocal.getSceneY();

		// The target box, in world tiles: the real object when it's loaded, else the known tile.
		int minX = target.getX();
		int minY = target.getY();
		int maxX = minX;
		int maxY = minY;
		boolean exact = false;
		boolean onto = false;
		NPC gardener = payLabel != null ? getGardenerToPay() : null;
		TileObject lead = leading ? navLeadObject : null;
		GameObject patchObj = leading ? null : objectFor(next);
		if (gardener != null && gardener.getWorldLocation() != null)
		{
			minX = maxX = gardener.getWorldLocation().getX();
			minY = maxY = gardener.getWorldLocation().getY();
			exact = true;
		}
		else if (lead instanceof GameObject)
		{
			GameObject g = (GameObject) lead;
			minX = baseX + g.getSceneMinLocation().getX();
			minY = baseY + g.getSceneMinLocation().getY();
			maxX = baseX + g.getSceneMaxLocation().getX();
			maxY = baseY + g.getSceneMaxLocation().getY();
			exact = true;
		}
		else if (lead != null && lead.getWorldLocation() != null)
		{
			minX = maxX = lead.getWorldLocation().getX();
			minY = maxY = lead.getWorldLocation().getY();
			exact = true;
		}
		else if (patchObj != null)
		{
			minX = baseX + patchObj.getSceneMinLocation().getX();
			minY = baseY + patchObj.getSceneMinLocation().getY();
			maxX = baseX + patchObj.getSceneMaxLocation().getX();
			maxY = baseY + patchObj.getSceneMaxLocation().getY();
			exact = true;
		}
		if (leading && navRingLabel != null && navRingLabel.startsWith("Fairy ring"))
		{
			onto = true; // you stand in a fairy ring
		}

		int agility = client.getBoostedSkillLevel(Skill.AGILITY);
		String key = (gardener != null ? "gardener" : minX + "," + minY + "," + maxX + "," + maxY)
			+ "," + me.getPlane() + "," + onto + "," + exact + "," + agility;
		if (me.equals(guideFrom) && key.equals(guideKey) && guideBox != null
			&& guideBox[0] == minX && guideBox[1] == minY)
		{
			return;
		}
		guideFrom = me;

		// Like the Shortest Path plugin: keep the route you're following, and only work it out
		// again when the target changes or you've gone more than RECALC_TILES off it. A moving
		// gardener only counts once they've wandered away from the end of the line.
		boolean sameTarget = key.equals(guideKey);
		if (sameTarget && gardener != null && !guideSteps.isEmpty())
		{
			int[] end = guideSteps.get(guideSteps.size() - 1);
			sameTarget = PathGuide.distToBox(end[0], end[1], minX, minY, maxX, maxY) <= GARDENER_SLACK;
		}
		int index = sameTarget ? nearestStep(guideSteps, me) : -1;
		if (index < 0)
		{
			PathGuide.Walkable map;
			WorldWalkMap world = worldMap;
			if (world != null && world.hasRegion(me.getX(), me.getY()))
			{
				world.setAgility(agility);
				map = world;
			}
			else
			{
				net.runelite.api.CollisionData[] maps = client.getCollisionMaps();
				if (maps == null || me.getPlane() >= maps.length || maps[me.getPlane()] == null)
				{
					clearGuide();
					return;
				}
				map = PathGuide.scene(maps[me.getPlane()].getFlags(), baseX, baseY);
			}
			List<int[]> steps = PathGuide.find(map, me.getPlane(), me.getX(), me.getY(), minX, minY, maxX, maxY, onto, GUIDE_MARGIN);
			// Only the rough tile known (the object isn't loaded yet): the tile may be inside the
			// patch, so allow a slightly bigger box.
			for (int grow = 1; steps.isEmpty() && !exact && grow <= 2; grow++)
			{
				steps = PathGuide.find(map, me.getPlane(), me.getX(), me.getY(),
					minX - grow, minY - grow, maxX + grow, maxY + grow, false, GUIDE_MARGIN);
			}
			guideSteps = PathGuide.gameWalk(map, me.getPlane(), steps);
			guideKey = key;
			guideBox = new int[]{minX, minY};
			index = 0;
		}
		guideBox = new int[]{minX, minY};

		// Draw from where you are on it; the part you've walked disappears.
		List<WorldPoint> out = new ArrayList<>();
		for (int i = index; i < guideSteps.size(); i++)
		{
			int[] t = guideSteps.get(i);
			out.add(new WorldPoint(t[0], t[1], me.getPlane()));
		}
		guidePath = out.size() >= 2 ? out : Collections.emptyList();
	}

	/**
	 * Your place on the route: the step you're standing on, else the nearest step within
	 * RECALC_TILES; -1 when you're further off than that (work it out again).
	 */
	private static int nearestStep(List<int[]> steps, WorldPoint me)
	{
		int best = -1;
		int bestDist = Integer.MAX_VALUE;
		for (int i = 0; i < steps.size(); i++)
		{
			int[] t = steps.get(i);
			int d = Math.max(Math.abs(t[0] - me.getX()), Math.abs(t[1] - me.getY()));
			if (d == 0)
			{
				return i;
			}
			if (d < bestDist)
			{
				bestDist = d;
				best = i;
			}
		}
		return bestDist <= RECALC_TILES ? best : -1;
	}

	private void clearGuide()
	{
		guidePath = Collections.emptyList();
		guideSteps = Collections.emptyList();
		guideKey = null;
		guideFrom = null;
		guideBox = null;
	}

	/**
	 * The guide line as points to draw, a tile apart (so it follows the ground), in local
	 * coordinates; null entries are off the loaded area (break the line there).
	 */
	List<LocalPoint> guideLinePoints()
	{
		List<WorldPoint> path = guidePath;
		Player player = client.getLocalPlayer();
		if (path.size() < 2 || player == null)
		{
			return Collections.emptyList();
		}
		WorldPoint me = player.getWorldLocation();
		LocalPoint anchor = player.getLocalLocation();
		if (me == null || anchor == null)
		{
			return Collections.emptyList();
		}
		List<LocalPoint> out = new ArrayList<>();
		for (int i = 0; i + 1 < path.size(); i++)
		{
			WorldPoint a = path.get(i);
			WorldPoint b = path.get(i + 1);
			int n = Math.max(1, Math.max(Math.abs(b.getX() - a.getX()), Math.abs(b.getY() - a.getY())));
			for (int k = (i == 0 ? 0 : 1); k <= n; k++)
			{
				double fx = a.getX() + (b.getX() - a.getX()) * (double) k / n;
				double fy = a.getY() + (b.getY() - a.getY()) * (double) k / n;
				int dx = (int) Math.round((fx - me.getX()) * Perspective.LOCAL_TILE_SIZE);
				int dy = (int) Math.round((fy - me.getY()) * Perspective.LOCAL_TILE_SIZE);
				LocalPoint lp = anchor.plus(dx, dy);
				int sx = lp.getSceneX();
				int sy = lp.getSceneY();
				out.add(sx < 1 || sy < 1 || sx >= SCENE_TILES - 1 || sy >= SCENE_TILES - 1 ? null : lp);
			}
		}
		return out;
	}

	private static final int MAX_GUIDE_TILES = 250;
	/** Same as the Shortest Path plugin's default "Recalculate Distance". */
	private static final int RECALC_TILES = 10;
	/** How far the gardener may wander from the end of the line before it's worked out again. */
	private static final int GARDENER_SLACK = 3;
	private static final int GUIDE_MARGIN = 48;
	private static final int SCENE_TILES = 104;

	private static final int RING_LEAD_RANGE = 120;
	/** The spirit tree just north-west of the Grand Exchange. */
	private static final WorldPoint GE_SPIRIT_TREE = new WorldPoint(3185, 3508, 0);

	private static int dist(WorldPoint p, int x, int y)
	{
		return Math.max(Math.abs(p.getX() - x), Math.abs(p.getY() - y));
	}

	private static TileObject nearest(Set<TileObject> objects, WorldPoint me, int range)
	{
		TileObject best = null;
		int bestDist = range;
		for (TileObject o : objects)
		{
			WorldPoint w = o.getWorldLocation();
			int d = dist(me, w.getX(), w.getY());
			if (w.getPlane() == me.getPlane() && d <= bestDist)
			{
				bestDist = d;
				best = o;
			}
		}
		return best;
	}

	private static String spiritTreeDestination(FarmPatch patch)
	{
		switch (patch)
		{
			case GNOME_TREE:
			case GNOME_FRUIT:
				return "Gnome Stronghold";
			case TREE_GNOME_VILLAGE_FRUIT:
				return "Tree Gnome Village";
			default:
				return patch.getDisplayName();
		}
	}

	/**
	 * Remembers where this patch's gardener stands: always a walkable tile right beside the patch,
	 * so it's what the walking line aims at. Learned once per patch and saved.
	 */
	private void learnApproach(FarmPatch patch)
	{
		if (approachTiles.containsKey(patch))
		{
			return;
		}
		int wanted = Gardeners.idFor(patch);
		for (NPC npc : gardeners)
		{
			if (npc.getId() == wanted && npc.getWorldLocation() != null)
			{
				approachTiles.put(patch, npc.getWorldLocation());
				saveTileMap(APPROACH_KEY, approachTiles);
				return;
			}
		}
	}

	boolean showArrow()
	{
		return config.hintArrow();
	}

	/** Where to go right now (the patch, or the fairy ring / spirit tree first), or null. */
	WorldPoint getNavTarget()
	{
		return navTarget;
	}

	/** The object to click first (fairy ring / spirit tree), or null. */
	TileObject getNavLeadObject()
	{
		return navLeadObject;
	}

	/** "Dial AIS for Nemus Retreat tree" / "Spirit tree: pick Gnome Stronghold", or null. */
	String getNavLeadHint()
	{
		return navLeadHint;
	}

	private static final int FAIRY_RING_INTERFACE = 398;

	/** The hint to show on screen: when the fairy ring interface is open, or you're at the ring/tree. */
	String getOnScreenLeadHint()
	{
		String hint = navLeadHint;
		if (hint == null)
		{
			return null;
		}
		if (hint.startsWith("Dial"))
		{
			for (int child = 0; child < 8; child++)
			{
				net.runelite.api.widgets.Widget w = client.getWidget(FAIRY_RING_INTERFACE, child);
				if (w != null && !w.isHidden())
				{
					return hint;
				}
			}
		}
		TileObject o = navLeadObject;
		Player player = client.getLocalPlayer();
		if (o != null && player != null)
		{
			WorldPoint me = player.getWorldLocation();
			WorldPoint t = o.getWorldLocation();
			if (me.getPlane() == t.getPlane() && dist(me, t.getX(), t.getY()) <= 6)
			{
				return hint;
			}
		}
		return null;
	}

	/** The loaded fairy ring object near a ring's map position, or null. */
	private TileObject ringObjectNear(int x, int y)
	{
		TileObject best = null;
		int bestDist = 8;
		for (TileObject o : fairyRingObjects)
		{
			WorldPoint w = o.getWorldLocation();
			int d = Math.max(Math.abs(w.getX() - x), Math.abs(w.getY() - y));
			if (w.getPlane() == 0 && d <= bestDist)
			{
				bestDist = d;
				best = o;
			}
		}
		return best;
	}

	/** The real fairy ring to outline, when heading to one and it's loaded. */
	TileObject getNavRingObject()
	{
		return navRingLabel == null ? null : navLeadObject;
	}

	/** The fairy ring to walk to first, or null when heading straight for the patch. */
	WorldPoint getNavRing()
	{
		return navRingLabel == null ? null : navTarget;
	}

	String getNavRingLabel()
	{
		return navRingLabel;
	}

	private void checkReadyNotifications()
	{
		if (!config.notifyReady())
		{
			return;
		}
		long now = System.currentTimeMillis();
		for (Map.Entry<FarmPatch, PatchTimers.Planting> e : timers.snapshot().entrySet())
		{
			if (e.getValue().isReady(now) && notifiedReady.add(e.getKey()))
			{
				// Only notify for patches that became ready while you were playing.
				if (now - e.getValue().readyAtMillis() < 60_000L)
				{
					notifier.notify(e.getKey().getDisplayName() + " (" + e.getValue().crop.getDisplayName() + ") is ready!");
				}
				dirty = true;
			}
		}
	}

	private void clearNavigation()
	{
		clearHintArrow();
		clearShortestPath();
	}

	private void clearHintArrow()
	{
		if (ourHintArrow)
		{
			client.clearHintArrow();
			ourHintArrow = false;
		}
	}

	private void clearShortestPath()
	{
		if (shortestPathTarget != null)
		{
			eventBus.post(new PluginMessage("shortestpath", "clear"));
			shortestPathTarget = null;
		}
	}

	// ================================================================ saving

	private void loadTimers()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return; // profile not known yet; try again next tick
		}
		timers.load(configManager.getRSProfileConfiguration(HerbRunConfig.GROUP, TIMERS_KEY));
		history.load(configManager.getRSProfileConfiguration(HerbRunConfig.GROUP, HISTORY_KEY));
		history.loadCurrent(configManager.getRSProfileConfiguration(HerbRunConfig.GROUP, CURRENT_RUN_KEY),
			System.currentTimeMillis());
		clock.load(configManager.getRSProfileConfiguration(HerbRunConfig.GROUP, CLOCK_KEY));
		timersLoaded = true;
		importTimeTracking();
		notifiedReady.clear();
		long now = System.currentTimeMillis();
		timers.snapshot().forEach((patch, planting) ->
		{
			if (planting.isReady(now))
			{
				notifiedReady.add(patch); // already ready before login: don't spam notifications
			}
		});
		dirty = true;
	}

	private void saveTimers()
	{
		configManager.setRSProfileConfiguration(HerbRunConfig.GROUP, TIMERS_KEY, timers.serialize());
	}

	/** Called from the panel's reset button (Swing thread). */
	private void resetTimers()
	{
		timers.load("");
		notifiedReady.clear();
		saveTimers();
		dirty = true;
	}

	// ================================================================ settings from the panel

	/** Reads a comma-separated list of enum names saved in config. Unknown names are skipped. */
	private static <E extends Enum<E>> Set<E> readSet(String raw, Class<E> type)
	{
		Set<E> out = EnumSet.noneOf(type);
		if (raw != null)
		{
			for (String part : raw.split(","))
			{
				try
				{
					if (!part.trim().isEmpty())
					{
						out.add(Enum.valueOf(type, part.trim()));
					}
				}
				catch (IllegalArgumentException ignored)
				{
					// renamed or removed in a newer version
				}
			}
		}
		return out;
	}

	private <E extends Enum<E>> void toggleInSet(String key, String raw, E value, Class<E> type)
	{
		Set<E> set = readSet(raw, type);
		if (!set.remove(value))
		{
			set.add(value);
		}
		StringBuilder sb = new StringBuilder();
		for (E e : set)
		{
			sb.append(sb.length() > 0 ? "," : "").append(e.name());
		}
		configManager.setConfiguration(HerbRunConfig.GROUP, key, sb.toString());
	}

	/** Called from a Travel tile click (Swing thread). Saving fires ConfigChanged. */
	private void toggleTeleport(Teleport teleport)
	{
		toggleInSet("disabledTeleports", config.disabledTeleports(), teleport, Teleport.class);
	}

	private void toggleCrop(Crop crop)
	{
		toggleInSet("disabledCrops", config.disabledCrops(), crop, Crop.class);
	}

	private void togglePatch(FarmPatch patch)
	{
		toggleInSet("disabledPatches", config.disabledPatches(), patch, FarmPatch.class);
	}

	/** Called from the panel's run checkboxes. */
	private void setRun(PatchType type, boolean on)
	{
		String key;
		switch (type)
		{
			case HERB:
				key = "includeHerbs";
				break;
			case TREE:
				key = "includeTrees";
				break;
			case FRUIT_TREE:
				key = "includeFruitTrees";
				break;
			case ALLOTMENT:
				key = "includeAllotments";
				break;
			default:
				key = "includeHardwood";
		}
		configManager.setConfiguration(HerbRunConfig.GROUP, key, on);
	}

	/** Called from the panel's Settings section. Saving fires ConfigChanged. */
	private void setSetting(String key, boolean value)
	{
		configManager.setConfiguration(HerbRunConfig.GROUP, key, value);
	}

	private void setUseSpells(boolean use)
	{
		configManager.setConfiguration(HerbRunConfig.GROUP, "useSpells", use);
	}

	private void setUseTabs(boolean use)
	{
		configManager.setConfiguration(HerbRunConfig.GROUP, "useTabs", use);
	}
}
