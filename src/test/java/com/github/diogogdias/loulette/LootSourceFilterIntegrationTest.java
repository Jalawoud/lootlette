package com.github.diogogdias.loulette;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.ServerNpcLoot;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LootSourceFilterIntegrationTest
{
	private LoulettePlugin plugin;
	private NpcSpinFilter filter;
	private LouletteConfig config;
	private NPC monkey;

	@Before
	public void setup() throws Exception
	{
		plugin = new LoulettePlugin();
		filter = mock(NpcSpinFilter.class);
		config = mock(LouletteConfig.class, CALLS_REAL_METHODS);
		set("npcSpinFilter", filter);
		set("config", config);
		set("client", mock(Client.class));
		monkey = mock(NPC.class);
		when(monkey.getName()).thenReturn("Monkey");
	}

	private void set(String name, Object value) throws Exception
	{
		Field field = LoulettePlugin.class.getDeclaredField(name);
		field.setAccessible(true);
		field.set(plugin, value);
	}

	private void death(NPC npc) throws Exception
	{
		Method method = LoulettePlugin.class.getDeclaredMethod("startSpin", NPC.class, long.class);
		method.setAccessible(true);
		method.invoke(plugin, npc, 100L);
	}

	@Test
	public void blockedMonkeyCannotStartADeathSpin() throws Exception
	{
		death(monkey);
		assertTrue(plugin.getActiveRolls().isEmpty());
		verify(filter).allows("Monkey");
	}

	@Test
	public void groundLootCannotBypassTheMonsterFilter()
	{
		NpcLootReceived event = mock(NpcLootReceived.class);
		when(event.getNpc()).thenReturn(monkey);
		when(event.getItems()).thenReturn(Arrays.asList(new ItemStack(526, 1)));
		plugin.onNpcLootReceived(event);
		assertTrue(plugin.getActiveRolls().isEmpty());
		verify(filter).allows("Monkey");
	}

	@Test
	public void serverLootCannotBypassTheMonsterFilter()
	{
		ServerNpcLoot event = mock(ServerNpcLoot.class);
		NPCComposition comp = mock(NPCComposition.class);
		when(comp.getName()).thenReturn("Monkey");
		when(event.getComposition()).thenReturn(comp);
		when(event.getItems()).thenReturn(Arrays.asList(new ItemStack(526, 1)));
		plugin.onServerNpcLoot(event);
		assertTrue(plugin.getActiveRolls().isEmpty());
		verify(filter).allows("Monkey");
	}

	@Test
	public void ignoreWinsEvenOverExplicitIncludes() throws Exception
	{
		when(config.ignoredNpcs()).thenReturn("Monkey");
		when(filter.allows("Monkey")).thenReturn(true);
		Method load = LoulettePlugin.class.getDeclaredMethod("loadIgnoredNpcs");
		load.setAccessible(true);
		load.invoke(plugin);
		death(monkey);
		assertTrue(plugin.getActiveRolls().isEmpty());
		verify(filter, never()).allows(anyString());
	}

	@Test
	public void disabledRaidRewardsCannotSpinFromLootEvent()
	{
		when(config.raidRewardSpins()).thenReturn(false);
		LootReceived event = mock(LootReceived.class);
		when(event.getType()).thenReturn(LootRecordType.EVENT);
		when(event.getName()).thenReturn("Chambers of Xeric");
		when(event.getItems()).thenReturn(Arrays.asList(new ItemStack(20997, 1)));
		plugin.onLootReceived(event);
		assertTrue(plugin.getActiveRolls().isEmpty());
	}

	@Test
	public void disabledRaidRewardsCannotStartAnAnticipationSpin() throws Exception
	{
		when(config.raidRewardSpins()).thenReturn(false);
		Field raids = LoulettePlugin.class.getDeclaredField("RAID_CHESTS");
		raids.setAccessible(true);
		Object raid = ((List<?>) raids.get(null)).get(0);
		Method start = LoulettePlugin.class.getDeclaredMethod("startChestAnticipation", raid.getClass(), WorldPoint.class, boolean.class, boolean.class, boolean.class);
		start.setAccessible(true);
		start.invoke(plugin, raid, null, true, true, true);
		assertTrue(plugin.getActiveRolls().isEmpty());
	}

	@Test
	public void raidToggleDoesNotDisableDoomAsAnOrdinaryBoss() throws Exception
	{
		when(config.raidRewardSpins()).thenReturn(false);
		when(filter.allows("Doom of Mokhaiotl")).thenReturn(true);
		Field raids = LoulettePlugin.class.getDeclaredField("RAID_CHESTS");
		raids.setAccessible(true);
		List<?> values = (List<?>) raids.get(null);
		Object doom = values.get(values.size() - 1);
		Method check = LoulettePlugin.class.getDeclaredMethod("shouldSpinChest", doom.getClass());
		check.setAccessible(true);
		assertEquals(true, check.invoke(plugin, doom));
	}

	@Test
	public void changingFilterRemovesAnAlreadyRunningRejectedSpin()
	{
		plugin.getActiveRolls().add(new ActiveRoll(1, null, 100L, "Monkey"));
		ConfigChanged event = new ConfigChanged();
		event.setGroup(LoulettePlugin.CONFIG_GROUP);
		event.setKey("spinSources");
		plugin.onConfigChanged(event);
		assertTrue(plugin.getActiveRolls().isEmpty());
	}
	@Test
	public void eligibleSourceStillSpinsWhenTheActualDropIsOrdinaryLoot() throws Exception
	{
		when(monkey.getName()).thenReturn("General Graardor");
		when(filter.allows("General Graardor")).thenReturn(true);
		DropTableService tables = mock(DropTableService.class);
		when(tables.table("General Graardor")).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(Collections.emptyList()));
		set("dropTableService", tables);
		net.runelite.client.game.ItemManager items = mock(net.runelite.client.game.ItemManager.class);
		when(items.getItemPrice(995)).thenReturn(1L);
		set("itemManager", items);
		NpcLootReceived event = mock(NpcLootReceived.class);
		when(event.getNpc()).thenReturn(monkey);
		when(event.getItems()).thenReturn(Arrays.asList(new ItemStack(995, 100)));
		plugin.onNpcLootReceived(event);
		assertEquals(1, plugin.getActiveRolls().size());
		assertFalse(plugin.getActiveRolls().get(0).getReels().isEmpty());
	}

	@Test
	public void eligibleSourceCanStillStartTheAnticipationBeforeLootArrives() throws Exception
	{
		when(monkey.getName()).thenReturn("General Graardor");
		when(filter.allows("General Graardor")).thenReturn(true);
		DropTableService tables = mock(DropTableService.class);
		when(tables.table("General Graardor")).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
			Arrays.asList(new DropEntry("Coins", false, 1, .5, false))));
		set("dropTableService", tables);
		set("itemManager", mock(net.runelite.client.game.ItemManager.class));
		death(monkey);
		assertEquals(1, plugin.getActiveRolls().size());
		assertFalse(plugin.getActiveRolls().get(0).getReels().isEmpty());
	}

}
