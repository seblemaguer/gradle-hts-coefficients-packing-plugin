package de.dfki.mary.htspacking;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.ArrayList;
import java.nio.file.Files;

/**
 *  Generation of FFO HTS observation file
 *
 *  @author Sébastien Le Maguer
 */
public class GenerateFFO extends GenerateCMP {

    /**
     *  Constructor
     *
     *  @param samplerate the samplerate of the original signal
     *  @param frameshift the frameshift in milliseconds
     *  @param stream_infos the information of the different streams.
     */
    public GenerateFFO(int samplerate, int frameshift, ArrayList<Hashtable<String, Object>> stream_infos) {
        super(samplerate, frameshift, stream_infos);
    }


    /**
     *  Method to generate the FFO file from a list of observation files
     *
     *  @param input_files the list of files containing the observations
     *  @param output_ffo_file the FFO filename
     *  @throws IOException
     */
    @Override
    public void generate(ArrayList<File> input_files, File output_ffo_file) throws IOException {
        // Compute windowing
        int i = 0;
        ArrayList<double[][]> O_list = new ArrayList<double[][]>();
        for (File cur_file : input_files) {

            // Load data
            double[][] input_data = loadFile(cur_file, ((Integer) stream_infos.get(i).get("order")).intValue() + 1);

            // Apply window
            double[][] O = WindowUtils.applyWindows(input_data,
                    (ArrayList<double[]>) stream_infos.get(i).get("windows"), false);
            O_list.add(O);

            // Next stream
            i++;
        }

        // Generate data
        byte[] output_data = merge(O_list);

        // Save the ffo file
        Files.write(output_ffo_file.toPath(), output_data);
    }
}
