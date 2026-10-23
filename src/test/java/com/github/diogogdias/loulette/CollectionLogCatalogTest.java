package com.github.diogogdias.loulette;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.StructComposition;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CollectionLogCatalogTest
{
	private Client client;
	private CollectionLogCatalog catalog;

	@Before
	public void setup()
	{
		client = mock(Client.class);
		for (int tab : new int[]{471, 472, 473, 474, 475})
		{
			StructComposition tabStruct = mock(StructComposition.class);
			when(client.getStructComposition(tab)).thenReturn(tabStruct);
			when(tabStruct.getIntValue(683)).thenReturn(tab + 1000);
			EnumComposition pages = mock(EnumComposition.class);
			when(client.getEnum(tab + 1000)).thenReturn(pages);
			when(pages.getIntVals()).thenReturn(new int[]{tab + 2000});
			StructComposition page = mock(StructComposition.class);
			when(client.getStructComposition(tab + 2000)).thenReturn(page);
			when(page.getStringValue(689)).thenReturn(tab == 471 ? "Example Boss" : "Example other page");
			when(page.getIntValue(690)).thenReturn(tab + 3000);
			EnumComposition items = mock(EnumComposition.class);
			when(client.getEnum(tab + 3000)).thenReturn(items);
			when(items.getIntVals()).thenReturn(new int[]{4151, -1, 0});
		}
		catalog = new CollectionLogCatalog(client);
	}

	@Test
	public void readsDefinitionsWithoutReadingPlayerProgressOrOpeningTheLog()
	{
		catalog.load();
		assertTrue(catalog.isLoaded());
		assertTrue(catalog.containsItem(4151));
		assertFalse(catalog.containsItem(0));
		assertTrue(catalog.containsBoss("EXAMPLE BOSS"));
		assertFalse(catalog.containsBoss("Example other page"));
		verify(client, never()).runScript(anyInt(), any());
	}

	@Test
	public void incompleteCacheCanRetryAndIsNeverStoredAsAnEmptyCatalog()
	{
		StructComposition original = client.getStructComposition(471);
		when(client.getStructComposition(471)).thenReturn(null);
		catalog.load();
		assertFalse(catalog.isLoaded());
		when(client.getStructComposition(471)).thenReturn(original);
		catalog.load();
		assertTrue(catalog.isLoaded());
		assertTrue(catalog.containsItem(4151));
	}
}
