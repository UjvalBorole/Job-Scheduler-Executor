package com.executor1.utility;

import com.executor1.config.EnvMapProvider;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class JenkinsfileExecutor {

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Map<String, String> envMap = EnvMapProvider.getEnvMap();
    private final ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    // ============================
    // Public method to parse & run Jenkinsfile
    // ============================
    public void parseAndRun(String jenkinsfilePath) throws Exception {
        String jobName = Paths.get(jenkinsfilePath).getFileName().toString().replaceAll("\\..*$", "");
        Map<String, String> pipelineEnv = parseEnvironment(jenkinsfilePath);
        Map<String, String> toolsEnv = parseTools(jenkinsfilePath);
        pipelineEnv.putAll(toolsEnv);

        // Read commands from sh/bat
        String content = new String(Files.readAllBytes(Paths.get(jenkinsfilePath)));
        List<String> commands = content.lines()
                .filter(line -> line.trim().startsWith("sh ") || line.trim().startsWith("bat "))
                .map(line -> line.trim().substring(3).replaceAll("[\"']", "").trim())
                .collect(Collectors.toList());

        // Parse matrix axes dynamically
        List<Map<String, String>> matrixCombinations = parseMatrixAxes(jenkinsfilePath);
        if (matrixCombinations.isEmpty()) {
            matrixCombinations.add(pipelineEnv); // default
        }

        // Execute matrix combinations in parallel
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Map<String, String> combo : matrixCombinations) {
            Map<String, String> finalEnv = new HashMap<>(pipelineEnv);
            finalEnv.putAll(combo);
            futures.add(executorService.submit(() -> runMatrixCombination(jobName, commands, finalEnv)));
        }

        // Wait for completion
        for (Future<Boolean> future : futures) {
            if (!future.get()) {
                System.out.println("Pipeline stopped due to failure in one of the matrix combinations.");
                break;
            }
        }

        executorService.shutdown();
    }

    // ============================
    // Run commands for a single matrix combination
    // ============================
    private boolean runMatrixCombination(String jobName, List<String> commands, Map<String, String> env) {
        String comboName = env.entrySet().stream()
                .filter(e -> !envMap.containsKey(e.getKey()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("_"));

        comboName = sanitizeFileName(comboName); // ✅ sanitize for Windows

        String logFile = "logs/" + jobName + "_" + comboName + "_" + dtf.format(LocalDateTime.now()) + ".log";
        new File("logs").mkdirs();

        try {
            int stageNum = 1;
            for (String cmd : commands) {
                // Replace environment variables
                for (Map.Entry<String, String> entry : envMap.entrySet()) {
                    cmd = cmd.replace("${" + entry.getKey() + "}", entry.getValue());
                }
                for (Map.Entry<String, String> entry : env.entrySet()) {
                    cmd = cmd.replace("${" + entry.getKey() + "}", entry.getValue());
                }

                String stageName = "Stage-" + stageNum + "_" + comboName;
                logStageStart(stageName, logFile);

                boolean success = runCommand(stageName, cmd, logFile);
                if (!success) {
                    logError("Execution stopped due to error in stage: " + stageName, logFile);
                    return false;
                }
                stageNum++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    // ============================
    // Sanitize filename for Windows
    // ============================
    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // ============================
    // Parse environment { } block
    // ============================
    private Map<String, String> parseEnvironment(String jenkinsfilePath) throws IOException {
        Map<String, String> pipelineEnv = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(jenkinsfilePath));
        boolean inBlock = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("environment {")) inBlock = true;
            else if (line.startsWith("}")) inBlock = false;
            else if (inBlock && line.contains("=")) {
                String[] parts = line.split("=", 2);
                pipelineEnv.put(parts[0].trim(), parts[1].trim().replaceAll("[\"']", ""));
            }
        }
        return pipelineEnv;
    }

    // ============================
    // Parse tools { } block
    // ============================
    private Map<String, String> parseTools(String jenkinsfilePath) throws IOException {
        Map<String, String> tools = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(jenkinsfilePath));
        boolean inBlock = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("tools {")) inBlock = true;
            else if (line.startsWith("}")) inBlock = false;
            else if (inBlock) {
                String[] parts = line.split("'", 2);
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    String value = parts[1].replaceAll("'", "").trim();
                    tools.put(key.toUpperCase(), value);
                }
            }
        }
        return tools;
    }

    // ============================
    // Parse matrix axes
    // ============================
    private List<Map<String, String>> parseMatrixAxes(String jenkinsfilePath) throws IOException {
        List<Map<String, String>> combinations = new ArrayList<>();
        Map<String, List<String>> axesMap = new LinkedHashMap<>();

        List<String> lines = Files.readAllLines(Paths.get(jenkinsfilePath));
        boolean inMatrix = false, inAxes = false, inAxis = false;
        String currentAxisName = null;

        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("matrix {")) inMatrix = true;
            else if (inMatrix && line.startsWith("axes {")) inAxes = true;
            else if (inAxes && line.startsWith("axis {")) inAxis = true;
            else if (inAxis && line.startsWith("name ")) {
                currentAxisName = line.split("'")[1].trim();
            } else if (inAxis && line.startsWith("values ")) {
                String[] values = line.split("'")[1].split(",");
                axesMap.put(currentAxisName, Arrays.stream(values).map(String::trim).toList());
                inAxis = false;
            } else if (line.startsWith("}")) {
                if (inAxis) inAxis = false;
                else if (inAxes) inAxes = false;
                else if (inMatrix) inMatrix = false;
            }
        }

        // Cartesian product
        combinations = generateCombinations(axesMap);
        return combinations;
    }

    private List<Map<String, String>> generateCombinations(Map<String, List<String>> axesMap) {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(new HashMap<>());

        for (Map.Entry<String, List<String>> entry : axesMap.entrySet()) {
            String axis = entry.getKey();
            List<String> values = entry.getValue();
            List<Map<String, String>> temp = new ArrayList<>();
            for (Map<String, String> combination : result) {
                for (String value : values) {
                    Map<String, String> newCombo = new HashMap<>(combination);
                    newCombo.put(axis, value);
                    temp.add(newCombo);
                }
            }
            result = temp;
        }
        return result;
    }

    // ============================
    // Logging helpers
    // ============================
    private void logStageStart(String stageName, String logFile) throws IOException {
        logToFile("\n==================================================", logFile);
        logToFile("[" + LocalDateTime.now() + "] Starting: " + stageName, logFile);
        logToFile("==================================================", logFile);
    }

    private void logStageEnd(String stageName, int exitCode, String logFile) throws IOException {
        logToFile("--------------------------------------------------", logFile);
        logToFile("[" + LocalDateTime.now() + "] Finished: " + stageName + " (exit code " + exitCode + ")", logFile);
        logToFile("--------------------------------------------------\n", logFile);
    }

    private void logError(String message, String logFile) throws IOException {
        logToFile("ERROR: " + message, logFile);
    }

    private void logToFile(String message, String logFile) throws IOException {
        System.out.println(message);
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(message);
            bw.newLine();
        }
    }

    // ============================
    // Run commands with interpreter detection
    // ============================
    private boolean runCommand(String stageName, String command, String logFile) throws IOException, InterruptedException {
        logToFile("Running command: " + command, logFile);

        ProcessBuilder builder = new ProcessBuilder();
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        String[] parts = command.split("\\s+", 2);
        String executable = parts[0];
        String args = parts.length > 1 ? parts[1] : "";
        String interpreter = envMap.getOrDefault(executable.toUpperCase(), executable);

        if (isWindows) {
            if (interpreter.equalsIgnoreCase("powershell"))
                builder.command("powershell.exe", "-Command", args);
            else if (interpreter.equalsIgnoreCase("cmd"))
                builder.command("cmd.exe", "/c", args);
            else builder.command(interpreter, args);
        } else {
            if (interpreter.equalsIgnoreCase("bash") || interpreter.equalsIgnoreCase("sh"))
                builder.command(interpreter, "-c", args);
            else builder.command(interpreter, args);
        }

        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logToFile("[" + LocalDateTime.now() + "] " + line, logFile);
            }
        }

        int exitCode = process.waitFor();
        logStageEnd(stageName, exitCode, logFile);
        return exitCode == 0;
    }

    // ============================
    // Main for testing
    // ============================
    public static void main(String[] args) {
        try {
            JenkinsfileExecutor executor = new JenkinsfileExecutor();
            executor.parseAndRun("D:/lap/java/dsa/jenkins"); // replace path
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
