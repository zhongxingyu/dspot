package eu.stamp_project.dspot.common.automaticbuilder.gradle;

/**
 * Created by Daniele Gagliardi
 * daniele.gagliardi@eng.it
 * on 21/07/17.
 */

public class GradlePitTaskAndOptions {

    public static final String OPT_WITH_HISTORY = "enableDefaultIncrementalAnalysis = ";

    public static final String OPT_VALUE_REPORT_DIR = "reportDir = 'build/pit-reports'";

    public static final String OPT_TARGET_CLASSES = "targetClasses = ";

    public static final String OPT_VALUE_FORMAT = "outputFormats = ['CSV','HTML','XML']";

    public static final String OPT_TARGET_TESTS = "targetTests = ";

    public static final String PROPERTY_ADDITIONAL_CP_ELEMENTS = "additionalMutableCodePaths = "; // TODO

    public static final String PROPERTY_EXCLUDED_CLASSES = "excludedClasses = "; // TODO

    //Apparently Unsupported by PIT plugin
    public static final String OPT_ADDITIONAL_CP_ELEMENTS = "classPath = ";

    public static final String OPT_EXCLUDED_CLASSES = "excludedClasses = ";

    public static final String OPT_MUTATION_ENGINE = "mutationEngine = 'descartes'";

    public static final String CMD_PIT_MUTATION_COVERAGE = "pitest";

    public static final String[] ARGUMENTS_SKIP_COMPILE_TEST = new String[]{"-x", "compileTest"};

    public static final String PROPERTY_VALUE_TIMEOUT = "timeoutConstInMillis";

    public static final String PROPERTY_VALUE_JVM_ARGS = "jvmArgs";

    public static final String OPT_MUTATORS = "mutators = ";

    public static final String VALUE_MUTATORS_ALL = "['ALL']";

    public static final String OUTPUT_DIRECTORY_PIT = "build/pit-reports/";

    public static final String OPT_PIT_VERSION = "pitestVersion = ";
}
