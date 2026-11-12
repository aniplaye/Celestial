package com.celestial.client.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Accent themes. The whole GUI reads {@link #current} every frame,
 * so switching themes updates the interface immediately.
 */
public class Theme {
	public final String id;
	public final String label;
	public final int accent;
	public final int secondary;

	public static final List<Theme> PRESETS = new ArrayList<>();
	public static Theme current;

	static {
		PRESETS.add(new Theme("pink", "Pink", 0xFF69B4, 0xC94F91));
		PRESETS.add(new Theme("purple", "Purple", 0xA855F7, 0x7E3AB8));
		PRESETS.add(new Theme("blue", "Blue", 0x55AAFF, 0x3A7EC2));
		PRESETS.add(new Theme("red", "Red", 0xFF5555, 0xC23A3A));
		PRESETS.add(new Theme("green", "Green", 0x55FF88, 0x2FA85C));
		current = PRESETS.get(0);
	}

	public Theme(String id, String label, int accent, int secondary) {
		this.id = id;
		this.label = label;
		this.accent = accent;
		this.secondary = secondary;
	}

	public static Theme byId(String id) {
		for (Theme theme : PRESETS) {
			if (theme.id.equalsIgnoreCase(id)) {
				return theme;
			}
		}
		return PRESETS.get(0);
	}

	/** Darker shade of the accent, for gradients and borders. */
	public int deep() {
		int r = (int) (((accent >> 16) & 0xFF) * 0.55);
		int g = (int) (((accent >> 8) & 0xFF) * 0.55);
		int b = (int) ((accent & 0xFF) * 0.55);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}
}
