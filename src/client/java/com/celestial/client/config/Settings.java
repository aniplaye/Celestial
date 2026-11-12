package com.celestial.client.config;

/**
 * User settings. Persisted to config/celestial/celestial.json via {@link Store}.
 * Everything applies live, no restart needed.
 */
public class Settings {
	public double guiScale = 1.0;
	public double opacity = 0.92;
	public int blurLayers = 2;
	public boolean animations = true;
	public boolean showFps = true;
	public boolean showPing = true;
	public String theme = "pink";
	public int customColor = 0xFF69B4;
	public String activeProfile = "Default";
}
