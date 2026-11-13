package com.celestial.client.module;

/** Module categories shown as filters on the Modules tab. */
public enum Category {
	HUD("HUD"),
	VISUAL("Visual"),
	GAMEPLAY("Gameplay"),
	INTERFACE("Interface");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
