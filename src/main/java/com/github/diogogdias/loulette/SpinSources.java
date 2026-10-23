package com.github.diogogdias.loulette;

public enum SpinSources
{
	MEANINGFUL_UNIQUES("Bosses and unique-dropping monsters"),
	ALL_MONSTERS("All monsters"),
	CUSTOM_LIST("Only my include list");

	private final String label;
	SpinSources(String label) { this.label = label; }
	@Override public String toString() { return label; }
}
