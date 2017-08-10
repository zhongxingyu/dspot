package fr.inria.stamp.test.listener;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 30/06/17
 */
public class TestListener extends RunListener {

    private List<Description> runningTests = new ArrayList<>();
    private List<Failure> failingTests = new ArrayList<>();
    private List<Failure> assumptionFailingTests = new ArrayList<>();
    private List<Description> ignoredTests = new ArrayList<>();

    @Override
    public void testFinished(Description description) throws Exception {
        this.runningTests.add(description);
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        this.failingTests.add(failure);
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
        this.assumptionFailingTests.add(failure);
    }

    @Override
    public void testIgnored(Description description) throws Exception {
        this.ignoredTests.add(description);
    }

    public List<Description> getRunningTests() {
        return runningTests;
    }

    public List<Description> getPassingTests() {
        List<Description> descriptionsOfFailingTests =
                this.failingTests.stream()
                        .map(Failure::getDescription)
                        .collect(Collectors.toList());
        List<Description> descriptionsOfAssumptionFailingTests =
                this.assumptionFailingTests.stream()
                        .map(Failure::getDescription)
                        .collect(Collectors.toList());
        return this.runningTests.stream()
                .filter(description -> !descriptionsOfFailingTests.contains(description))
                .filter(description -> !descriptionsOfAssumptionFailingTests.contains(description))
                .collect(Collectors.toList());
    }

    public TestListener aggregate(TestListener that) {
        this.runningTests.addAll(that.runningTests);
        this.failingTests.addAll(that.failingTests);
        this.assumptionFailingTests.addAll(that.assumptionFailingTests);
        this.ignoredTests.addAll(that.ignoredTests);
        return this;
    }

    public List<Failure> getFailingTests() {
        return failingTests;
    }

    public List<Failure> getAssumptionFailingTests() {
        return assumptionFailingTests;
    }

    public List<Description> getIgnoredTests() {
        return ignoredTests;
    }

    public Failure getFailureOf(String methodTestName) {
        return this.failingTests.stream()
                .filter(failure ->
                        methodTestName.equals(failure.getDescription().getMethodName())
                ).findFirst()
                .get();
    }
}