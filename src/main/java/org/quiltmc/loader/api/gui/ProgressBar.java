package org.quiltmc.loader.api.gui;

public interface ProgressBar {

	/** @return 1 by default, or the last value passed in to {@link #setMaximum(double)}. */
	double getMaximum();

	void setMaximum(double value);

	/** @return A value between 0 and {@link #getMaximum()}. */
	double getProgress();

	/** @return A value between 0 and 1. */
	default double getPercent() {
		return getProgress() / getMaximum();
	}

	void advance(double by);

	void setText(QuiltLoaderText text);

	/** Advances by {@link #getMaximum()}
	 *
	 * @param percent A value between 0 and 1. */
	default void advanceRemaining(double percent) {
		double progress = getProgress();
		double max = getMaximum();
		double remaining = max - progress;
		advance(remaining * Math.min(1, Math.max(0, percent)));
	}

	void finish();
}
