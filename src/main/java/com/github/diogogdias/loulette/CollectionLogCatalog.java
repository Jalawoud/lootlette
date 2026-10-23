package com.github.diogogdias.loulette;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;

/** Reads collection definitions, not the player's obtained items. No log UI or network connection is needed. */
@Singleton
@Slf4j
class CollectionLogCatalog
{
	// Cache structure used by collection_draw_list / collection_draw_log and the Collection Log plugin.
	private static final int[] TAB_STRUCTS = {471, 472, 473, 474, 475};
	private static final int TAB_PAGES_ENUM = 683;
	private static final int PAGE_NAME = 689;
	private static final int PAGE_ITEMS_ENUM = 690;

	private final Client client;
	private volatile Set<Integer> items = Collections.emptySet();
	private volatile Set<String> bosses = Collections.emptySet();
	private volatile boolean loaded;

	@Inject
	CollectionLogCatalog(Client client) { this.client = client; }

	boolean isLoaded() { return loaded; }
	boolean containsItem(int id) { return id > 0 && items.contains(id); }
	boolean containsBoss(String name) { return bosses.contains(NpcSpinFilter.normalise(name)); }

	/** Called on the client thread after login. Incomplete cache reads are retried, never committed as empty data. */
	void load()
	{
		if (loaded) return;
		Set<Integer> nextItems = new HashSet<>();
		Set<String> nextBosses = new HashSet<>();
		try
		{
			for (int tabId : TAB_STRUCTS)
			{
				StructComposition tab = client.getStructComposition(tabId);
				if (tab == null) return;
				int pagesId = tab.getIntValue(TAB_PAGES_ENUM);
				if (pagesId <= 0) return;
				EnumComposition pages = client.getEnum(pagesId);
				if (pages == null || pages.getIntVals() == null || pages.getIntVals().length == 0) return;
				for (int pageId : pages.getIntVals())
				{
					StructComposition page = client.getStructComposition(pageId);
					if (page == null) return;
					int itemsId = page.getIntValue(PAGE_ITEMS_ENUM);
					if (itemsId <= 0) return;
					EnumComposition itemEnum = client.getEnum(itemsId);
					if (itemEnum == null || itemEnum.getIntVals() == null) return;
					for (int itemId : itemEnum.getIntVals()) if (itemId > 0) nextItems.add(itemId);
					if (tabId == TAB_STRUCTS[0]) nextBosses.add(NpcSpinFilter.normalise(page.getStringValue(PAGE_NAME)));
				}
			}
		}
		catch (RuntimeException ex)
		{
			log.debug("Collection-log definitions not available yet", ex);
			return;
		}
		if (nextItems.isEmpty()) return;
		items = Collections.unmodifiableSet(nextItems);
		bosses = Collections.unmodifiableSet(nextBosses);
		loaded = true;
		log.debug("Loaded {} collection-log items and {} boss pages for source filtering", items.size(), bosses.size());
	}
}
