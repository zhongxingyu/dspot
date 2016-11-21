package fr.inria.diversify.dspot;

import fr.inria.diversify.buildSystem.DiversifyClassLoader;
import fr.inria.diversify.dspot.amp.*;
import fr.inria.diversify.exp.LogResult;
import fr.inria.diversify.factories.DiversityCompiler;
import fr.inria.diversify.log.branch.Coverage;
import fr.inria.diversify.logger.Logger;
import fr.inria.diversify.runner.InputProgram;
import fr.inria.diversify.testRunner.JunitResult;
import fr.inria.diversify.testRunner.JunitRunner;
import fr.inria.diversify.util.FileUtils;
import fr.inria.diversify.util.Log;
import fr.inria.diversify.util.PrintClassUtils;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User: Simon
 * Date: 03/12/15
 * Time: 13:52
 */
public class Amplification {
    protected DiversifyClassLoader applicationClassLoader;
    protected InputProgram inputProgram;
    protected File logDir;
    protected List<AbstractAmp> amplifiers;
    protected DiversityCompiler compiler;
    protected TestSelector testSelector;
    protected ClassWithLoggerBuilder classWithLoggerBuilder;
    protected Map<Boolean, List<CtMethod>> testsStatus;

    protected static int ampTestCount;

    public Amplification(InputProgram inputProgram, DiversityCompiler compiler, DiversifyClassLoader applicationClassLoader, List<AbstractAmp> amplifiers, File logDir) {
        this.inputProgram = inputProgram;
        this.compiler = compiler;
        this.applicationClassLoader = applicationClassLoader;
        this.amplifiers = amplifiers;
        this.logDir = logDir;
        classWithLoggerBuilder = new ClassWithLoggerBuilder(inputProgram);
        testSelector = new TestSelector(logDir, 10);
    }

    public List<CtMethod> amplification(CtType classTest, int maxIteration) throws IOException, InterruptedException, ClassNotFoundException {
        return amplification(classTest, getAllTest(classTest), maxIteration);
    }

    public List<CtMethod> amplification(CtType classTest, List<CtMethod> methods, int maxIteration) throws IOException, InterruptedException, ClassNotFoundException {
        List<CtMethod> tests = methods.stream()
                .filter(mth -> isTest(mth))
                .collect(Collectors.toList());

        if(tests.isEmpty()) {
            return null;
        }
        testSelector.init();
        CtType classWithLogger = classWithLoggerBuilder.buildClassWithLogger(classTest, tests);
        boolean status = writeAndCompile(classWithLogger);
        if(!status) {
            Log.info("error with Logger in class {}", classTest);
            return null;
        }
        JunitResult result = runTests(classWithLogger, tests);
        testSelector.updateLogInfo();
//        LogResult.addCoverage(testSelector.getCoverage(), tests, true);
        resetAmplifiers(classTest, testSelector.getGlobalCoverage());

        Log.info("amplification of {} ({} test)", classTest.getQualifiedName(), tests.size());

        List<CtMethod> ampTest = new ArrayList<>();
        for(int i = 0; i < tests.size(); i++) {
            CtMethod test = tests.get(i);
            Log.debug("amp {} ({}/{})", test.getSimpleName(), i+1, tests.size());
            testSelector.init();

            classWithLogger = classWithLoggerBuilder.buildClassWithLogger(classTest, tests.get(i));
            writeAndCompile(classWithLogger);

            result = runTest(classWithLogger, test);
            if(result != null
                    && result.getFailures().isEmpty()
                    && !result.getTestRuns().isEmpty()) {
                testSelector.updateLogInfo();

                amplification(classTest, test, maxIteration);

                Set<CtMethod> selectedAmpTests = new HashSet<>();
                selectedAmpTests.addAll(testSelector.selectedAmplifiedTests(testsStatus.get(false)));
                selectedAmpTests.addAll(testSelector.selectedAmplifiedTests(testsStatus.get(true)));
                ampTest.addAll(selectedAmpTests);
                ampTestCount += ampTest.size();
                Log.debug("total amp test: {}, global: {}", ampTest.size(), ampTestCount);
//                LogResult.addCoverage(testSelector.getCoverage(), selectedAmpTests, false);
            }
        }
        return ampTest;
    }

    protected void amplification(CtType originalClass, CtMethod test, int maxIteration) throws IOException, InterruptedException, ClassNotFoundException {
        testsStatus();
        List<CtMethod> newTests = new ArrayList<>();
        Collection<CtMethod> ampTests = new ArrayList<>();
        newTests.add(test);
        ampTests.add(test);

        for (int i = 0; i < maxIteration; i++) {
            Log.debug("iteration {}:", i);

            Collection<CtMethod> testToAmp = testSelector.selectTestToAmp(ampTests, newTests);
            if(testToAmp.isEmpty()) {
                break;
            }
            Log.debug("{} tests selected to be amplified", testToAmp.size());
            newTests = ampTest(testToAmp);
            Log.debug("{} new tests generated", newTests.size());

            newTests = reduce(newTests);

            CtType classWithLogger = classWithLoggerBuilder.buildClassWithLogger(originalClass, newTests);
            boolean status = writeAndCompile(classWithLogger);
            if(!status) {
                break;
            }
            Log.debug("run tests");
            JunitResult result = runTests(classWithLogger, newTests);
            if(result == null) {
                break;
            }
            newTests = filterTest(newTests, result);
            ampTests.addAll(newTests);
            saveTestStatus(newTests, result);
            Log.debug("update coverage info");
            testSelector.updateLogInfo();
        }
    }

    protected List<CtMethod> reduce(List<CtMethod> newTests) {
        Random r = new Random();
        while(newTests.size() > 6000) {
            newTests.remove(r.nextInt(newTests.size()));
        }
        return newTests;
    }

    protected void saveTestStatus(Collection<CtMethod> newTests, JunitResult result) {
        List<String> runTests = result.runTests();
        List<String> failedTests = result.failureTests();
        newTests.stream()
                .filter(test -> runTests.contains(test.getSimpleName()))
                .forEach(test -> {
                    if(failedTests.contains(test.getSimpleName())) {
                        testsStatus.get(false).add(test);
                    } else {
                        testsStatus.get(true).add(test);
                    }
                });
    }

    protected List<CtMethod> filterTest(List<CtMethod> newTests, JunitResult result) {
        List<String> goodTests = result.goodTests();
        return newTests.stream()
                .filter(test -> goodTests.contains(test.getSimpleName()))
                .collect(Collectors.toList());
    }

    protected void testsStatus()  {
        testsStatus = new HashMap<>();
        testsStatus.put(true, new ArrayList<>());
        testsStatus.put(false, new ArrayList<>());
    }

    protected boolean writeAndCompile(CtType classInstru) throws IOException {
        FileUtils.cleanDirectory(compiler.getSourceOutputDirectory());
        FileUtils.cleanDirectory(compiler.getBinaryOutputDirectory());
        try {
            PrintClassUtils.printJavaFile(compiler.getSourceOutputDirectory(), classInstru);
            compiler.compileFileIn(compiler.getSourceOutputDirectory(), false);
            return true;
        } catch (Exception e) {
            Log.warn("error during compilation",e);
            return false;
        }
    }


    protected JunitResult runTest(CtType testClass, CtMethod test) throws ClassNotFoundException {
        List<CtMethod> tests = new ArrayList<>(1);
        tests.add(test);
        return runTests(testClass, tests);
    }

    protected JunitResult runTests(CtType testClass, Collection<CtMethod> tests) throws ClassNotFoundException {
        ClassLoader classLoader = new DiversifyClassLoader(applicationClassLoader, compiler.getBinaryOutputDirectory().getAbsolutePath());
        JunitRunner junitRunner = new JunitRunner(classLoader);

        Logger.reset();
        Logger.setLogDir(new File(logDir.getAbsolutePath()));

        String currentUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", inputProgram.getProgramDir());
        JunitResult result = junitRunner.runTestClass(testClass.getQualifiedName(), tests.stream()
                .map(test -> test.getSimpleName())
                .collect(Collectors.toList()));
        System.setProperty("user.dir", currentUserDir);

        return result;
    }

    protected List<CtMethod> ampTest(Collection<CtMethod> tests) {
        return tests.stream()
                .flatMap(test -> ampTest(test).stream())
                .filter(test -> !test.getBody().getStatements().isEmpty())
                .collect(Collectors.toList());
    }

    protected List<CtMethod> ampTest(CtMethod test) {
        return amplifiers.stream().
                flatMap(amplifier -> amplifier.apply(test).stream())
                .collect(Collectors.toList());
    }

    protected void resetAmplifiers(CtType parentClass, Coverage coverage) {
        amplifiers.stream()
                .forEach(amp -> amp.reset(inputProgram, coverage, parentClass));
    }

    protected List<CtMethod> getAllTest(CtType classTest) {
        Set<CtMethod> mths = classTest.getMethods();
        return mths.stream()
                .filter(mth -> isTest(mth))
                .distinct()
                .collect(Collectors.toList());
    }

    protected boolean isTest(CtMethod candidate) {
        if (candidate.isImplicit()
                || candidate.getVisibility() == null
                || !candidate.getVisibility().equals(ModifierKind.PUBLIC)
                || candidate.getBody() == null
                || candidate.getBody().getStatements().size() == 0) {
            return false;
        }
        try {

        if (candidate.getPosition() != null
                && candidate.getPosition().getFile() != null
                && !candidate.getPosition().getFile().toString().contains(inputProgram.getRelativeTestSourceCodeDir())) {
            return false;
        }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return candidate.getSimpleName().contains("test")
                || candidate.getAnnotations().stream()
                    .map(annotation -> annotation.toString())
                    .anyMatch(annotation -> annotation.startsWith("@org.junit.Test"));
    }
}