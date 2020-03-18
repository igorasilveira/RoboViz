package rv.ui.screens;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the statistics overlay.
 *
 * @author igorasilveira
 */

public class StatisticsPanel {
	private ArrayList<String> titles;
	private ArrayList<List<String>> values;

	public StatisticsPanel(ArrayList<String> titles, ArrayList<List<String>> values) {
		this.titles = titles;
		this.values = values;
	}

	public ArrayList<String> getTitles() {
		return titles;
	}

	public void setTitles(ArrayList<String> titles) {
		this.titles = titles;
	}

	public ArrayList<List<String>> getValues() {
		return values;
	}

	public void setValues(ArrayList<List<String>> values) {
		this.values = values;
	}
}
