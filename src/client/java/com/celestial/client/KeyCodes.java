package com.celestial.client;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Default key binds, as SDL scancodes (what Type.KEYBOARD expects in 26.3 —
 * e.g. X is 27, not the old GLFW code 88). References Mojang's own KEY_*
 * constants so they can never drift out of sync.
 */
public final class KeyCodes {
	private KeyCodes() {
	}

	public static final int KEY_B = InputConstants.KEY_B;
	public static final int KEY_C = InputConstants.KEY_C;
	public static final int KEY_F = InputConstants.KEY_F;
	public static final int KEY_G = InputConstants.KEY_G;
	public static final int KEY_J = InputConstants.KEY_J;
	public static final int KEY_K = InputConstants.KEY_K;
	public static final int KEY_M = InputConstants.KEY_M;
	public static final int KEY_O = InputConstants.KEY_O;
	public static final int KEY_P = InputConstants.KEY_P;
	public static final int KEY_R = InputConstants.KEY_R;
	public static final int KEY_V = InputConstants.KEY_V;
	public static final int KEY_X = InputConstants.KEY_X;
}
