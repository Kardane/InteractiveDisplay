package com.interactivedisplay.tools;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

public final class SandboxTestRunner {
    private SandboxTestRunner() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("사용법: SandboxTestRunner <test-classes-dir>");
            System.exit(2);
        }

        Path root = Path.of(args[0]);
        Set<String> excludedClasses = Arrays.stream(System.getenv().getOrDefault("SANDBOX_TEST_EXCLUDES", "").split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toSet());
        List<String> classNames = Files.walk(root)
                .filter(path -> path.toString().endsWith(".class"))
                .map(root::relativize)
                .map(path -> path.toString().replace('/', '.').replace('\\', '.'))
                .map(name -> name.substring(0, name.length() - 6))
                .filter(name -> !name.contains("$"))
                .filter(name -> !excludedClasses.contains(name))
                .sorted()
                .collect(Collectors.toList());

        if (!excludedClasses.isEmpty()) {
            System.out.println("[SKIP] " + String.join(", ", excludedClasses));
        }

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(classNames.stream().map(DiscoverySelectors::selectClass).toList())
                .build();

        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);

        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));

        if (!summary.getFailures().isEmpty()) {
            for (TestExecutionSummary.Failure failure : summary.getFailures()) {
                System.out.println("[FAIL] " + failure.getTestIdentifier().getDisplayName());
                failure.getException().printStackTrace(System.out);
            }
        }

        if (summary.getTestsFailedCount() > 0 || summary.getContainersFailedCount() > 0) {
            System.exit(1);
        }
    }
}
