package com.executor1.utility;

import com.executor1.config.EnvMapProvider;
import com.executor1.entities2.Payload;
import com.executor1.entities2.RunStatus;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private final ExecutorService executorService;

    // Inject Spring-managed thread pool
    public JenkinsfileExecutor(@Qualifier("jenkinsExecutor") ExecutorService executorService) {
        this.executorService = executorService;
    }

    // ============================
    // Public method to execute Payload
    // ============================
    public Payload execute(Payload payload) {
        String jenkinsfilePath = payload.getPath();
        String jobName = payload.getName();
        String jobId = String.valueOf(payload.getJobId());

        String timestamp = dtf.format(LocalDateTime.now());

        File currentDir = new File(System.getProperty("user.dir"));
        String projectRoot = currentDir.getParentFile().getParent();
//        String logDir = System.getProperty("user.dir") + File.separator + "logs";
        String logDir = projectRoot + File.separator + "logs";
        System.out.println("logDir "+logDir);

        new File(logDir).mkdirs(); // ensure "logs/" exists in project

        String logFile = logDir + File.separator + jobName + "_" + jobId + "_" + timestamp + ".log";


        new File("logs").mkdirs();

        try {
            logToFile("Starting execution for job: " + jobName + " (ID: " + jobId + ")", logFile);
            parseAndRun(jenkinsfilePath, logFile, payload);

            if (payload.getErrorMsg() == null || payload.getErrorMsg().isEmpty()) {
                payload.setStatus(RunStatus.SUCCESS);
                logToFile("Execution completed successfully.", logFile);
            } else {
                payload.setStatus(RunStatus.FAILED);
            }
            payload.setModifiedTime(LocalDateTime.now());

        } catch (Exception e) {
            payload.setStatus(RunStatus.FAILED);
            payload.setErrorMsg("Stage execution failed with exception: " + e.getMessage());
            payload.setModifiedTime(LocalDateTime.now());
            try { logToFile("Execution failed with exception: " + e.getMessage(), logFile); } catch (IOException ignored) {}
        }
    System.out.println("Jenkins file Executor "+payload);
        return payload;
    }

    // ============================
    // Parse & Run Jenkinsfile
    // ============================
    private void parseAndRun(String jenkinsfilePath, String logFile, Payload payload) throws Exception {
        String jobName = Paths.get(jenkinsfilePath).getFileName().toString().replaceAll("\\..*$", "");
        Map<String, String> pipelineEnv = parseEnvironment(jenkinsfilePath);
        Map<String, String> toolsEnv = parseTools(jenkinsfilePath);
        pipelineEnv.putAll(toolsEnv);

        List<String> lines = Files.readAllLines(Paths.get(jenkinsfilePath));
        List<CommandWithLine> commandsWithLines = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.startsWith("sh ") || line.startsWith("bat ")) {
                String cmd = line.substring(3).replaceAll("[\"']", "").trim();
                commandsWithLines.add(new CommandWithLine(cmd, i + 1, line));
            }
        }

        List<Map<String, String>> matrixCombinations = parseMatrixAxes(jenkinsfilePath);
        if (matrixCombinations.isEmpty()) {
            matrixCombinations.add(pipelineEnv);
        }

        List<Future<Boolean>> futures = new ArrayList<>();
        for (Map<String, String> combo : matrixCombinations) {
            Map<String, String> finalEnv = new HashMap<>(pipelineEnv);
            finalEnv.putAll(combo);

            futures.add(executorService.submit(() ->
                    runMatrixCombination(jobName, commandsWithLines, finalEnv, logFile, payload)));
        }

        for (Future<Boolean> future : futures) {
            if (!future.get()) {
                logToFile("Pipeline stopped due to failure in one of the matrix combinations.", logFile);
                break;
            }
        }
    }

    // ============================
    // Run commands for a single matrix combination
    // ============================
    private boolean runMatrixCombination(String jobName, List<CommandWithLine> commandsWithLines,
                                         Map<String, String> env, String logFile, Payload payload) {
        String comboName = env.entrySet().stream()
                .filter(e -> !envMap.containsKey(e.getKey()))
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("_"));
        comboName = sanitizeFileName(comboName);

        try {
            int stageNum = 1;
            for (CommandWithLine cwl : commandsWithLines) {
                String cmd = cwl.command;
                for (Map.Entry<String, String> entry : envMap.entrySet())
                    cmd = cmd.replace("${" + entry.getKey() + "}", entry.getValue());
                for (Map.Entry<String, String> entry : env.entrySet())
                    cmd = cmd.replace("${" + entry.getKey() + "}", entry.getValue());

                String stageName = "Stage-" + stageNum + "_" + comboName;
                logStageStart(stageName, logFile);

                int attemptNumber = payload.getAttemptNumber();
                String errorOutput = runCommand(stageName, cmd, logFile, attemptNumber);

                if (errorOutput != null) {
                    payload.setErrorMsg("Stage: " + stageName + "\nStep: " + cwl.originalLine +
                            "\nAttempt " + attemptNumber + " - " + errorOutput.trim());
                    logError(payload.getErrorMsg(), logFile);
                    return false;
                }
                stageNum++;
            }
        } catch (Exception e) {
            String errorMsg = "Exception in stage execution: " + e.getMessage();
            payload.setErrorMsg(errorMsg);
            try { logError(errorMsg, logFile); } catch (IOException ignored) {}
            return false;
        }
        return true;
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    // ============================
    // Parsing methods
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
            else if (inAxis && line.startsWith("name ")) currentAxisName = line.split("'")[1].trim();
            else if (inAxis && line.startsWith("values ")) {
                String[] values = line.split("'")[1].split(",");
                axesMap.put(currentAxisName, Arrays.stream(values).map(String::trim).toList());
                inAxis = false;
            } else if (line.startsWith("}")) {
                if (inAxis) inAxis = false;
                else if (inAxes) inAxes = false;
                else if (inMatrix) inMatrix = false;
            }
        }
        return generateCombinations(axesMap);
    }

    private List<Map<String, String>> generateCombinations(Map<String, List<String>> axesMap) {
        List<Map<String, String>> result = new ArrayList<>();
        result.add(new HashMap<>());
        for (Map.Entry<String, List<String>> entry : axesMap.entrySet()) {
            String axis = entry.getKey();
            List<String> values = entry.getValue();
            List<Map<String, String>> temp = new ArrayList<>();
            for (Map<String, String> combination : result)
                for (String value : values) {
                    Map<String, String> newCombo = new HashMap<>(combination);
                    newCombo.put(axis, value);
                    temp.add(newCombo);
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
    // Run command with attempt tracking
    // ============================
    private String runCommand(String stageName, String command, String logFile, int attemptNumber)
            throws IOException, InterruptedException {
        logToFile("[" + LocalDateTime.now() + "] Running command (Attempt " + attemptNumber + "): " + command, logFile);

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

        StringBuilder errorOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logToFile("[" + LocalDateTime.now() + "] " + line, logFile);
                if (line.toLowerCase().contains("error")
                        || line.toLowerCase().contains("exception")
                        || line.toLowerCase().contains("traceback")) {
                    errorOutput.append(line).append("\n");
                }
            }
        }

        int exitCode = process.waitFor();
        logStageEnd(stageName, exitCode, logFile);
        return exitCode == 0 ? null : errorOutput.toString().trim();
    }

    // ============================
    // Command with Jenkinsfile line info
    // ============================
    private static class CommandWithLine {
        String command;
        int lineNumber;
        String originalLine;

        CommandWithLine(String command, int lineNumber, String originalLine) {
            this.command = command;
            this.lineNumber = lineNumber;
            this.originalLine = originalLine;
        }
    }
    public static void main(String[] args) {
        try {
            Payload payload = new Payload();
            payload.setPath("D:/lap/java/dsa/jenkins"); // replace with actual Jenkinsfile path
            payload.setName("TestJob");
            payload.setJobId(12345L);
            payload.setAttemptNumber(3);

            // ✅ Manually create a pool for testing (since Spring DI isn't available here)
            ExecutorService testExecutor = Executors.newFixedThreadPool(3);

            JenkinsfileExecutor executor = new JenkinsfileExecutor(testExecutor);
            Payload payload1 = Payload.builder()
                    .Id(1L)
                    .jobId(1001L)
                    .name("SampleJob")
                    .seqId(10)
                    .status(RunStatus.RUNNING)   // assuming RunStatus is an enum
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now().plusHours(2))
                    .modifiedTime(null)
                    .executorId("executor-1")
                    .attemptNumber(1)
                    .errorMsg(null)
                    .path("D:/lap/java/dsa/jenkins")
                    .build();

            Payload result = executor.execute(payload1);

            System.out.println("Run Status: " + result.getStatus());
            System.out.println("Error Msg: " + result.getErrorMsg());
            System.out.println("Modified Time: " + result.getModifiedTime());

            testExecutor.shutdown(); // ✅ shutdown since it's a standalone test
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
