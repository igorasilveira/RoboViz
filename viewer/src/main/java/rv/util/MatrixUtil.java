package rv.util;

import java.util.Arrays;

public class MatrixUtil
{
	public static void print2D(float mat[][])
	{
		// Loop through all rows
		for (float[] row : mat)

			// converting each row as string
			// and then printing in a separate line
			System.out.println(Arrays.toString(row));
	}
}
