package de.dfki.mary.htspacking.task

// Import utils
import de.dfki.mary.htspacking.WindowUtils;

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*

// Generateion helper class
import de.dfki.mary.htspacking.GenerateFFO

/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateFFOTask extends DefaultTask {
    /** The worker */
    private final WorkerExecutor workerExecutor;

    /** One of the input directory to get the file list*/
    @InputDirectory
    final DirectoryProperty origin_directory = newInputDirectory()

    /** The directory containing the spectrum files */
    @OutputDirectory
    final DirectoryProperty ffo_dir = newOutputDirectory()

    /**
     *  The constructor which defines which worker executor is going to achieve the conversion job
     *
     *  @param workerExecutor the worker executor
     */
    @Inject
    public GenerateFFOTask(WorkerExecutor workerExecutor) {
        super();
        this.workerExecutor = workerExecutor;
    }

    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {
        // FIXME: for now basename = only extension
        for (File orig_file: project.fileTree(origin_directory.get()).include('*.*').collect()) {
            // Get basename
            String basename = orig_file.getName().take(orig_file.getName().lastIndexOf('.'))

            // List all input files
            ArrayList<File> input_files = new ArrayList<File>();
            for (def stream: project.gradle.vb_configuration.models.ffo.streams) {
                input_files.add(new File(stream.coeffDir, basename + "." + stream.kind))
            }

            // Generate output filename
            File ffo_file = new File(ffo_dir.getAsFile().get(), basename + ".ffo");

            // Submit the execution
            workerExecutor.submit(GenerateFFOWorker.class,
                                  new Action<WorkerConfiguration>() {
                    @Override
                    public void execute(WorkerConfiguration config) {
                        config.setIsolationMode(IsolationMode.NONE);
                        config.params(input_files, ffo_file,
                                      project.gradle.vb_configuration);
                    }
                });
        }
    }
}


/**
 *  Worker class which generate spectrum, f0 and aperiodicity using the vocoder World
 *
 */
class GenerateFFOWorker implements Runnable {
    /** The input SP file */
    private final ArrayList<File> input_files;

    /** The generated FFO file */
    private final File ffo_output_file;

    /** The configuration object */
    private final Object configuration;

    /**
     *  The contructor which initialize the worker
     *
     *  @param input_files the input files
     *  @param ffo_output_file the output FFO file
     *  @param configuration the configuration object
     */
    @Inject
    public GenerateFFOWorker(ArrayList<File> input_files, File ffo_output_file, Object configuration) {
	this.input_files = input_files;
	this.ffo_output_file = ffo_output_file;
        this.configuration = configuration
    }

    /**
     *  Run method which achieve the generateion/conversion
     *
     */
    @Override
    public void run() {

        ArrayList<Hashtable<String, Object>> stream_infos = new Hashtable<String, Object>();
        for (def stream: configuration.models.ffo.streams) {
            def cur_stream = new Hashtable<String, Object>();
            cur_stream["order"] = stream.order;
            cur_stream["windows"] = WindowUtils.loadWindows(stream.winfiles);

            stream_infos.add(cur_stream);
        }

        // // First get the dimension of the current stream (static only)
        // int vecsize = ((Long) cur_stream.get("order")).intValue() + 1;

        // Define generateor
        def generator = new GenerateFFO(configuration.signal.samplerate,
                                        configuration.signal.frameshift * 10000,
                                        stream_infos);

        // Run generateion
        generator.generate(input_files, ffo_output_file)
    }
}
