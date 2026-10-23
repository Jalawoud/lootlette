package com.github.diogogdias.loulette;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NpcSpinFilterTest
{
	private LouletteConfig config;
	private DropTableService tables;
	private CollectionLogCatalog catalog;
	private NpcSpinFilter filter;

	@Before
	public void setup()
	{
		config = mock(LouletteConfig.class, CALLS_REAL_METHODS);
		tables = new DropTableService(null, new Gson(), config);
		catalog = mock(CollectionLogCatalog.class);
		when(catalog.isLoaded()).thenReturn(true);
		filter = new NpcSpinFilter(config, tables, catalog);
	}

	@Test
	public void meaningfulModeIsTheDefaultAndKaramjaMonkeysDoNotQualify()
	{
		assertEquals(SpinSources.MEANINGFUL_UNIQUES, config.spinSources());
		assertFalse(config.includeSharedDrops());
		assertTrue(config.raidRewardSpins());
		assertFalse(filter.allows("Monkey"));
		assertFalse(filter.allows("Chicken"));
		assertFalse(filter.allows("Cow"));
	}

	@Test
	public void realBundledUniquesAndMissingSlayerSourcesQualifyBeforeCatalogLoads()
	{
		when(catalog.isLoaded()).thenReturn(false);
		for (String name : Arrays.asList("General Graardor", "Zulrah", "Vorkath", "Demonic gorilla", "Abyssal demon", "Cave horror", "Lizardman shaman"))
			assertTrue(name, filter.allows(name));
	}

	@Test
	public void collectionLogItemFillsAUniqueTagGap()
	{
		DropTableService data = mock(DropTableService.class);
		when(data.table("test creature")).thenReturn(CompletableFuture.completedFuture(Arrays.asList(new DropEntry("Special item", false, 1, .001, false))));
		when(data.uniqueNames(anyString())).thenReturn(Collections.emptyList());
		when(data.itemId("special item")).thenReturn(123);
		when(catalog.containsItem(123)).thenReturn(true);
		assertTrue(new NpcSpinFilter(config, data, catalog).allows("Test creature"));
	}

	@Test
	public void rdtAndCluesDoNotQualifyUntilSharedDropsAreEnabled()
	{
		DropTableService data = mock(DropTableService.class);
		when(data.table("ordinary creature")).thenReturn(CompletableFuture.completedFuture(Arrays.asList(
			new DropEntry("Clue scroll (hard)", false, 1, .01, false), new DropEntry("Fancy shared item", false, 1, .001, true))));
		when(data.uniqueNames(anyString())).thenReturn(Collections.emptyList());
		when(data.itemId(anyString())).thenReturn(123);
		when(catalog.containsItem(123)).thenReturn(true);
		NpcSpinFilter f = new NpcSpinFilter(config, data, catalog);
		assertFalse(f.allows("Ordinary creature"));
		when(config.includeSharedDrops()).thenReturn(true);
		assertTrue(f.allows("Ordinary creature"));
	}

	@Test
	public void trackedBulkResourceDoesNotQualifyAsAUnique()
	{
		DropTableService data = mock(DropTableService.class);
		when(data.table("resource creature")).thenReturn(CompletableFuture.completedFuture(Arrays.asList(new DropEntry("Ancient essence", false, 1, .5, false))));
		when(data.uniqueNames(anyString())).thenReturn(Arrays.asList("Ancient essence"));
		when(data.itemId(anyString())).thenReturn(123);
		when(catalog.containsItem(123)).thenReturn(true);
		assertFalse(new NpcSpinFilter(config, data, catalog).allows("Resource creature"));
	}

	@Test
	public void customIncludesSupportCaseWhitespaceTagsAndWildcards()
	{
		when(config.alwaysIncludeNpcs()).thenReturn("  MONKEY, Revenant*,  ");
		assertTrue(filter.allows("<col=ffff00>Monkey</col>"));
		assertTrue(filter.allows("Revenant dragon"));
		assertFalse(filter.allows("Chicken"));
		when(config.alwaysIncludeNpcs()).thenReturn("");
		assertFalse(filter.allows("Monkey"));
	}

	@Test
	public void customListModeExcludesEvenBossesUnlessListed()
	{
		when(config.spinSources()).thenReturn(SpinSources.CUSTOM_LIST);
		assertFalse(filter.allows("General Graardor"));
		when(config.alwaysIncludeNpcs()).thenReturn("General Graardor");
		assertTrue(filter.allows("General Graardor"));
	}

	@Test
	public void allMonstersModeRestoresUnfilteredSources()
	{
		when(config.spinSources()).thenReturn(SpinSources.ALL_MONSTERS);
		assertTrue(filter.allows("Monkey"));
		assertTrue(filter.allows("Chicken"));
		assertFalse(filter.allows(null));
	}

	@Test
	public void negativeDecisionIsReconsideredWhenCollectionDataArrives()
	{
		DropTableService data = mock(DropTableService.class);
		when(data.table("new creature")).thenReturn(CompletableFuture.completedFuture(Arrays.asList(new DropEntry("New unique", false, 1, .001, false))));
		when(data.uniqueNames(anyString())).thenReturn(Collections.emptyList());
		when(data.itemId("new unique")).thenReturn(123);
		when(catalog.isLoaded()).thenReturn(false);
		NpcSpinFilter f = new NpcSpinFilter(config, data, catalog);
		assertFalse(f.allows("New creature"));
		when(catalog.containsItem(123)).thenReturn(true);
		when(catalog.isLoaded()).thenReturn(true);
		assertTrue(f.allows("New creature"));
	}
}
