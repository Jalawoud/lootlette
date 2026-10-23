package com.github.diogogdias.loulette;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.util.Text;

/** Source eligibility: checks what CAN drop, never what the last kill actually dropped. */
@Singleton
class NpcSpinFilter
{
	// Bulk resources are tracked on some log pages, but are not a reason to spin for a source.
	private static final Set<String> BULK_ITEMS = new HashSet<>(Arrays.asList(
		"coins", "zulrah's scales", "ancient essence", "frozen cache", "revenant ether", "numulite", "crystal shard"
	));
	// Shared curiosities must not turn almost every ordinary monster into a 'unique' source.
	private static final Set<String> SHARED_ITEMS = new HashSet<>(Arrays.asList(
		"shield left half", "dragon spear", "dragon med helm", "uncut dragonstone", "dragonstone",
		"long bone", "curved bone"
	));
	// Useful while cache definitions are loading; these sources are missing from the existing wiki-unique tags.
	private static final Set<String> FALLBACK_LOG_ITEMS = new HashSet<>(Arrays.asList(
		"abyssal whip", "abyssal head", "black mask", "black mask (10)", "dragon warhammer"
	));

	private final LouletteConfig config;
	private final DropTableService tables;
	private final CollectionLogCatalog catalog;
	private final Map<String, Boolean> eligibility = new ConcurrentHashMap<>();
	private String includeText;
	private List<Pattern> includes = Collections.emptyList();

	@Inject
	NpcSpinFilter(LouletteConfig config, DropTableService tables, CollectionLogCatalog catalog)
	{
		this.config = config;
		this.tables = tables;
		this.catalog = catalog;
	}

	static String normalise(String name)
	{
		return name == null ? "" : Text.removeTags(name).replace('\u2019', '\'').trim().toLowerCase(Locale.ROOT);
	}

	boolean allows(String name)
	{
		String key = normalise(name);
		if (key.isEmpty() || "null".equals(key)) return false;
		SpinSources mode = config.spinSources();
		if (mode == SpinSources.ALL_MONSTERS || explicitlyIncluded(key)) return true;
		if (mode == SpinSources.CUSTOM_LIST) return false;
		boolean sharedDrops = config.includeSharedDrops();
		String cacheKey = key + ":" + sharedDrops;
		Boolean cached = eligibility.get(cacheKey);
		if (cached != null) return cached;
		CompletableFuture<List<DropEntry>> future = tables.table(key);
		List<DropEntry> entries = future.getNow(null);
		boolean allowed = meaningful(key, entries, sharedDrops);
		// A negative result must be reconsidered when cache definitions / live drop data finish loading.
		if (allowed || (catalog.isLoaded() && entries != null)) eligibility.put(cacheKey, allowed);
		return allowed;
	}

	private boolean meaningful(String name, List<DropEntry> entries, boolean sharedDrops)
	{
		if (catalog.containsBoss(name)) return true;
		Set<String> uniqueNames = new HashSet<>();
		for (String item : tables.uniqueNames(name)) uniqueNames.add(normalise(item));
		if (entries == null) return false;
		for (DropEntry entry : entries)
		{
			String item = normalise(entry.getName());
			if (BULK_ITEMS.contains(item)) continue;
			boolean shared = entry.isRdt() || SHARED_ITEMS.contains(item) || item.startsWith("clue scroll") || item.startsWith("clue bottle")
				|| item.startsWith("clue nest") || item.startsWith("clue geode");
			if (shared)
			{
				if (sharedDrops) return true;
				continue;
			}
			if (uniqueNames.contains(item) || tables.isPet(item) || FALLBACK_LOG_ITEMS.contains(item)
				|| catalog.containsItem(tables.itemId(item))) return true;
		}
		return false;
	}

	synchronized boolean explicitlyIncluded(String name)
	{
		String text = config.alwaysIncludeNpcs();
		if (text == null) text = "";
		if (!text.equals(includeText))
		{
			includeText = text;
			java.util.ArrayList<Pattern> next = new java.util.ArrayList<>();
			for (String token : text.split(","))
			{
				String key = normalise(token);
				if (!key.isEmpty()) next.add(Pattern.compile(Pattern.quote(key).replace("*", "\\E.*\\Q")));
			}
			includes = next;
		}
		String key = normalise(name);
		for (Pattern pattern : includes) if (pattern.matcher(key).matches()) return true;
		return false;
	}

	void clear() { eligibility.clear(); }
}
