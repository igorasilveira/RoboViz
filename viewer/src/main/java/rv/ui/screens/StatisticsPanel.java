package rv.ui.screens;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays the statistics overlay.
 *
 * @author igorasilveira
 */

public class StatisticsPanel
{
	public enum PanelType {
		EVENTS(0, "events"),
		POSSESSION(0, "possession"),
		HEAT_MAP(1, "heat_map");

		private int index;
		private String name;

		PanelType(int index, String name)
		{
			this.index = index;
			this.name = name;
		}

		public String toString()
		{
			return name;
		}
	}

	private ArrayList<String> titles;
	private ArrayList<List<String>> values;

	public StatisticsPanel(ArrayList<String> titles, ArrayList<List<String>> values)
	{
		this.titles = titles;
		this.values = values;
	}

	public ArrayList<String> getTitles()
	{
		return titles;
	}

	public void setTitles(ArrayList<String> titles)
	{
		this.titles = titles;
	}

	public ArrayList<List<String>> getValues()
	{
		return values;
	}

	public void setValues(ArrayList<List<String>> values)
	{
		this.values = values;
	}
}
