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
import static java.nio.file.StandardCopyOption.*;



import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import org.ejml.simple.SimpleMatrix;

/**
 *  Generation of CMP HTS observation file
 *
 *  @author <a href="mailto:slemaguer@coli.uni-saarland.de">Sébastien Le Maguer</a>
 */
public class GenerateCMP
{
    private int samplerate;
    private int frameshift;
    private ArrayList<Hashtable<String, Object>> stream_infos;

    public ArrayList<Hashtable<String, Object>> getStreamInfos() {
        return stream_infos;
    }

    public void setStreamInfos(ArrayList<Hashtable<String, Object>> stream_infos) {
        this.stream_infos = stream_infos;
    }

    public int getFrameshift() {
        return frameshift;
    }

    public void setFrameshift(int frameshift) {
        this.frameshift = frameshift;
    }

    public int getSamplerate() {
        return samplerate;
    }

    public void setSamplerate(int samplerate) {
        this.samplerate = samplerate;
    }

    public GenerateCMP(int samplerate, int frameshift, ArrayList<Hashtable<String, Object>> stream_infos)
        throws Exception {
        setSamplerate(samplerate);
        setFrameshift(frameshift);
        setStreamInfos(stream_infos);
    }


    /**
     *
     */
    public static byte[] merge(ArrayList<double[][]> O_list) {
        int nb_streams = O_list.size();
        int T = O_list.get(0).length;

        // Compute buffer size
        int size = 0;
        for (int i=0; i<nb_streams; i++) {
            size += O_list.get(i)[0].length;
            if (O_list.get(i).length < T)
                T = O_list.get(i).length; // FIXME: warning message
        }
        size = size * T * Float.BYTES;

        // Fill bytebuffer
        ByteBuffer data = ByteBuffer.allocate(size);
        data.order(ByteOrder.LITTLE_ENDIAN);
        for (int t=0; t<T; t++) {
            for (int s=0; s<nb_streams; s++) {
                double[] tmp = O_list.get(s)[t];
                for (int d=0; d<tmp.length; d++) {
                    data.putFloat((float) tmp[d]);
                }
            }
        }

        // Return byte array
        return data.array();
    }

    /**
     *  Add an HTK header to the input cmp file
     *
     *   @param input_file_name the input cmp file path
     *   @param output_file_name the output cmp file with header path
     *   @param frameshift the used frameshift in HTK format
     *   @param framesize the number of coefficients for one frame
     *   @param HTK_feature_type the HTK feature type information
     */
    public static byte[] computeHTKHeader(ArrayList<double[][]> O_list, int frameshift, short HTK_feature_type)
        throws IOException
    {

        // Prepare buffer
        ByteBuffer buffer = ByteBuffer.allocate((Integer.BYTES + Short.BYTES) * 2);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Get needed information
        int nb_frames = O_list.get(0).length;
        short framesize = 0;
        for (double[][] cur_o: O_list) {
            framesize += (short) cur_o[0].length;
            if (cur_o.length < nb_frames)
                nb_frames = cur_o.length;
        }

        framesize= (short) (Float.BYTES * framesize);

        // Generate header
        buffer.putInt(nb_frames);
        buffer.putInt(frameshift);

        buffer.putShort(framesize);
        buffer.putShort(HTK_feature_type);

        // Return generated header
        return buffer.array();
    }



    private double[][] loadFile(File input_file, int dim) throws FileNotFoundException, IOException {
        Path p_input = input_file.toPath();
        byte[] data_bytes = Files.readAllBytes(p_input);
        ByteBuffer buffer = ByteBuffer.wrap(data_bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Compute nb frames
        int T = data_bytes.length / (dim * Float.BYTES);

        // Generate vector C
        double[][] input_data = new double[T][dim];
        for (int i=0; i<T; i++) {
            for (int j=0; j<dim; j++)  {
                input_data[i][j] = buffer.getFloat();
                if (Double.isNaN(input_data[i][j])) {
                    throw new IOException(input_file.toString() + " contains nan values! ");
                }
            }
        }

        return input_data;
    }

    /**
     * Generateion method => generate the cmp file
     *
     *   @param basename: the basename of the utterance file analyzed. Each specific filename is
     *   built using the kind officients (extensio)
     */
    public void generate(ArrayList<File> input_files, File output_cmp_file) throws Exception
    {
        // Compute windowing
        int i=0;
        ArrayList<double[][]> O_list = new ArrayList<double[][]>();
        for (File cur_file : input_files) {

            // Load data
            double[][] input_data = loadFile(cur_file,
                                             ((Integer) stream_infos.get(i).get("order")).intValue()+1);

            // Apply window
            double[][] O = WindowUtils.applyWindows(input_data,
                                                    (ArrayList<double[]>) stream_infos.get(i).get("windows"),
                                                    (Boolean) stream_infos.get(i).get("is_msd"));
            O_list.add(O);

            // Next stream
            i++;
        }

        // Get header information
        byte[] header = computeHTKHeader(O_list, frameshift, (short) 9);

        // Generate data
        byte[] data = merge(O_list);

        // Generate full byte array
        byte[] output_data = new byte[header.length + data.length];
        System.arraycopy(header, 0, output_data, 0, header.length);
        System.arraycopy(data, 0, output_data, header.length, data.length);

        // Save the cmp file
        Files.write(output_cmp_file.toPath(), output_data);
    }
}
