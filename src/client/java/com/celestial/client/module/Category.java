package com.celestial.client.module;

/** Module categories shown as filters on the Modules tab. */
public enum Category {
	RENDER("Render"),
	MOVEMENT("Movement"),
	PLAYER("Player");

	private final String label;

	Category(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}
}
