package rv.ui.screens;

/**
 * Displays the statistics overlay.
 *
 * @author igorasilveira
 */

public class StatisticsPanel {
	private String[] titles;
	private String[][] values;

	public StatisticsPanel(String[] titles, String[][] values) {
		this.titles = titles;
		this.values = values;
	}

	public String[] getTitles() {
		return titles;
	}

	public void setTitles(String[] titles) {
		this.titles = titles;
	}

	public String[][] getValues() {
		return values;
	}

	public void setValues(String[][] values) {
		this.values = values;
	}
}
