package de.dfki.mary.htspacking;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 *  Generation of CMP HTS observation file
 *
 *  @author Sébastien Le Maguer
 */
public class GenerateCMP
{
    /** The sample rate of the signal */
    protected int samplerate;

    /** The frameshift in milliseconds */
    protected int frameshift;

    /** The list of stream informations */
    protected ArrayList<Hashtable<String, Object>> stream_infos;

    /**
     *  Constructor
     *
     *  @param samplerate the samplerate of the original signal
     *  @param frameshift the frameshift in milliseconds
     *  @param stream_infos the information of the different streams.
     */
    public GenerateCMP(int samplerate, int frameshift, ArrayList<Hashtable<String, Object>> stream_infos)
    {
        setSamplerate(samplerate);
        setFrameshift(frameshift);
        setStreamInfos(stream_infos);
    }


    /**
     *  Function to merge all the observation using a frame basis.
     *
     *  @param O_list the list of observation matrices
     *  @returns the frame based observation matrix in a byte format (ready to be dumped in a file)
     */
    protected static byte[] merge(ArrayList<double[][]> O_list) {
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
     *  @param O_list the matrix of observations
     *  @param frameshift the frameshift in milliseconds
     *  @param HTK_feature_type generally set to 9
     *  @returns the HTK header as a byte array
     *  @throws IOException
     */
    protected static byte[] computeHTKHeader(ArrayList<double[][]> O_list, int frameshift, short HTK_feature_type)
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

    /**
     *  Load the observations from a file into a double matrix
     *
     *  @param input_file the binary file containing the informations store in Float32 format
     *  @param dim the dimension of a vector of observation
     *  @returns a double matrix containing the observations
     *  @throws FileNotFoundException
     *  @throws IOException
     */
    protected double[][] loadFile(File input_file, int dim) throws FileNotFoundException, IOException {
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
     *  Method to generate the CMP file from a list of observation files
     *
     *  @param input_files the list of files containing the observations
     *  @param output_cmp_file the CMP filename
     *  @throws IOException
     */
    public void generate(ArrayList<File> input_files, File output_cmp_file) throws IOException
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


    /***********************************************************************************************
     ** Accessors
     ***********************************************************************************************/

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
}
