package com.executor1.utility;

import com.executor1.config.EnvMapProvider;
import com.executor1.entities2.Payload;
import com.executor1.entities2.RunStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class JenkinsfileExecutor {

    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final Map<String, String> envMap = EnvMapProvider.getEnvMap();
    private static final int DEFAULT_TIMEOUT_MILLIS = 3600000; // 1 hour
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");
    private static final Pattern PARAM_VAR_PATTERN = Pattern.compile("\\$\\{params\\.([^}]+)\\}");
    private static final Pattern DOCKER_AGENT_PATTERN = Pattern.compile("docker\\s*\\{([^}]+?)\\}", Pattern.DOTALL);
    private static final Pattern KUBERNETES_AGENT_PATTERN = Pattern.compile("kubernetes\\s*\\{([^}]+?)\\}", Pattern.DOTALL);

    private final ExecutorService executorService;

    // ============================
    // Advanced Agent Configuration
    // ============================
    private static class AgentConfig {
        String type; // 'docker', 'kubernetes', 'label', 'any'
        String image;
        String label;
        List<String> args = new ArrayList<>();
        Map<String, String> registryCredentials = new HashMap<>();
        Map<String, Object> kubeConfig = new HashMap<>();
    }

    // ============================
    // Parameter Definition
    // ============================
    private static class ParameterDefinition {
        String name;
        String type; // choice, string, boolean, password, file
        String defaultValue;
        List<String> choices = new ArrayList<>();
        String description;
    }

    // ============================
    // Build Trigger
    // ============================
    private static class Trigger {
        String type; // cron, pollSCM, upstream, webhook
        String expression;
        Map<String, Object> config = new HashMap<>();
    }

    // ============================
    // Stage execution context for managing state across stages
    // ============================
    private static class PipelineExecutionContext {
        Map<String, String> globalEnv = new HashMap<>();
        Map<String, String> stageEnv = new HashMap<>();
        Map<String, String> parametersValues = new HashMap<>();
        Map<String, ParameterDefinition> parameters = new HashMap<>();
        Map<String, String> credentials = new HashMap<>();
        List<StageResult> stageResults = new ArrayList<>();
        AtomicBoolean shouldFail = new AtomicBoolean(false);
        String failureStage = null;
        String failureReason = null;
        Map<String, Object> variables = new HashMap<>(); // For storing outputs between stages
        int totalStages = 0;
        int completedStages = 0;
        AgentConfig currentAgent = new AgentConfig();
        LocalDateTime startTime;
        LocalDateTime endTime;
    }

    // Result of stage execution
    private static class StageResult {
        String stageName;
        boolean success;
        int exitCode;
        String output;
        long durationMs;
        LocalDateTime startTime;
        LocalDateTime endTime;

        StageResult(String stageName, boolean success, int exitCode, String output, long durationMs) {
            this.stageName = stageName;
            this.success = success;
            this.exitCode = exitCode;
            this.output = output;
            this.durationMs = durationMs;
            this.startTime = LocalDateTime.now();
            this.endTime = LocalDateTime.now().plusNanos(durationMs * 1_000_000);
        }
    }

    // Inject Spring-managed thread pool
    public JenkinsfileExecutor(@Qualifier("jenkinsExecutor") ExecutorService executorService) {
        this.executorService = executorService;
    }

    // ============================
    // Logging helpers
    // ============================
    private void logToFile(String message, String logFile) throws IOException {
        System.out.println(message);
        try (FileWriter fw = new FileWriter(logFile, true);
             BufferedWriter bw = new BufferedWriter(fw)) {
            bw.write(message);
            bw.newLine();
        }
    }

    // ============================
    // Utility methods
    // ============================
    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes % 60, seconds % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    // ============================
    // ============================
    // Pipeline Stage Model (Enhanced)
    // ============================
    private static class PipelineStage {
        String name;
        String agentOverride;
        List<String> steps;
        Map<String, String> stageEnvironment = new HashMap<>();
        String whenCondition;
        List<List<PipelineStage>> parallelStages;
        Map<String, Object> matrixAxes;
        boolean skipOnFailure = false;

        PipelineStage(String name, List<String> steps) {
            this.name = name;
            this.steps = steps;
        }

        @Override
        public String toString() {
            return "Stage{" + name + ", steps=" + steps.size() + "}";
        }
    }

    // ============================
    // Debug file (for troubleshooting)
    // ============================
    public void debugJenkinsfile(Payload payload) {
        System.out.println("🔍 DEBUG: Jenkinsfile Analysis");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        File f = new File(payload.getPath());
        System.out.println("📁 Path: " + f.getAbsolutePath());
        System.out.println("   ✓ Exists? " + f.exists());
        System.out.println("   ✓ Readable? " + f.canRead());
        System.out.println("   ✓ Is File? " + f.isFile());

        if (f.exists() && f.canRead()) {
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                System.out.println("\n📖 First 10 lines:");
                String line;
                int lineNum = 0;
                while ((line = br.readLine()) != null && lineNum < 10) {
                    System.out.println(String.format("   %3d: %s", ++lineNum, line));
                }
            } catch (Exception e) {
                System.err.println("❌ Error reading file: " + e.getMessage());
            }
        }
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
    }

    // ============================
    // Main execution entry point
    // ============================
    public Payload execute(Payload payload) {
        payload.setErrorMsg(null);
        payload.setStatus(RunStatus.RUNNING);
        payload.setEndTime(null);

        LocalDateTime executionStartTime = LocalDateTime.now();
        String jenkinsfilePath = payload.getPath();
        String jobName = payload.getName();
        String jobId = String.valueOf(payload.getJobId());
        String timestamp = dtf.format(executionStartTime);

        // Setup logging
        File currentDir = new File(System.getProperty("user.dir"));
        String projectRoot = currentDir.getParentFile().getParent();
        String logDir = projectRoot + File.separator + "logs";
        new File(logDir).mkdirs();

        String logFile = logDir + File.separator + jobName + "_" + jobId + "_" + timestamp + ".log";

        try {
            logToFile("================================================================================", logFile);
            logToFile("🚀 JENKINS PIPELINE EXECUTOR - Job: " + jobName + " (ID: " + jobId + ")", logFile);
            logToFile("📋 Jenkinsfile: " + jenkinsfilePath, logFile);
            logToFile("⏰ Started at: " + executionStartTime, logFile);
            logToFile("================================================================================", logFile);

            // Validate Jenkinsfile exists
            Path path = Paths.get(jenkinsfilePath);
            if (!Files.exists(path)) {
                throw new FileNotFoundException("Jenkinsfile not found at: " + path.toAbsolutePath());
            }

            // Parse and execute
            PipelineExecutionContext context = new PipelineExecutionContext();
            parseAndExecutePipeline(jenkinsfilePath, logFile, payload, context);

            // Determine final status
            if (payload.getStatus() != RunStatus.FAILED) {
                payload.setStatus(RunStatus.SUCCESS);
                logToFile("✅ Pipeline execution COMPLETED SUCCESSFULLY", logFile);
            } else {
                logToFile("❌ Pipeline execution FAILED", logFile);
            }

        } catch (FileNotFoundException e) {
            payload.setStatus(RunStatus.FAILED);
            payload.setErrorMsg("File not found: " + e.getMessage());
            try { logToFile("❌ ERROR: " + e.getMessage(), logFile); } catch (IOException ignored) {}
        } catch (Exception e) {
            payload.setStatus(RunStatus.FAILED);
            payload.setErrorMsg("Pipeline execution error: " + e.getMessage());
            try { logToFile("❌ EXCEPTION: " + e.getMessage(), logFile); } catch (IOException ignored) {}
            log.error("Pipeline execution failed", e);
        } finally {
            payload.setEndTime(LocalDateTime.now());
            payload.setModifiedTime(LocalDateTime.now());
            try {
                long durationMs = java.time.temporal.ChronoUnit.MILLIS.between(executionStartTime, LocalDateTime.now());
                logToFile("📊 Total execution time: " + formatDuration(durationMs), logFile);
                logToFile("================================================================================", logFile);
            } catch (IOException ignored) {}
        }

        log.info("Jenkins file Executor completed - Status: {} - Job: {}", payload.getStatus(), payload.getName());
        return payload;
    }

    // ============================
    // Parse and Execute entire Pipeline
    // ============================
    private void parseAndExecutePipeline(String jenkinsfilePath, String logFile, Payload payload,
                                         PipelineExecutionContext context) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(jenkinsfilePath));
        
        context.startTime = LocalDateTime.now();
        
        // Parse pipeline components
        AgentConfig agentConfig = parseAgentConfig(lines, logFile);
        Map<String, ParameterDefinition> parametersMap = parseParameters(lines, logFile);
        Map<String, String> pipelineEnv = parseEnvironmentBlock(lines, logFile);
        Map<String, String> toolsEnv = parseToolsBlock(lines, logFile);
        List<Trigger> triggers = parseTriggers(lines, logFile);
        List<PipelineStage> stages = parseStages(lines, logFile);
        Map<String, String> postActions = parsePostBlock(lines, logFile);

        // Initialize context with all parsed configuration
        context.globalEnv.putAll(pipelineEnv);
        context.globalEnv.putAll(toolsEnv);
        context.globalEnv.putAll(envMap);
        context.parameters = parametersMap;
        context.currentAgent = agentConfig;
        context.totalStages = stages.size();

        logToFile("📦 Parsed Configuration:", logFile);
        logToFile("   - Agent type: " + agentConfig.type, logFile);
        logToFile("   - Parameters: " + parametersMap.size(), logFile);
        logToFile("   - Environment variables: " + pipelineEnv.size(), logFile);
        logToFile("   - Tools: " + toolsEnv.size(), logFile);
        logToFile("   - Triggers: " + triggers.size(), logFile);
        logToFile("   - Stages: " + stages.size(), logFile);
        logToFile("", logFile);

        // Setup parameters with default values
        setupParameters(context, logFile);

        // Execute each stage sequentially
        for (int i = 0; i < stages.size(); i++) {
            PipelineStage stage = stages.get(i);
            
            // Evaluate when condition
            if (stage.whenCondition != null && !evaluateWhenCondition(stage.whenCondition, context)) {
                logToFile("⏭️  Skipping stage: " + stage.name + " (when condition not met)", logFile);
                continue;
            }
            
            if (context.shouldFail.get()) {
                logToFile("⚠️ Pipeline already failed. Skipping stage: " + stage.name, logFile);
                continue;
            }

            // Override agent if stage specifies one
            AgentConfig stageAgent = stage.agentOverride != null && !stage.agentOverride.isEmpty() 
                ? parseAgentFromString(stage.agentOverride, logFile)
                : agentConfig;
            context.currentAgent = stageAgent;

            boolean stageSuccess = executeStage(stage, i + 1, logFile, payload, context);
            context.completedStages++;

            if (!stageSuccess && !stage.skipOnFailure) {
                context.shouldFail.set(true);
                context.failureStage = stage.name;
                payload.setStatus(RunStatus.FAILED);
                payload.setErrorMsg("Stage failed: " + stage.name);
            }
        }

        context.endTime = LocalDateTime.now();

        // Execute post actions
        if (!postActions.isEmpty()) {
            logToFile("\n📮 Executing Post Actions:", logFile);
            executePostActions(postActions, logFile, payload, context);
        }
    }

    // ============================
    // Execute a single stage
    // ============================
    private boolean executeStage(PipelineStage stage, int stageNumber, String logFile, Payload payload,
                                  PipelineExecutionContext context) throws IOException, InterruptedException {
        long stageStartTime = System.currentTimeMillis();
        String agentDisplay = context.currentAgent.type != null ? context.currentAgent.type : "local";
        if ("docker".equals(context.currentAgent.type) && context.currentAgent.image != null) {
            agentDisplay += " [" + context.currentAgent.image + "]";
        }
        
        logToFile("\n┌─────────────────────────────────────────────────────────────", logFile);
        logToFile("│ 🔄 STAGE " + stageNumber + ": " + stage.name + " [" + agentDisplay + "]", logFile);
        logToFile("├─────────────────────────────────────────────────────────────", logFile);

        try {
            // Execute all steps in the stage
            for (String step : stage.steps) {
                String expandedStep = expandVariables(step, context);
                
                // Special handling for echo statements - they're just logging in Jenkinsfile
                if (expandedStep.toLowerCase().startsWith("echo ")) {
                    String msg = expandedStep.substring(5).trim();
                    logToFile("   ▶️  " + msg, logFile);
                    logToFile("   ✓ Completed in 0s", logFile);
                    continue;  // Don't execute as command, just log it
                }
                
                logToFile("   ▶️  Step: " + expandedStep, logFile);

                long stepStartTime = System.currentTimeMillis();
                CommandResult result = executeCommand(expandedStep, logFile, context, stage.name);
                long stepDurationMs = System.currentTimeMillis() - stepStartTime;

                logToFile("   ✓ Completed in " + formatDuration(stepDurationMs), logFile);

                if (result.exitCode != 0) {
                    String errorMsg = "Step failed with exit code " + result.exitCode + ": " + expandedStep;
                    logToFile("   ❌ " + errorMsg, logFile);
                    if (!result.output.isEmpty()) {
                        logToFile("   Output: " + result.output.split("\n")[0], logFile);
                    }
                    
                    long stageDurationMs = System.currentTimeMillis() - stageStartTime;
                    logToFile("├─────────────────────────────────────────────────────────────", logFile);
                    logToFile("│ ❌ STAGE FAILED (" + formatDuration(stageDurationMs) + ")", logFile);
                    logToFile("└─────────────────────────────────────────────────────────────\n", logFile);
                    
                    return false;
                }
            }

            long stageDurationMs = System.currentTimeMillis() - stageStartTime;
            logToFile("├─────────────────────────────────────────────────────────────", logFile);
            logToFile("│ ✅ STAGE PASSED (" + formatDuration(stageDurationMs) + ")", logFile);
            logToFile("└─────────────────────────────────────────────────────────────\n", logFile);
            
            return true;

        } catch (Exception e) {
            long stageDurationMs = System.currentTimeMillis() - stageStartTime;
            logToFile("❌ Exception in stage: " + e.getMessage(), logFile);
            logToFile("├─────────────────────────────────────────────────────────────", logFile);
            logToFile("│ ❌ STAGE FAILED (" + formatDuration(stageDurationMs) + ")", logFile);
            logToFile("└─────────────────────────────────────────────────────────────\n", logFile);
            return false;
        }
    }

    // ============================
    // Execute Post Actions
    // ============================
    private void executePostActions(Map<String, String> postActions, String logFile, Payload payload,
                                     PipelineExecutionContext context) throws IOException, InterruptedException {
        for (Map.Entry<String, String> entry : postActions.entrySet()) {
            String action = entry.getKey();
            String commands = entry.getValue();

            boolean shouldExecute = false;
            if ("always".equals(action)) {
                shouldExecute = true;
            } else if ("success".equals(action) && payload.getStatus() != RunStatus.FAILED) {
                shouldExecute = true;
            } else if ("failure".equals(action) && payload.getStatus() == RunStatus.FAILED) {
                shouldExecute = true;
            }

            if (shouldExecute) {
                logToFile("📮 Running post action: " + action, logFile);
                String expandedCmd = expandVariables(commands, context.globalEnv);
                CommandResult result = executeCommand(expandedCmd, logFile, context, "post-" + action);
                logToFile("   Result: " + (result.exitCode == 0 ? "✅ Success" : "❌ Failed"), logFile);
            }
        }
    }

    // ============================
    // Execute Command (with proper environment and isolation)
    // ============================
    private static class CommandResult {
        int exitCode;
        String output;
        String errorOutput;

        CommandResult(int exitCode, String output, String errorOutput) {
            this.exitCode = exitCode;
            this.output = output;
            this.errorOutput = errorOutput;
        }
    }

    private CommandResult executeCommand(String command, String logFile, PipelineExecutionContext context,
                                        String stageName) throws IOException, InterruptedException {
        // Route command to appropriate executor based on agent type
        String agentType = context.currentAgent.type != null ? context.currentAgent.type : "any";

        switch (agentType) {
            case "docker":
                return executeCommandInDocker(command, logFile, context, stageName);
            case "kubernetes":
                return executeCommandInKubernetes(command, logFile, context, stageName);
            default:
                return executeCommandLocally(command, logFile, context, stageName);
        }
    }

    private CommandResult executeCommandLocally(String command, String logFile, PipelineExecutionContext context,
                                              String stageName) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder();
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        // ✅ Jenkins-equivalent execution - exactly like Jenkins does it
        if (isWindows) {
            // For Windows, use cmd.exe with proper Unicode support
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("/bin/bash", "-c", command);
        }

        // Set up environment
        Map<String, String> env = builder.environment();
        env.putAll(context.globalEnv);

        // Set working directory
        builder.directory(new File(System.getProperty("user.dir")));
        builder.redirectErrorStream(false);

        // For Windows, ensure UTF-8 encoding is available
        if (isWindows) {
            env.put("CHCP", "65001");  // UTF-8 code page for Windows
        }

        return executeProcess(builder, logFile, stageName);
    }

    private CommandResult executeCommandInDocker(String command, String logFile, PipelineExecutionContext context,
                                                String stageName) throws IOException, InterruptedException {
        logToFile("   🐳 Executing in Docker [", logFile);
        
        String image = context.currentAgent.image != null ? context.currentAgent.image : "alpine:latest";
        logToFile("      Image: " + image, logFile);
        logToFile("      Command: " + command, logFile);
        
        // Check if Docker is available
        try {
            ProcessBuilder checkDocker = new ProcessBuilder("docker", "--version");
            Process dockerCheck = checkDocker.start();
            int exitCode = dockerCheck.waitFor();
            if (exitCode != 0) {
                logToFile("   ⚠️  Docker not available - falling back to local execution", logFile);
                return executeCommandLocally(command, logFile, context, stageName);
            }
        } catch (Exception e) {
            logToFile("   ⚠️  Docker check failed: " + e.getMessage() + " - falling back to local", logFile);
            return executeCommandLocally(command, logFile, context, stageName);
        }
        
        // Build docker run command
        List<String> dockerArgs = new ArrayList<>();
        dockerArgs.add("docker");
        dockerArgs.add("run");
        dockerArgs.add("--rm");
        
        // Add any custom args from agent config
        if (context.currentAgent.args != null && !context.currentAgent.args.isEmpty()) {
            dockerArgs.addAll(context.currentAgent.args);
        }
        
        // Add environment variables
        for (Map.Entry<String, String> entry : context.globalEnv.entrySet()) {
            dockerArgs.add("-e");
            dockerArgs.add(entry.getKey() + "=" + entry.getValue());
        }
        
        // Add image
        dockerArgs.add(image);
        
        // Determine shell based on image OS
        String shell = image.toLowerCase().contains("windows") ? "cmd.exe /c" : "/bin/sh -c";
        dockerArgs.add(shell);
        dockerArgs.add(command);
        
        logToFile("      Registry: " + (context.currentAgent.registryCredentials.isEmpty() ? "default" : "custom"), logFile);
        logToFile("   ]", logFile);
        
        ProcessBuilder builder = new ProcessBuilder(dockerArgs);
        builder.directory(new File(System.getProperty("user.dir")));
        Map<String, String> env = builder.environment();
        env.putAll(context.globalEnv);
        
        return executeProcess(builder, logFile, stageName);
    }

    private CommandResult executeCommandInKubernetes(String command, String logFile, PipelineExecutionContext context,
                                                    String stageName) throws IOException, InterruptedException {
        logToFile("   ☸️  Executing in Kubernetes [", logFile);
        
        // Check if kubectl is available
        try {
            ProcessBuilder checkKubectl = new ProcessBuilder("kubectl", "version", "--short");
            Process kubeCheck = checkKubectl.start();
            int exitCode = kubeCheck.waitFor();
            if (exitCode != 0) {
                logToFile("   ⚠️  kubectl not available - falling back to local execution", logFile);
                return executeCommandLocally(command, logFile, context, stageName);
            }
        } catch (Exception e) {
            logToFile("   ⚠️  kubectl check failed: " + e.getMessage() + " - falling back to local", logFile);
            return executeCommandLocally(command, logFile, context, stageName);
        }
        
        String namespace = (String) context.currentAgent.kubeConfig.getOrDefault("namespace", "default");
        String podName = (String) context.currentAgent.kubeConfig.getOrDefault("podName", "executor-pod");
        String container = (String) context.currentAgent.kubeConfig.getOrDefault("container", "executor");
        String image = (String) context.currentAgent.kubeConfig.getOrDefault("image", "alpine:latest");
        
        logToFile("      Namespace: " + namespace, logFile);
        logToFile("      Pod: " + podName, logFile);
        logToFile("      Container: " + container, logFile);
        logToFile("      Image: " + image, logFile);
        logToFile("      Command: " + command, logFile);
        logToFile("   ]", logFile);
        
        // Build kubectl exec command
        List<String> kubectlArgs = new ArrayList<>();
        kubectlArgs.add("kubectl");
        kubectlArgs.add("exec");
        kubectlArgs.add("-n");
        kubectlArgs.add(namespace);
        kubectlArgs.add(podName);
        kubectlArgs.add("-c");
        kubectlArgs.add(container);
        kubectlArgs.add("--");
        kubectlArgs.add("/bin/sh");
        kubectlArgs.add("-c");
        kubectlArgs.add(command);
        
        ProcessBuilder builder = new ProcessBuilder(kubectlArgs);
        builder.directory(new File(System.getProperty("user.dir")));
        Map<String, String> env = builder.environment();
        env.putAll(context.globalEnv);
        
        return executeProcess(builder, logFile, stageName);
    }

    private CommandResult executeProcess(ProcessBuilder builder, String logFile, String stageName) 
                                        throws IOException, InterruptedException {
        // Capture output with UTF-8 encoding
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        
        // Start process
        long startTime = System.currentTimeMillis();
        Process process = builder.start();

        // Use UTF-8 for reading streams (handles emojis and special characters)
        Thread stdoutThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append("\n");
                    System.out.println("[" + stageName + "] " + line);
                }
            } catch (IOException e) {
                log.error("Error reading stdout", e);
            }
        });

        Thread stderrThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                    System.err.println("[" + stageName + "] " + line);
                }
            } catch (IOException e) {
                log.error("Error reading stderr", e);
            }
        });

        stdoutThread.start();
        stderrThread.start();

        // Wait with timeout
        int exitCode = -1;
        boolean completed = process.waitFor(DEFAULT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        
        if (completed) {
            exitCode = process.exitValue();
        } else {
            process.destroyForcibly();
            exitCode = -1;
        }

        // Wait for output threads to finish
        try {
            stdoutThread.join(5000);
            stderrThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long durationMs = System.currentTimeMillis() - startTime;
        
        return new CommandResult(exitCode, stdout.toString(), stderr.toString());
    }

    // ============================
    // Expand Variables in commands
    // ============================
    private String expandVariables(String input, Map<String, String> variables) {
        String result = input;
        
        // Expand environment variables
        Matcher envMatcher = ENV_VAR_PATTERN.matcher(result);
        while (envMatcher.find()) {
            String varName = envMatcher.group(1);
            String varValue = variables.getOrDefault(varName, "");
            result = result.replace("${" + varName + "}", varValue);
        }
        
        return result;
    }

    private String expandVariables(String input, PipelineExecutionContext context) {
        String result = input;
        
        // Expand environment variables
        Matcher envMatcher = ENV_VAR_PATTERN.matcher(result);
        while (envMatcher.find()) {
            String varName = envMatcher.group(1);
            String varValue = context.globalEnv.getOrDefault(varName, "");
            result = result.replace("${" + varName + "}", varValue);
        }
        
        // Expand parameter variables
        Matcher paramMatcher = PARAM_VAR_PATTERN.matcher(result);
        while (paramMatcher.find()) {
            String paramName = paramMatcher.group(1);
            String paramValue = context.parametersValues.getOrDefault(paramName, "");
            result = result.replace("${params." + paramName + "}", paramValue);
        }
        
        return result;
    }

    // ============================
    // Parse Pipeline Blocks
    // ============================
    private Map<String, String> parseEnvironmentBlock(List<String> lines, String logFile) throws IOException {
        Map<String, String> env = new HashMap<>();
        boolean inBlock = false;
        int braceCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.contains("environment {")) {
                inBlock = true;
                braceCount = 1;
                continue;
            }

            if (inBlock) {
                braceCount += (int) trimmed.chars().filter(c -> c == '{').count();
                braceCount -= (int) trimmed.chars().filter(c -> c == '}').count();

                if (braceCount == 0) {
                    inBlock = false;
                    continue;
                }

                if (trimmed.contains("=") && !trimmed.startsWith("//")) {
                    String[] parts = trimmed.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim().replaceAll("[\"']", "");
                        env.put(key, value);
                    }
                }
            }
        }

        return env;
    }

    private Map<String, String> parseToolsBlock(List<String> lines, String logFile) throws IOException {
        Map<String, String> tools = new HashMap<>();
        boolean inBlock = false;
        int braceCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.contains("tools {")) {
                inBlock = true;
                braceCount = 1;
                continue;
            }

            if (inBlock) {
                braceCount += (int) trimmed.chars().filter(c -> c == '{').count();
                braceCount -= (int) trimmed.chars().filter(c -> c == '}').count();

                if (braceCount == 0) {
                    inBlock = false;
                    continue;
                }

                if (trimmed.contains("'")) {
                    String[] parts = trimmed.split("'");
                    if (parts.length >= 2) {
                        String key = parts[0].trim().replaceAll("[:\\s]", "").toUpperCase();
                        String value = parts[1].trim();
                        if (!key.isEmpty() && !value.isEmpty()) {
                            tools.put(key, value);
                        }
                    }
                }
            }
        }

        return tools;
    }

    private List<PipelineStage> parseStages(List<String> lines, String logFile) throws IOException {
        List<PipelineStage> stages = new ArrayList<>();
        boolean inStages = false;
        boolean inStage = false;
        boolean inSteps = false;
        String currentStageName = null;
        List<String> currentSteps = new ArrayList<>();
        String stageWhenCondition = null;
        int braceCount = 0;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.contains("stages {")) {
                inStages = true;
                braceCount = 1;
                continue;
            }

            if (inStages) {
                braceCount += (int) line.chars().filter(c -> c == '{').count();
                braceCount -= (int) line.chars().filter(c -> c == '}').count();

                if (braceCount == 0) {
                    if (inStage && currentStageName != null) {
                        PipelineStage stage = new PipelineStage(currentStageName, new ArrayList<>(currentSteps));
                        stage.whenCondition = stageWhenCondition;
                        stages.add(stage);
                    }
                    inStages = false;
                    continue;
                }

                if (line.startsWith("stage('") || line.startsWith("stage(\"")) {
                    if (inStage && currentStageName != null) {
                        PipelineStage stage = new PipelineStage(currentStageName, new ArrayList<>(currentSteps));
                        stage.whenCondition = stageWhenCondition;
                        stages.add(stage);
                    }
                    currentStageName = line.split("['\"]")[1];
                    inStage = true;
                    currentSteps.clear();
                    stageWhenCondition = null;
                    continue;
                }

                // Capture when condition
                if (inStage && line.contains("when {")) {
                    StringBuilder whenBlock = new StringBuilder();
                    int whenBraces = 1;
                    whenBlock.append(line.substring(line.indexOf("when {") + 6));
                    i++;
                    while (i < lines.size() && whenBraces > 0) {
                        String whenLine = lines.get(i).trim();
                        whenBraces += (int) whenLine.chars().filter(c -> c == '{').count();
                        whenBraces -= (int) whenLine.chars().filter(c -> c == '}').count();
                        if (whenBraces > 0) {
                            whenBlock.append(" ").append(whenLine);
                        }
                        i++;
                    }
                    i--; // Adjust since for loop will increment
                    stageWhenCondition = whenBlock.toString();
                    continue;
                }

                if (inStage && line.contains("steps {")) {
                    inSteps = true;
                    continue;
                }

                if (inSteps && (line.startsWith("sh ") || line.startsWith("bat ") || line.startsWith("echo ") || line.startsWith("script "))) {
                    String cmd = extractCommand(line);
                    if (!cmd.isEmpty()) {
                        currentSteps.add(cmd);
                    }
                }

                if (line.startsWith("}") && inSteps) {
                    inSteps = false;
                }
            }
        }

        return stages;
    }

    private Map<String, String> parsePostBlock(List<String> lines, String logFile) throws IOException {
        Map<String, String> postActions = new HashMap<>();
        boolean inPost = false;
        String currentAction = null;
        StringBuilder actionContent = new StringBuilder();
        int braceCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("post {")) {
                inPost = true;
                braceCount = 1;
                continue;
            }

            if (inPost) {
                braceCount += (int) trimmed.chars().filter(c -> c == '{').count();
                braceCount -= (int) trimmed.chars().filter(c -> c == '}').count();

                if (braceCount == 0) {
                    if (currentAction != null && actionContent.length() > 0) {
                        postActions.put(currentAction, actionContent.toString());
                    }
                    inPost = false;
                    continue;
                }

                if (trimmed.matches("(always|success|failure|unstable)\\s*\\{")) {
                    if (currentAction != null && actionContent.length() > 0) {
                        postActions.put(currentAction, actionContent.toString());
                    }
                    currentAction = trimmed.split("\\s*\\{")[0].trim();
                    actionContent = new StringBuilder();
                } else if (currentAction != null && 
                          (trimmed.startsWith("sh ") || trimmed.startsWith("bat ") || trimmed.startsWith("echo "))) {
                    String cmd = extractCommand(trimmed);
                    if (!cmd.isEmpty()) {
                        actionContent.append(cmd).append(";");
                    }
                }
            }
        }

        return postActions;
    }

    // ============================
    // Advanced Parsing Methods
    // ============================

    private AgentConfig parseAgentConfig(List<String> lines, String logFile) {
        AgentConfig config = new AgentConfig();
        config.type = "any"; // default

        String content = String.join("\n", lines);

        if (content.contains("docker {")) {
            config.type = "docker";
            Matcher matcher = DOCKER_AGENT_PATTERN.matcher(content);
            if (matcher.find()) {
                String dockerBlock = matcher.group(1);
                config.image = extractValue(dockerBlock, "image", "['\"]?");
                // Extract args
                Pattern argsPattern = Pattern.compile("args\\s+['\"]([^'\"]+)['\"]");
                Matcher argsMatcher = argsPattern.matcher(dockerBlock);
                if (argsMatcher.find()) {
                    config.args.add(argsMatcher.group(1));
                }
            }
        } else if (content.contains("kubernetes {")) {
            config.type = "kubernetes";
            Matcher matcher = KUBERNETES_AGENT_PATTERN.matcher(content);
            if (matcher.find()) {
                String kubeBlock = matcher.group(1);
                config.kubeConfig.put("label", extractValue(kubeBlock, "label", "['\"]?"));
                config.kubeConfig.put("defaultContainer", extractValue(kubeBlock, "defaultContainer", "['\"]?"));
                config.kubeConfig.put("yaml", extractValue(kubeBlock, "yaml", "['\"]?"));
            }
        } else if (content.contains("label")) {
            config.type = "label";
            Pattern labelPattern = Pattern.compile("label\\s+['\"]([^'\"]+)['\"]");
            Matcher matcher = labelPattern.matcher(content);
            if (matcher.find()) {
                config.label = matcher.group(1);
            }
        }

        return config;
    }

    private AgentConfig parseAgentFromString(String agentStr, String logFile) {
        AgentConfig config = new AgentConfig();
        config.type = "any";
        
        if (agentStr.contains("docker")) {
            config.type = "docker";
            config.image = extractValue(agentStr, "image", "['\"]?");
        } else if (agentStr.contains("kubernetes")) {
            config.type = "kubernetes";
        }
        
        return config;
    }

    private Map<String, ParameterDefinition> parseParameters(List<String> lines, String logFile) {
        Map<String, ParameterDefinition> params = new HashMap<>();
        String content = String.join("\n", lines);

        Pattern parametersPattern = Pattern.compile("parameters\\s*\\{([^}]+?)\\s*\\}", Pattern.DOTALL);
        Matcher matcher = parametersPattern.matcher(content);

        if (matcher.find()) {
            String paramBlock = matcher.group(1);

            // Parse choice parameters
            Pattern choicePattern = Pattern.compile("choice\\(([^)]+?)\\)", Pattern.DOTALL);
            Matcher choiceMatcher = choicePattern.matcher(paramBlock);
            while (choiceMatcher.find()) {
                ParameterDefinition param = parseChoiceParameter(choiceMatcher.group(1));
                if (param.name != null) {
                    params.put(param.name, param);
                }
            }

            // Parse string parameters
            Pattern stringPattern = Pattern.compile("string\\(([^)]+?)\\)", Pattern.DOTALL);
            Matcher stringMatcher = stringPattern.matcher(paramBlock);
            while (stringMatcher.find()) {
                ParameterDefinition param = parseStringParameter(stringMatcher.group(1));
                if (param.name != null) {
                    params.put(param.name, param);
                }
            }

            // Parse boolean parameters
            Pattern boolPattern = Pattern.compile("booleanParam\\(([^)]+?)\\)", Pattern.DOTALL);
            Matcher boolMatcher = boolPattern.matcher(paramBlock);
            while (boolMatcher.find()) {
                ParameterDefinition param = parseBooleanParameter(boolMatcher.group(1));
                if (param.name != null) {
                    params.put(param.name, param);
                }
            }
        }

        return params;
    }

    private ParameterDefinition parseChoiceParameter(String content) {
        ParameterDefinition param = new ParameterDefinition();
        param.type = "choice";
        param.name = extractValue(content, "name", "['\"]?");
        param.description = extractValue(content, "description", "['\"]?");

        Pattern choicesPattern = Pattern.compile("choices\\s*:\\s*\\[([^\\]]+)\\]");
        Matcher matcher = choicesPattern.matcher(content);
        if (matcher.find()) {
            String choicesStr = matcher.group(1);
            param.choices = Arrays.stream(choicesStr.split(","))
                .map(String::trim)
                .map(s -> s.replaceAll("['\"]|\\s+", ""))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
            if (!param.choices.isEmpty()) {
                param.defaultValue = param.choices.get(0);
            }
        }

        return param;
    }

    private ParameterDefinition parseStringParameter(String content) {
        ParameterDefinition param = new ParameterDefinition();
        param.type = "string";
        param.name = extractValue(content, "name", "['\"]?");
        param.defaultValue = extractValue(content, "defaultValue", "['\"]?");
        param.description = extractValue(content, "description", "['\"]?");
        return param;
    }

    private ParameterDefinition parseBooleanParameter(String content) {
        ParameterDefinition param = new ParameterDefinition();
        param.type = "boolean";
        param.name = extractValue(content, "name", "['\"]?");
        param.description = extractValue(content, "description", "['\"]?");
        String defaultStr = extractValue(content, "defaultValue", "['\"]?");
        param.defaultValue = (defaultStr != null && defaultStr.toLowerCase().equals("true")) ? "true" : "false";
        return param;
    }

    private List<Trigger> parseTriggers(List<String> lines, String logFile) {
        List<Trigger> triggers = new ArrayList<>();
        String content = String.join("\n", lines);

        Pattern triggersPattern = Pattern.compile("triggers\\s*\\{([^}]+?)\\}", Pattern.DOTALL);
        Matcher matcher = triggersPattern.matcher(content);

        if (matcher.find()) {
            String triggersBlock = matcher.group(1);

            // Parse cron trigger
            if (triggersBlock.contains("cron")) {
                Trigger trigger = new Trigger();
                trigger.type = "cron";
                trigger.expression = extractValue(triggersBlock, "cron", "['\"]?");
                triggers.add(trigger);
            }

            // Parse pollSCM trigger
            if (triggersBlock.contains("pollSCM")) {
                Trigger trigger = new Trigger();
                trigger.type = "pollSCM";
                trigger.expression = extractValue(triggersBlock, "pollSCM", "['\"]?");
                triggers.add(trigger);
            }
        }

        return triggers;
    }

    private String extractValue(String content, String key, String quotePattern) {
        Pattern pattern = Pattern.compile(key + "\\s*[:\\(]?\\s*" + quotePattern + "([^'\"\n}]+?)" + quotePattern);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private void setupParameters(PipelineExecutionContext context, String logFile) throws IOException {
        logToFile("🔧 Setting up parameters:", logFile);
        for (Map.Entry<String, ParameterDefinition> entry : context.parameters.entrySet()) {
            String paramName = entry.getKey();
            ParameterDefinition paramDef = entry.getValue();
            String value = paramDef.defaultValue != null ? paramDef.defaultValue : "";
            context.parametersValues.put(paramName, value);
            String displayValue = paramDef.type.equals("password") ? "****" : value;
            logToFile("   - " + paramName + " = " + displayValue, logFile);
        }
    }

    private boolean evaluateWhenCondition(String condition, PipelineExecutionContext context) {
        if (condition == null || condition.isEmpty()) {
            return true;
        }
        
        // Simple condition evaluation
        if (condition.contains("expression")) {
            return true; // For now, treat expressions as true
        }
        if (condition.contains("branch")) {
            return true; // Branch conditions default to true for executor
        }
        
        return true;
    }

    private String extractCommand(String line) {
        String trimmed = line.trim();
        
        // Handle echo statements - these are logging statements, not actual commands
        if (trimmed.startsWith("echo ")) {
            String msg = trimmed.substring(5).trim();
            // Remove outer quotes if present, but preserve internal content
            if ((msg.startsWith("'") && msg.endsWith("'")) || 
                (msg.startsWith("\"") && msg.endsWith("\""))) {
                msg = msg.substring(1, msg.length() - 1);
            }
            // Return as echo command for proper execution
            return "echo " + msg;
        }
        
        // Handle sh/bash commands - preserve full structure
        if (trimmed.startsWith("sh ")) {
            String cmd = trimmed.substring(3).trim();
            // Remove outer quotes only if they're properly paired
            if ((cmd.startsWith("'") && cmd.endsWith("'")) || 
                (cmd.startsWith("\"") && cmd.endsWith("\""))) {
                cmd = cmd.substring(1, cmd.length() - 1);
            }
            return cmd;
        }
        
        // Handle bat/batch commands - preserve full structure
        if (trimmed.startsWith("bat ")) {
            String cmd = trimmed.substring(4).trim();
            // Remove outer quotes only if they're properly paired
            if ((cmd.startsWith("'") && cmd.endsWith("'")) || 
                (cmd.startsWith("\"") && cmd.endsWith("\""))) {
                cmd = cmd.substring(1, cmd.length() - 1);
            }
            return cmd;
        }
        
        return trimmed;
    }




    // ============================
    // Main method for testing
    // ============================
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Jenkins Executor Test\n");

            // Create test executor with fixed thread pool
            ExecutorService testExecutor = Executors.newFixedThreadPool(4);

            JenkinsfileExecutor executor = new JenkinsfileExecutor(testExecutor);

            // Use the simple test file that works on all platforms
            String jenkinsfilePath = "d:/lap/Projects/Job Scheduling and Execution - Copy/examples/Jenkinsfile.docker";
            
            Payload testPayload = Payload.builder()
                    .Id(1L)
                    .jobId(1001L)
                    .name("TestJob")
                    .seqId(10)
                    .status(RunStatus.RUNNING)
                    .startTime(LocalDateTime.now())
                    .executorId("executor-1")
                    .attemptNumber(1)
                    .errorMsg(null)
                    .path(jenkinsfilePath)
                    .build();

            System.out.println("📋 Job Details:");
            System.out.println("   - Job Name: " + testPayload.getName());
            System.out.println("   - Job ID: " + testPayload.getJobId());
            System.out.println("   - Jenkinsfile: " + testPayload.getPath());
            System.out.println();

            // Debug the Jenkinsfile
            executor.debugJenkinsfile(testPayload);

            // Execute the pipeline
            Payload result = executor.execute(testPayload);

            // Print results
            System.out.println("📊 EXECUTION RESULTS");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Status: " + result.getStatus());
            System.out.println("Start: " + result.getStartTime());
            System.out.println("End: " + result.getEndTime());
            if (result.getErrorMsg() != null && !result.getErrorMsg().isEmpty()) {
                System.out.println("Error: " + result.getErrorMsg());
            }
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");

            testExecutor.shutdown();
            testExecutor.awaitTermination(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
