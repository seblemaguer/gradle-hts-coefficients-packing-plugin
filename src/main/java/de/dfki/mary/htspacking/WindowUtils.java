package de.dfki.mary.htspacking;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.util.Hashtable;
import java.util.Arrays;
import java.util.ArrayList;
import java.io.FileReader;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;


import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

/**
 *
 *
 * @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class WindowUtils
{

    private static final float IGNORE_VALUE = (float) -1.0e+10;

    public static ArrayList<double[]> loadWindows(ArrayList<File> win_files)
        throws FileNotFoundException, IOException
    {
        ArrayList<double[]> windows = new ArrayList<double[]>();
        for (File win_file: win_files) {

            // Read the file
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(new FileInputStream(win_file)));
            String line = reader.readLine();
            reader.close();
            String[] elts = line.split(" ");

            // Extract window elements
            int size = Integer.parseInt(elts[0]);
            if ((size % 2) != 1) {
                throw new IOException("Size need to be odd !");
            }

            double[] win = new double[size];
            for (int f=1; f<=size; f++)
                win[f-1] = Double.parseDouble(elts[f]);
            windows.add(win);

        }

        return windows;
    }

    /**
     *  Compute the equation O = W.C to get the observations
     *
     */
    public static double[][] applyWindows(double[][] input_data, ArrayList<double[]> windows, boolean is_msd)
    {
        int T = input_data.length;
        int dim = input_data[0].length;

        // Allocate vector O
        double[][] output_data = new double[T][dim * windows.size()];

        // Load window and apply it
        int i_win = 0;
        for (double[] win: windows)  {
            int size = win.length;

            // Check boundary flags
            boolean[] check_bound = new boolean[size+1];
            for (int j=0; j<=size; j++)
                check_bound[j] = true;

            for (int j=1; j<=size; j++)
                {
                    if (win[j-1] != 0.0)
                        break;
                    check_bound[j] = false;
                }
            for (int j=size; j>=1; j--)
                {
                    if (win[j-1] != 0.0)
                        break;
                    check_bound[j] = false;
                }

            // Calcul i-th coefficients
            int nlr = (size - 1) / 2;
            for (int t=0; t<T; t++)
                {
                    for (int d=0; d<dim; d++)
                        {
                            // MSD boundary evaluation
                            boolean boundary = false;
                            for (int k=-nlr; k<=nlr; k++)
                                {
                                    if (check_bound[k+nlr+1])
                                        {
                                            int l = 0;
                                            if ((t + k) < 0)
                                                {
                                                    l = 0;
                                                }
                                            else if ((t + k) >= T)
                                                {
                                                    l = T - 1;
                                                }
                                            else
                                                {
                                                    l = t + k;
                                                }

                                            if ((is_msd) && (input_data[l][d] == WindowUtils.IGNORE_VALUE))
                                                boundary = true;
                                        }
                                }

                            // Normal case window
                            if (!boundary)
                                {
                                    output_data[t][dim * i_win + d] = (float) 0.0;

                                    for (int k=-nlr; k<=nlr; k++)
                                        {
                                            if ((t + k) < 0)
                                                {
                                                    output_data[t][dim * i_win + d] += win[k+nlr] * input_data[0][d];
                                                }
                                            else if ((t + k) >= T)
                                                {
                                                    output_data[t][dim * i_win + d] += win[k+nlr] * input_data[T-1][d];
                                                }
                                            else
                                                {
                                                    output_data[t][dim * i_win + d] += win[k+nlr] * input_data[t+k][d];
                                                }
                                        }

                                }
                            // Boundary adaptation
                            else
                                {
                                    output_data[t][dim * i_win + d] = WindowUtils.IGNORE_VALUE;
                                }

                        }
                }

            // Now go the next windows
            i_win++;
        }

        // return
        return output_data;
    }

}
