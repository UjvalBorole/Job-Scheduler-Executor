////
////package com.executor1.service;
////
////import com.executor1.entities2.Payload;
////
////import java.io.*;
////import java.nio.charset.StandardCharsets;
////import java.nio.file.*;
////import java.time.*;
////import java.time.format.DateTimeFormatter;
////import java.util.*;
////import java.util.concurrent.*;
////import java.util.concurrent.atomic.AtomicBoolean;
////import java.util.concurrent.atomic.AtomicReference;
////import java.util.function.Supplier;
////import java.util.regex.Matcher;
////import java.util.regex.Pattern;
////
/////**
//// * Robust, Jenkins-like pipeline executor with detailed logging:
//// * - streaming stdout/stderr to rolling logs
//// * - status.json heartbeat
//// * - per-stage checkpoints & resume
//// * - per-step timeout, retries, cancel
//// * - Windows/Linux shell support
//// * - detailed logs for jobs, stages, steps, retries, cancel, timeouts
//// */
////public class RobustPipelineExecutor {
////
////    // ---------- Public API ----------
////
////    public static final class RunConfig {
////        public long stepTimeoutMinutes = 60;
////        public int  stepMaxRetries     = 1;
////        public long retryBackoffSec    = 10;
////        public long heartbeatSec       = 5;
////        public long maxLogBytes        = 50L * 1024 * 1024;
////        public boolean resumeFromCheckpoints = true;
////    }
////
////    public static final class JobHandle {
////        public final String jobId;
////        public final Path workspace;
////        public final Path runDir;
////        public final Path statusFile;
////        public final Path checkpointFile;
////
////        JobHandle(String jobId, Path workspace, Path runDir, Path status, Path checkpoint) {
////            this.jobId = jobId;
////            this.workspace = workspace;
////            this.runDir = runDir;
////            this.statusFile = status;
////            this.checkpointFile = checkpoint;
////        }
////    }
////
////    public static final class SubmitResult {
////        public final boolean accepted;
////        public final String message;
////        public final JobHandle handle;
////
////        SubmitResult(boolean accepted, String message, JobHandle handle) {
////            this.accepted = accepted;
////            this.message = message;
////            this.handle = handle;
////        }
////    }
////
////    public enum JobState { PENDING, RUNNING, SUCCESS, FAILED, CANCELED }
////
////    // ---------- Implementation ----------
////
////    private final ExecutorService runPool = Executors.newCachedThreadPool();
////    private final Map<String, RunningJob> jobs = new ConcurrentHashMap<>();
////    private final Path rootRuns;
////
////    public RobustPipelineExecutor(Path runsRoot) throws IOException {
////        this.rootRuns = runsRoot;
////        Files.createDirectories(rootRuns);
////    }
////
////    public SubmitResult submit(Payload payload, String pipelineDef, RunConfig cfg) {
////        Objects.requireNonNull(payload, "payload");
////        Objects.requireNonNull(pipelineDef, "pipelineDef");
////        if (payload.getPath() == null || payload.getPath().isBlank()) {
////            return new SubmitResult(false, "Payload path is required", null);
////        }
////
////        String jobId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").format(LocalDateTime.now());
////        Path workspace = Paths.get(payload.getPath()).toAbsolutePath();
////        Path runDir = rootRuns.resolve("job_" + jobId);
////        Path logsDir = runDir.resolve("logs");
////        Path status = runDir.resolve("status.json");
////        Path checkpoint = runDir.resolve("checkpoint.json");
////
////        try {
////            Files.createDirectories(logsDir);
////            Status s = Status.initial(jobId, workspace.toString());
////            s.write(status);
////
////            Pipeline pipeline = Pipeline.parse(pipelineDef);
////            RunningJob rj = new RunningJob(jobId, workspace, runDir, logsDir, status, checkpoint, pipeline, cfg);
////            jobs.put(jobId, rj);
////            runPool.submit(() -> runJob(rj));
////            return new SubmitResult(true, "Accepted", new JobHandle(jobId, workspace, runDir, status, checkpoint));
////        } catch (Exception e) {
////            return new SubmitResult(false, "Failed to create run: " + e.getMessage(), null);
////        }
////    }
////
////    public boolean cancel(String jobId) {
////        RunningJob rj = jobs.get(jobId);
////        if (rj == null) return false;
////        rj.cancelRequested.set(true);
////        Process p = rj.currentProcess.get();
////        if (p != null) {
////            appendLog(rj.logsDir.resolve("job.log"), "[CANCEL] Cancel signal sent to PID=" + p.pid());
////            p.destroy();
////        }
////        return true;
////    }
////
////    public Optional<String> readStatus(String jobId) {
////        RunningJob rj = jobs.get(jobId);
////        if (rj == null) return Optional.empty();
////        try {
////            return Optional.of(Files.readString(rj.statusFile, StandardCharsets.UTF_8));
////        } catch (IOException e) {
////            return Optional.of("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
////        }
////    }
////
////    // ---------- Run Loop ----------
////
////    private void runJob(RunningJob rj) {
////        Status status = Status.initial(rj.jobId, rj.workspace.toString());
////        status.state = JobState.RUNNING;
////        status.startedAt = Instant.now();
////
////        Path jobLog = rj.logsDir.resolve("job.log");
////        appendLog(jobLog, "=== JOB START ===");
////        appendLog(jobLog, "JobId: " + rj.jobId);
////        appendLog(jobLog, "Workspace: " + rj.workspace);
////        appendLog(jobLog, "RunDir: " + rj.runDir);
////        appendLog(jobLog, "OS: " + System.getProperty("os.name") + " | User: " + System.getProperty("user.name"));
////        appendLog(jobLog, "Pipeline stages: " + rj.pipeline.stages.size());
////
////        Heartbeat hb = new Heartbeat(rj.statusFile, rj.cfg.heartbeatSec, () -> status.clone());
////        hb.start();
////        try {
////            long jobStart = System.currentTimeMillis();
////            Set<String> completedStages = rj.cfg.resumeFromCheckpoints ? Checkpoint.load(rj.checkpointFile) : new HashSet<>();
////
////            for (Pipeline.Stage stage : rj.pipeline.stages) {
////                if (rj.cancelRequested.get()) { status.state = JobState.CANCELED; break; }
////                status.currentStage = stage.name;
////                status.currentStep = null;
////                status.write(rj.statusFile);
////
////                Path stageLog = rj.logsDir.resolve("stage_" + safe(stage.name) + ".log");
////                long stageStart = System.currentTimeMillis();
////                appendLog(stageLog, ">>> ENTER Stage: " + stage.name);
////
////                if (completedStages.contains(stage.name)) {
////                    appendLog(stageLog, "[CHECKPOINT] Skipping stage: " + stage.name);
////                    continue;
////                }
////
////                boolean stageOk = true;
////                for (Pipeline.Step step : stage.steps) {
////                    if (rj.cancelRequested.get()) { status.state = JobState.CANCELED; stageOk = false; break; }
////                    status.currentStep = step.type + " " + step.command;
////                    status.write(rj.statusFile);
////
////                    long stepStart = System.currentTimeMillis();
////                    appendLog(stageLog, "[STEP START] " + step.type + " " + step.command);
////
////                    if ("echo".equals(step.type)) {
////                        appendLog(stageLog, "[ECHO] " + step.command);
////                        appendLog(stageLog, "[STEP END] echo | Duration: " + (System.currentTimeMillis() - stepStart) + " ms");
////                        continue;
////                    }
////
////                    if ("sh".equals(step.type)) {
////                        int attempt = 0;
////                        boolean ok = false;
////                        while (attempt <= rj.cfg.stepMaxRetries && !ok && !rj.cancelRequested.get()) {
////                            attempt++;
////                            appendLog(stageLog, "[SH] Attempt " + attempt + " executing: " + step.command + " (timeout=" + rj.cfg.stepTimeoutMinutes + "m)");
////
////                            int exit = execStreaming(rj, step.command, stageLog, Duration.ofMinutes(rj.cfg.stepTimeoutMinutes));
////                            if (exit == 0) {
////                                ok = true;
////                                appendLog(stageLog, "[SH] Exit code: 0 (SUCCESS)");
////                            } else {
////                                appendLog(stageLog, "[SH] Exit code: " + exit + " (FAIL)");
////                                if (attempt <= rj.cfg.stepMaxRetries) {
////                                    appendLog(stageLog, "[RETRY] Backing off " + rj.cfg.retryBackoffSec + "s...");
////                                    sleepSeconds(rj.cfg.retryBackoffSec);
////                                }
////                            }
////                        }
////                        appendLog(stageLog, "[STEP END] sh '" + step.command + "' | Duration: " + (System.currentTimeMillis() - stepStart) + " ms");
////                        if (!ok) { stageOk = false; break; }
////                    }
////                }
////
////                long stageDuration = System.currentTimeMillis() - stageStart;
////                if (!stageOk) {
////                    appendLog(stageLog, "<<< FAIL Stage: " + stage.name + " | Duration: " + stageDuration + " ms");
////                    status.state = rj.cancelRequested.get() ? JobState.CANCELED : JobState.FAILED;
////                    break;
////                } else {
////                    appendLog(stageLog, "<<< SUCCESS Stage: " + stage.name + " | Duration: " + stageDuration + " ms");
////                    completedStages.add(stage.name);
////                    Checkpoint.save(rj.checkpointFile, completedStages);
////                }
////            }
////
////            if (status.state == JobState.RUNNING) {
////                status.state = JobState.SUCCESS;
////            }
////        } catch (Exception ex) {
////            status.state = JobState.FAILED;
////            status.error = ex.getMessage();
////        } finally {
////            status.endedAt = Instant.now();
////            long jobDuration = status.startedAt != null ? (status.endedAt.toEpochMilli() - status.startedAt.toEpochMilli()) : 0;
////            appendLog(jobLog, "=== JOB END (" + status.state + ") | Duration: " + jobDuration + " ms ===");
////            status.write(rj.statusFile);
////            hb.stop();
////            jobs.remove(rj.jobId);
////        }
////    }
////
////    // ---------- Process exec with streaming logs & rotation ----------
////
////    private int execStreaming(RunningJob rj, String command, Path stageLog, Duration timeout) {
////        ProcessBuilder pb;
////        if (isWindows()) pb = new ProcessBuilder("cmd.exe", "/c", command);
////        else             pb = new ProcessBuilder("bash", "-c", command);
////        pb.directory(rj.workspace.toFile());
////
////        try {
////            Process p = pb.start();
////            rj.currentProcess.set(p);
////
////            CountDownLatch latch = new CountDownLatch(2);
////            StreamGobbler outG = new StreamGobbler(p.getInputStream(), stageLog, rj.cfg.maxLogBytes, "[STDOUT]", latch);
////            StreamGobbler errG = new StreamGobbler(p.getErrorStream(), stageLog, rj.cfg.maxLogBytes, "[STDERR]", latch);
////            outG.start(); errG.start();
////
////            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
////
////            if (!finished) {
////                appendLog(stageLog, "[TIMEOUT] Killing process after " + timeout.toMinutes() + " min (PID=" + p.pid() + ")");
////                p.destroy();
////                if (p.isAlive()) {
////                    sleepSeconds(3);
////                    p.destroyForcibly();
////                }
////            }
////
////            latch.await();
////            return finished ? p.exitValue() : 124;
////        } catch (Exception e) {
////            appendLog(stageLog, "[ERROR] execStreaming crash: " + e.getMessage());
////            return 1;
////        } finally {
////            rj.currentProcess.set(null);
////        }
////    }
////
////    // ---------- Helpers ----------
////
////    private static boolean isWindows() {
////        return System.getProperty("os.name").toLowerCase().contains("win");
////    }
////
////    private static void sleepSeconds(long s) {
////        try { TimeUnit.SECONDS.sleep(s); } catch (InterruptedException ignored) {}
////    }
////
////    private static String safe(String name) {
////        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
////    }
////
////    private static void appendLog(Path file, String line) {
////        try {
////            String ts = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now());
////            Files.createDirectories(file.getParent());
////            Files.writeString(file, "[" + ts + "] " + line + System.lineSeparator(),
////                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
////        } catch (IOException ignored) {}
////    }
////
////    // ---------- Data structures ----------
////
////    private static final class RunningJob {
////        final String jobId;
////        final Path workspace, runDir, logsDir, statusFile, checkpointFile;
////        final Pipeline pipeline;
////        final RunConfig cfg;
////        final AtomicBoolean cancelRequested = new AtomicBoolean(false);
////        final AtomicReference<Process> currentProcess = new AtomicReference<>(null);
////
////        RunningJob(String jobId, Path workspace, Path runDir, Path logsDir, Path status, Path checkpoint,
////                   Pipeline pipeline, RunConfig cfg) {
////            this.jobId = jobId;
////            this.workspace = workspace;
////            this.runDir = runDir;
////            this.logsDir = logsDir;
////            this.statusFile = status;
////            this.checkpointFile = checkpoint;
////            this.pipeline = pipeline;
////            this.cfg = cfg;
////        }
////    }
////
////    private static final class StreamGobbler extends Thread {
////        private final InputStream in;
////        private final Path file;
////        private final long maxBytes;
////        private final String prefix;
////        private final CountDownLatch latch;
////
////        StreamGobbler(InputStream in, Path file, long maxBytes, String prefix, CountDownLatch latch) {
////            this.in = in; this.file = file; this.maxBytes = maxBytes; this.prefix = prefix; this.latch = latch;
////            setName("StreamGobbler-" + prefix);
////            setDaemon(true);
////        }
////
////        @Override public void run() {
////            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
////                String line;
////                while ((line = br.readLine()) != null) {
////                    rotateIfNeeded(file, maxBytes);
////                    appendLog(file, prefix + " " + line);
////                }
////            } catch (IOException ignored) {
////            } finally { latch.countDown(); }
////        }
////
////        private void rotateIfNeeded(Path file, long maxBytes) {
////            try {
////                if (Files.exists(file) && Files.size(file) >= maxBytes) {
////                    Path rotated = Paths.get(file.toString() + "." + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()));
////                    Files.move(file, rotated, StandardCopyOption.ATOMIC_MOVE);
////                }
////            } catch (IOException ignored) {}
////        }
////    }
////
////    private static final class Heartbeat {
////        private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
////        private final Path statusFile;
////        private final long periodSec;
////        private final Supplier<Status> snapshot;
////
////        Heartbeat(Path statusFile, long periodSec, Supplier<Status> snapshot) {
////            this.statusFile = statusFile; this.periodSec = periodSec; this.snapshot = snapshot;
////        }
////
////        void start() { ses.scheduleAtFixedRate(() -> snapshot.get().write(statusFile), 0, periodSec, TimeUnit.SECONDS); }
////        void stop()  { ses.shutdownNow(); }
////    }
////
////    private static final class Status {
////        String jobId;
////        JobState state = JobState.PENDING;
////        String workspace;
////        String currentStage;
////        String currentStep;
////        Instant startedAt;
////        Instant endedAt;
////        String error;
////
////        static Status initial(String jobId, String workspace) {
////            Status s = new Status();
////            s.jobId = jobId; s.workspace = workspace;
////            return s;
////        }
////
////        public Status clone() {
////            Status s = new Status();
////            s.jobId = jobId; s.state = state; s.workspace = workspace;
////            s.currentStage = currentStage; s.currentStep = currentStep;
////            s.startedAt = startedAt; s.endedAt = endedAt; s.error = error;
////            return s;
////        }
////
////        void write(Path f) {
////            try {
////                String json = "{"
////                        + "\"jobId\":\"" + esc(jobId) + "\","
////                        + "\"state\":\"" + state + "\","
////                        + "\"workspace\":\"" + esc(workspace) + "\","
////                        + "\"currentStage\":\"" + esc(nullToEmpty(currentStage)) + "\","
////                        + "\"currentStep\":\"" + esc(nullToEmpty(currentStep)) + "\","
////                        + "\"startedAt\":\"" + (startedAt == null ? "" : startedAt) + "\","
////                        + "\"endedAt\":\"" + (endedAt == null ? "" : endedAt) + "\","
////                        + "\"error\":\"" + esc(nullToEmpty(error)) + "\""
////                        + "}";
////                Files.createDirectories(f.getParent());
////                Files.writeString(f, json, StandardCharsets.UTF_8,
////                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
////            } catch (IOException ignored) {}
////        }
////
////        private static String esc(String s) {
////            return s.replace("\\","\\\\").replace("\"","\\\"");
////        }
////        private static String nullToEmpty(String s) { return s == null ? "" : s; }
////    }
////
////    // ---------- Checkpoint persistence ----------
////    private static final class Checkpoint {
////        static Set<String> load(Path f) {
////            try {
////                if (!Files.exists(f)) return new HashSet<>();
////                String json = Files.readString(f, StandardCharsets.UTF_8);
////                String[] lines = json.split("\\R");
////                return new HashSet<>(Arrays.asList(lines));
////            } catch (IOException e) {
////                return new HashSet<>();
////            }
////        }
////
////        static void save(Path f, Set<String> completed) {
////            try {
////                Files.createDirectories(f.getParent());
////                Files.write(f, String.join(System.lineSeparator(), completed).getBytes(StandardCharsets.UTF_8),
////                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
////            } catch (IOException ignored) {}
////        }
////    }
////
////    // ---------- Pipeline DSL ----------
////    private static final class Pipeline {
////        final List<Stage> stages = new ArrayList<>();
////
////        static final class Stage {
////            final String name;
////            final List<Step> steps = new ArrayList<>();
////            Stage(String name) { this.name = name; }
////        }
////
////        static final class Step {
////            final String type;   // "echo" | "sh"
////            final String command;
////            Step(String type, String command) { this.type = type; this.command = command; }
////        }
////
////        static Pipeline parse(String def) {
////            Pipeline p = new Pipeline();
////            Pattern stagePat = Pattern.compile("stage\\s*\\(\\s*['\"](.*?)['\"]\\s*\\)\\s*\\{([\\s\\S]*?)\\}");
////            Matcher sm = stagePat.matcher(def);
////            while (sm.find()) {
////                String name = sm.group(1);
////                String body = sm.group(2);
////                Stage st = new Stage(name);
////
////                Matcher stepsM = Pattern.compile("steps\\s*\\{([\\s\\S]*?)\\}").matcher(body);
////                if (stepsM.find()) {
////                    String stepsBody = stepsM.group(1);
////                    Matcher cmdM = Pattern.compile("(echo|sh)\\s+['\"]([\\s\\S]*?)['\"]").matcher(stepsBody);
////                    while (cmdM.find()) {
////                        st.steps.add(new Step(cmdM.group(1), cmdM.group(2)));
////                    }
////                }
////                p.stages.add(st);
////            }
////            if (p.stages.isEmpty()) {
////                throw new IllegalArgumentException("No stages parsed from pipeline definition");
////            }
////            return p;
////        }
////    }
////
////    // ---------- Demo Main ----------
////    public static void main(String[] args) throws Exception {
////        RobustPipelineExecutor exec = new RobustPipelineExecutor(Paths.get("runs"));
////
////        // Payload workspace
////        Payload payload = new Payload();
////        payload.setName("ETL Job");
////        payload.setPath("D:/lap/java/dsa/path/to"); // <-- change to your workspace
////
////        // Jenkins-like pipeline
////        String pipeline =
////                "stage('Extract'){ steps { echo 'Starting Extract'; sh 'python extract.py' } }\n" +
////                        "stage('Transform'){ steps { echo 'Starting Transform'; sh 'python transform.py' } }\n" +
////                        "stage('Load'){ steps { echo 'Starting Load'; sh 'python load.py' } }";
////
////        RunConfig cfg = new RunConfig();
////        cfg.stepTimeoutMinutes = 60;
////        cfg.stepMaxRetries = 2;
////        cfg.retryBackoffSec = 15;
////        cfg.heartbeatSec = 5;
////        cfg.maxLogBytes = 50L * 1024 * 1024;
////
////        SubmitResult sr = exec.submit(payload, pipeline, cfg);
////        if (!sr.accepted) {
////            System.out.println("Submit failed: " + sr.message);
////            return;
////        }
////
////        System.out.println("JobId: " + sr.handle.jobId);
////        System.out.println("RunDir: " + sr.handle.runDir);
////        System.out.println("Status: " + sr.handle.statusFile);
////        System.out.println("Checkpoint: " + sr.handle.checkpointFile);
////        System.out.println("Logs: " + sr.handle.runDir.resolve("logs"));
////
////        for (int i = 0; i < 10; i++) {
////            Thread.sleep(3000);
////            exec.readStatus(sr.handle.jobId).ifPresent(System.out::println);
////        }
////
////        // Example cancel (optional)
////        // exec.cancel(sr.handle.jobId);
////    }
////}
////
//
//
//
//
//package com.executor1.service;
//
//import com.executor1.entities2.Payload;
//
//import java.io.*;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.*;
//import java.time.*;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.concurrent.*;
//import java.util.concurrent.atomic.AtomicBoolean;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.function.Supplier;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//
///**
// * Robust, Jenkins-like pipeline executor with detailed logging:
// * - streaming stdout/stderr to rolling logs
// * - status.json heartbeat
// * - per-stage checkpoints & resume
// * - per-step timeout, retries, cancel
// * - Windows/Linux shell support
// * - detailed logs for jobs, stages, steps, retries, cancel, timeouts
// * - metrics collection and tracing
// */
//public class RobustPipelineExecutor {
//
//    // ---------- Public API ----------
//
//    public static final class RunConfig {
//        public long stepTimeoutMinutes = 60;
//        public int  stepMaxRetries     = 1;
//        public long retryBackoffSec    = 10;
//        public long heartbeatSec       = 5;
//        public long maxLogBytes        = 50L * 1024 * 1024;
//        public boolean resumeFromCheckpoints = true;
//        public boolean enableMetrics = true;
//        public boolean enableTracing = true;
//    }
//
//    public static final class JobHandle {
//        public final String jobId;
//        public final Path workspace;
//        public final Path runDir;
//        public final Path statusFile;
//        public final Path checkpointFile;
//        public final Path metricsFile;
//
//        JobHandle(String jobId, Path workspace, Path runDir, Path status, Path checkpoint, Path metricsFile) {
//            this.jobId = jobId;
//            this.workspace = workspace;
//            this.runDir = runDir;
//            this.statusFile = status;
//            this.checkpointFile = checkpoint;
//            this.metricsFile = metricsFile;
//        }
//    }
//
//    public static final class SubmitResult {
//        public final boolean accepted;
//        public final String message;
//        public final JobHandle handle;
//
//        SubmitResult(boolean accepted, String message, JobHandle handle) {
//            this.accepted = accepted;
//            this.message = message;
//            this.handle = handle;
//        }
//    }
//
//    public enum JobState { PENDING, RUNNING, SUCCESS, FAILED, CANCELED }
//
//    // ---------- Implementation ----------
//
//    private final ExecutorService runPool = Executors.newCachedThreadPool();
//    private final Map<String, RunningJob> jobs = new ConcurrentHashMap<>();
//    private final Path rootRuns;
//
//    public RobustPipelineExecutor(Path runsRoot) throws IOException {
//        this.rootRuns = runsRoot;
//        Files.createDirectories(rootRuns);
//    }
//
//    public SubmitResult submit(Payload payload, String pipelineDef, RunConfig cfg) {
//        Objects.requireNonNull(payload, "payload");
//        Objects.requireNonNull(pipelineDef, "pipelineDef");
//        if (payload.getPath() == null || payload.getPath().isBlank()) {
//            return new SubmitResult(false, "Payload path is required", null);
//        }
//
//        String jobId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").format(LocalDateTime.now());
//        Path workspace = Paths.get(payload.getPath()).toAbsolutePath();
//        Path runDir = rootRuns.resolve("job_" + jobId);
//        Path logsDir = runDir.resolve("logs");
//        Path status = runDir.resolve("status.json");
//        Path checkpoint = runDir.resolve("checkpoint.json");
//        Path metricsFile = runDir.resolve("metrics.json");
//
//        try {
//            Files.createDirectories(logsDir);
//            Status s = Status.initial(jobId, workspace.toString());
//            s.write(status);
//
//            Pipeline pipeline = Pipeline.parse(pipelineDef);
//            RunningJob rj = new RunningJob(jobId, workspace, runDir, logsDir, status, checkpoint, metricsFile, pipeline, cfg);
//            jobs.put(jobId, rj);
//            runPool.submit(() -> runJob(rj));
//            return new SubmitResult(true, "Accepted", new JobHandle(jobId, workspace, runDir, status, checkpoint, metricsFile));
//        } catch (Exception e) {
//            return new SubmitResult(false, "Failed to create run: " + e.getMessage(), null);
//        }
//    }
//
//    public boolean cancel(String jobId) {
//        RunningJob rj = jobs.get(jobId);
//        if (rj == null) return false;
//        rj.cancelRequested.set(true);
//        Process p = rj.currentProcess.get();
//        if (p != null) {
//            appendLog(rj.logsDir.resolve("job.log"), "[CANCEL] Cancel signal sent to PID=" + p.pid());
//            appendLog(rj.logsDir.resolve("job.log"), "[CANCEL] Process info: " + p.info());
//            p.destroy();
//        }
//        return true;
//    }
//
//    public Optional<String> readStatus(String jobId) {
//        RunningJob rj = jobs.get(jobId);
//        if (rj == null) return Optional.empty();
//        try {
//            return Optional.of(Files.readString(rj.statusFile, StandardCharsets.UTF_8));
//        } catch (IOException e) {
//            return Optional.of("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
//        }
//    }
//
//    public Optional<String> readMetrics(String jobId) {
//        RunningJob rj = jobs.get(jobId);
//        if (rj == null) return Optional.empty();
//        try {
//            return Optional.of(Files.readString(rj.metricsFile, StandardCharsets.UTF_8));
//        } catch (IOException e) {
//            return Optional.of("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
//        }
//    }
//
//    // ---------- Run Loop ----------
//
//    private void runJob(RunningJob rj) {
//        Status status = Status.initial(rj.jobId, rj.workspace.toString());
//        status.state = JobState.RUNNING;
//        status.startedAt = Instant.now();
//
//        Path jobLog = rj.logsDir.resolve("job.log");
//        appendLog(jobLog, "=== JOB START ===");
//        appendLog(jobLog, "JobId: " + rj.jobId);
//        appendLog(jobLog, "Workspace: " + rj.workspace);
//        appendLog(jobLog, "RunDir: " + rj.runDir);
//        appendLog(jobLog, "OS: " + System.getProperty("os.name") + " | User: " + System.getProperty("user.name"));
//        appendLog(jobLog, "OS Architecture: " + System.getProperty("os.arch"));
//        appendLog(jobLog, "OS Version: " + System.getProperty("os.version"));
//        appendLog(jobLog, "Java Version: " + System.getProperty("java.version"));
//        appendLog(jobLog, "Available Processors: " + Runtime.getRuntime().availableProcessors());
//        appendLog(jobLog, "Total Memory: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + "MB");
//        appendLog(jobLog, "Free Memory: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + "MB");
//        appendLog(jobLog, "Max Memory: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + "MB");
//        appendLog(jobLog, "Pipeline stages: " + rj.pipeline.stages.size());
//
//        // Initialize metrics
//        Metrics metrics = new Metrics(rj.jobId);
//        metrics.jobStartTime = System.currentTimeMillis();
//        metrics.totalStages = rj.pipeline.stages.size();
//
//        Heartbeat hb = new Heartbeat(rj.statusFile, rj.cfg.heartbeatSec, () -> status.clone());
//        hb.start();
//        try {
//            long jobStart = System.currentTimeMillis();
//            Set<String> completedStages = rj.cfg.resumeFromCheckpoints ? Checkpoint.load(rj.checkpointFile) : new HashSet<>();
//
//            for (Pipeline.Stage stage : rj.pipeline.stages) {
//                if (rj.cancelRequested.get()) {
//                    status.state = JobState.CANCELED;
//                    appendLog(jobLog, "[CANCEL] Job cancellation requested during stage: " + stage.name);
//                    break;
//                }
//                status.currentStage = stage.name;
//                status.currentStep = null;
//                status.write(rj.statusFile);
//
//                Path stageLog = rj.logsDir.resolve("stage_" + safe(stage.name) + ".log");
//                long stageStart = System.currentTimeMillis();
//                appendLog(stageLog, ">>> ENTER Stage: " + stage.name);
//                appendLog(stageLog, "Stage start time: " + Instant.ofEpochMilli(stageStart));
//                appendLog(stageLog, "Thread: " + Thread.currentThread().getName());
//
//                if (completedStages.contains(stage.name)) {
//                    appendLog(stageLog, "[CHECKPOINT] Skipping stage: " + stage.name);
//                    appendLog(stageLog, "[CHECKPOINT] Stage was previously completed successfully");
//                    metrics.completedStages++;
//                    continue;
//                }
//
//                boolean stageOk = true;
//                for (Pipeline.Step step : stage.steps) {
//                    if (rj.cancelRequested.get()) {
//                        status.state = JobState.CANCELED;
//                        stageOk = false;
//                        appendLog(stageLog, "[CANCEL] Step execution cancelled: " + step.type + " " + step.command);
//                        break;
//                    }
//                    status.currentStep = step.type + " " + step.command;
//                    status.write(rj.statusFile);
//
//                    long stepStart = System.currentTimeMillis();
//                    appendLog(stageLog, "[STEP START] " + step.type + " " + step.command);
//                    appendLog(stageLog, "[STEP DETAILS] Start time: " + Instant.ofEpochMilli(stepStart));
//                    appendLog(stageLog, "[STEP DETAILS] Working directory: " + rj.workspace);
//
//                    if ("echo".equals(step.type)) {
//                        appendLog(stageLog, "[ECHO] " + step.command);
//                        appendLog(stageLog, "[STEP END] echo | Duration: " + (System.currentTimeMillis() - stepStart) + " ms");
//                        metrics.logStepMetrics(step.type, step.command, 0, System.currentTimeMillis() - stepStart, 1);
//                        continue;
//                    }
//
//                    if ("sh".equals(step.type)) {
//                        int attempt = 0;
//                        boolean ok = false;
//                        long stepTotalDuration = 0;
//                        while (attempt <= rj.cfg.stepMaxRetries && !ok && !rj.cancelRequested.get()) {
//                            attempt++;
//                            appendLog(stageLog, "[SH] Attempt " + attempt + " executing: " + step.command + " (timeout=" + rj.cfg.stepTimeoutMinutes + "m)");
//                            appendLog(stageLog, "[SH] Command details: " + (isWindows() ? "cmd.exe /c " : "bash -c ") + step.command);
//
//                            long attemptStart = System.currentTimeMillis();
//                            int exit = execStreaming(rj, step.command, stageLog, Duration.ofMinutes(rj.cfg.stepTimeoutMinutes));
//                            long attemptDuration = System.currentTimeMillis() - attemptStart;
//                            stepTotalDuration += attemptDuration;
//
//                            if (exit == 0) {
//                                ok = true;
//                                appendLog(stageLog, "[SH] Exit code: 0 (SUCCESS)");
//                                appendLog(stageLog, "[SH] Attempt " + attempt + " duration: " + attemptDuration + " ms");
//                            } else {
//                                appendLog(stageLog, "[SH] Exit code: " + exit + " (FAIL)");
//                                appendLog(stageLog, "[SH] Attempt " + attempt + " duration: " + attemptDuration + " ms");
//                                if (attempt <= rj.cfg.stepMaxRetries) {
//                                    appendLog(stageLog, "[RETRY] Backing off " + rj.cfg.retryBackoffSec + "s...");
//                                    appendLog(stageLog, "[RETRY] Next attempt will be attempt " + (attempt + 1));
//                                    sleepSeconds(rj.cfg.retryBackoffSec);
//                                }
//                            }
//                        }
//                        appendLog(stageLog, "[STEP END] sh '" + step.command + "' | Duration: " + stepTotalDuration + " ms | Attempts: " + attempt);
//                        metrics.logStepMetrics(step.type, step.command, ok ? 0 : 1, stepTotalDuration, attempt);
//                        if (!ok) {
//                            stageOk = false;
//                            break;
//                        }
//                    }
//                }
//
//                long stageDuration = System.currentTimeMillis() - stageStart;
//                if (!stageOk) {
//                    appendLog(stageLog, "<<< FAIL Stage: " + stage.name + " | Duration: " + stageDuration + " ms");
//                    appendLog(stageLog, "Stage end time: " + Instant.ofEpochMilli(System.currentTimeMillis()));
//                    status.state = rj.cancelRequested.get() ? JobState.CANCELED : JobState.FAILED;
//                    metrics.failedStages++;
//                    break;
//                } else {
//                    appendLog(stageLog, "<<< SUCCESS Stage: " + stage.name + " | Duration: " + stageDuration + " ms");
//                    appendLog(stageLog, "Stage end time: " + Instant.ofEpochMilli(System.currentTimeMillis()));
//                    completedStages.add(stage.name);
//                    Checkpoint.save(rj.checkpointFile, completedStages);
//                    metrics.completedStages++;
//                    metrics.stageDurations.put(stage.name, stageDuration);
//                }
//            }
//
//            if (status.state == JobState.RUNNING) {
//                status.state = JobState.SUCCESS;
//            }
//        } catch (Exception ex) {
//            status.state = JobState.FAILED;
//            status.error = ex.getMessage();
//            appendLog(jobLog, "[JOB ERROR] Unhandled exception: " + ex.getMessage());
//            appendLog(jobLog, "[JOB ERROR] Stack trace: " + Arrays.toString(ex.getStackTrace()));
//        } finally {
//            status.endedAt = Instant.now();
//            long jobDuration = status.startedAt != null ? (status.endedAt.toEpochMilli() - status.startedAt.toEpochMilli()) : 0;
//            appendLog(jobLog, "=== JOB END (" + status.state + ") | Duration: " + jobDuration + " ms ===");
//            appendLog(jobLog, "Job end time: " + status.endedAt);
//
//            // Finalize metrics
//            metrics.jobEndTime = System.currentTimeMillis();
//            metrics.jobDuration = jobDuration;
//            metrics.jobStatus = status.state.toString();
//            if (rj.cfg.enableMetrics) {
//                metrics.write(rj.metricsFile);
//            }
//
//            status.write(rj.statusFile);
//            hb.stop();
//            jobs.remove(rj.jobId);
//        }
//    }
//
//    // ---------- Process exec with streaming logs & rotation ----------
//
//    private int execStreaming(RunningJob rj, String command, Path stageLog, Duration timeout) {
//        ProcessBuilder pb;
//        if (isWindows()) pb = new ProcessBuilder("cmd.exe", "/c", command);
//        else             pb = new ProcessBuilder("bash", "-c", command);
//        pb.directory(rj.workspace.toFile());
//
//        // Capture environment details
//        Map<String, String> env = pb.environment();
//        appendLog(stageLog, "[ENV] Working directory: " + pb.directory().getAbsolutePath());
//        appendLog(stageLog, "[ENV] Command: " + String.join(" ", pb.command()));
//        appendLog(stageLog, "[ENV] Timeout: " + timeout.toMinutes() + " minutes");
//
//        try {
//            Process p = pb.start();
//            rj.currentProcess.set(p);
//
//            CountDownLatch latch = new CountDownLatch(2);
//            StreamGobbler outG = new StreamGobbler(p.getInputStream(), stageLog, rj.cfg.maxLogBytes, "[STDOUT]", latch);
//            StreamGobbler errG = new StreamGobbler(p.getErrorStream(), stageLog, rj.cfg.maxLogBytes, "[STDERR]", latch);
//            outG.start(); errG.start();
//
//            appendLog(stageLog, "[PROCESS] Started with PID: " + p.pid());
//            appendLog(stageLog, "[PROCESS] Process info: " + p.info().toString());
//
//            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
//
//            if (!finished) {
//                appendLog(stageLog, "[TIMEOUT] Killing process after " + timeout.toMinutes() + " min (PID=" + p.pid() + ")");
//                appendLog(stageLog, "[TIMEOUT] Process was alive for: " + (System.currentTimeMillis() - p.info().startInstant().get().toEpochMilli()) + " ms");
//                p.destroy();
//                if (p.isAlive()) {
//                    sleepSeconds(3);
//                    p.destroyForcibly();
//                    appendLog(stageLog, "[TIMEOUT] Process forcibly destroyed");
//                }
//            }
//
//            latch.await();
//            int exitCode = finished ? p.exitValue() : 124;
//            appendLog(stageLog, "[PROCESS] Exit code: " + exitCode);
//            if (finished) {
//                appendLog(stageLog, "[PROCESS] Execution time: " +
//                        (p.info().totalCpuDuration().isPresent() ? p.info().totalCpuDuration().get().toMillis() + " ms" : "unknown"));
//            }
//            return exitCode;
//        } catch (Exception e) {
//            appendLog(stageLog, "[ERROR] execStreaming crash: " + e.getMessage());
//            appendLog(stageLog, "[ERROR] Stack trace: " + Arrays.toString(e.getStackTrace()));
//            return 1;
//        } finally {
//            rj.currentProcess.set(null);
//        }
//    }
//
//    // ---------- Helpers ----------
//
//    private static boolean isWindows() {
//        return System.getProperty("os.name").toLowerCase().contains("win");
//    }
//
//    private static void sleepSeconds(long s) {
//        try { TimeUnit.SECONDS.sleep(s); } catch (InterruptedException ignored) {}
//    }
//
//    private static String safe(String name) {
//        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
//    }
//
//    private static void appendLog(Path file, String line) {
//        try {
//            String ts = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now());
//            Files.createDirectories(file.getParent());
//            Files.writeString(file, "[" + ts + "] " + line + System.lineSeparator(),
//                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
//        } catch (IOException ignored) {}
//    }
//
//    // ---------- Data structures ----------
//
//    private static final class RunningJob {
//        final String jobId;
//        final Path workspace, runDir, logsDir, statusFile, checkpointFile, metricsFile;
//        final Pipeline pipeline;
//        final RunConfig cfg;
//        final AtomicBoolean cancelRequested = new AtomicBoolean(false);
//        final AtomicReference<Process> currentProcess = new AtomicReference<>(null);
//
//        RunningJob(String jobId, Path workspace, Path runDir, Path logsDir, Path status, Path checkpoint, Path metricsFile,
//                   Pipeline pipeline, RunConfig cfg) {
//            this.jobId = jobId;
//            this.workspace = workspace;
//            this.runDir = runDir;
//            this.logsDir = logsDir;
//            this.statusFile = status;
//            this.checkpointFile = checkpoint;
//            this.metricsFile = metricsFile;
//            this.pipeline = pipeline;
//            this.cfg = cfg;
//        }
//    }
//
//    private static final class StreamGobbler extends Thread {
//        private final InputStream in;
//        private final Path file;
//        private final long maxBytes;
//        private final String prefix;
//        private final CountDownLatch latch;
//
//        StreamGobbler(InputStream in, Path file, long maxBytes, String prefix, CountDownLatch latch) {
//            this.in = in; this.file = file; this.maxBytes = maxBytes; this.prefix = prefix; this.latch = latch;
//            setName("StreamGobbler-" + prefix);
//            setDaemon(true);
//        }
//
//        @Override public void run() {
//            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
//                String line;
//                while ((line = br.readLine()) != null) {
//                    rotateIfNeeded(file, maxBytes);
//                    appendLog(file, prefix + " " + line);
//                }
//            } catch (IOException ignored) {
//            } finally { latch.countDown(); }
//        }
//
//        private void rotateIfNeeded(Path file, long maxBytes) {
//            try {
//                if (Files.exists(file) && Files.size(file) >= maxBytes) {
//                    Path rotated = Paths.get(file.toString() + "." + DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()));
//                    Files.move(file, rotated, StandardCopyOption.ATOMIC_MOVE);
//                    appendLog(file, "[LOG ROTATION] Log file rotated to: " + rotated.getFileName());
//                }
//            } catch (IOException ignored) {}
//        }
//    }
//
//    private static final class Heartbeat {
//        private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
//        private final Path statusFile;
//        private final long periodSec;
//        private final Supplier<Status> snapshot;
//
//        Heartbeat(Path statusFile, long periodSec, Supplier<Status> snapshot) {
//            this.statusFile = statusFile; this.periodSec = periodSec; this.snapshot = snapshot;
//        }
//
//        void start() {
//            ses.scheduleAtFixedRate(() -> {
//                try {
//                    snapshot.get().write(statusFile);
//                } catch (Exception e) {
//                    System.err.println("Heartbeat failed: " + e.getMessage());
//                }
//            }, 0, periodSec, TimeUnit.SECONDS);
//        }
//        void stop()  { ses.shutdownNow(); }
//    }
//
//    private static final class Status {
//        String jobId;
//        JobState state = JobState.PENDING;
//        String workspace;
//        String currentStage;
//        String currentStep;
//        Instant startedAt;
//        Instant endedAt;
//        String error;
//
//        static Status initial(String jobId, String workspace) {
//            Status s = new Status();
//            s.jobId = jobId; s.workspace = workspace;
//            return s;
//        }
//
//        public Status clone() {
//            Status s = new Status();
//            s.jobId = jobId; s.state = state; s.workspace = workspace;
//            s.currentStage = currentStage; s.currentStep = currentStep;
//            s.startedAt = startedAt; s.endedAt = endedAt; s.error = error;
//            return s;
//        }
//
//        void write(Path f) {
//            try {
//                String json = "{"
//                        + "\"jobId\":\"" + esc(jobId) + "\","
//                        + "\"state\":\"" + state + "\","
//                        + "\"workspace\":\"" + esc(workspace) + "\","
//                        + "\"currentStage\":\"" + esc(nullToEmpty(currentStage)) + "\","
//                        + "\"currentStep\":\"" + esc(nullToEmpty(currentStep)) + "\","
//                        + "\"startedAt\":\"" + (startedAt == null ? "" : startedAt) + "\","
//                        + "\"endedAt\":\"" + (endedAt == null ? "" : endedAt) + "\","
//                        + "\"error\":\"" + esc(nullToEmpty(error)) + "\""
//                        + "}";
//                Files.createDirectories(f.getParent());
//                Files.writeString(f, json, StandardCharsets.UTF_8,
//                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//            } catch (IOException ignored) {}
//        }
//
//        private static String esc(String s) {
//            return s.replace("\\","\\\\").replace("\"","\\\"");
//        }
//        private static String nullToEmpty(String s) { return s == null ? "" : s; }
//    }
//
//    // ---------- Metrics collection ----------
//    private static final class Metrics {
//        final String jobId;
//        final Map<String, Object> stepMetrics = new HashMap<>();
//        final Map<String, Long> stageDurations = new HashMap<>();
//        long jobStartTime;
//        long jobEndTime;
//        long jobDuration;
//        int totalStages;
//        int completedStages;
//        int failedStages;
//        String jobStatus;
//
//        Metrics(String jobId) {
//            this.jobId = jobId;
//        }
//
//        void logStepMetrics(String type, String command, int exitCode, long duration, int attempts) {
//            String key = type + "_" + command.hashCode();
//            Map<String, Object> stepData = new HashMap<>();
//            stepData.put("type", type);
//            stepData.put("command", command);
//            stepData.put("exitCode", exitCode);
//            stepData.put("durationMs", duration);
//            stepData.put("attempts", attempts);
//            stepData.put("timestamp", Instant.now().toString());
//
//            stepMetrics.put(key, stepData);
//        }
//
//        void write(Path f) {
//            try {
//                Map<String, Object> metricsData = new HashMap<>();
//                metricsData.put("jobId", jobId);
//                metricsData.put("jobStartTime", jobStartTime);
//                metricsData.put("jobEndTime", jobEndTime);
//                metricsData.put("jobDurationMs", jobDuration);
//                metricsData.put("totalStages", totalStages);
//                metricsData.put("completedStages", completedStages);
//                metricsData.put("failedStages", failedStages);
//                metricsData.put("jobStatus", jobStatus);
//                metricsData.put("stageDurations", stageDurations);
//                metricsData.put("stepMetrics", stepMetrics);
//                metricsData.put("systemCpuCores", Runtime.getRuntime().availableProcessors());
//                metricsData.put("systemTotalMemory", Runtime.getRuntime().totalMemory());
//                metricsData.put("systemFreeMemory", Runtime.getRuntime().freeMemory());
//                metricsData.put("systemMaxMemory", Runtime.getRuntime().maxMemory());
//
//                String json = "{" +
//                        metricsData.entrySet().stream()
//                                .map(entry -> "\"" + entry.getKey() + "\":" + toJsonValue(entry.getValue()))
//                                .collect(Collectors.joining(",")) +
//                        "}";
//
//                Files.createDirectories(f.getParent());
//                Files.writeString(f, json, StandardCharsets.UTF_8,
//                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//            } catch (IOException ignored) {}
//        }
//
//        private String toJsonValue(Object value) {
//            if (value instanceof String) {
//                return "\"" + ((String) value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
//            } else if (value instanceof Map) {
//                return "{" +
//                        ((Map<?, ?>) value).entrySet().stream()
//                                .map(entry -> "\"" + entry.getKey() + "\":" + toJsonValue(entry.getValue()))
//                                .collect(Collectors.joining(",")) +
//                        "}";
//            } else {
//                return String.valueOf(value);
//            }
//        }
//    }
//
//    // ---------- Checkpoint persistence ----------
//    private static final class Checkpoint {
//        static Set<String> load(Path f) {
//            try {
//                if (!Files.exists(f)) return new HashSet<>();
//                String json = Files.readString(f, StandardCharsets.UTF_8);
//                String[] lines = json.split("\\R");
//                return new HashSet<>(Arrays.asList(lines));
//            } catch (IOException e) {
//                return new HashSet<>();
//            }
//        }
//
//        static void save(Path f, Set<String> completed) {
//            try {
//                Files.createDirectories(f.getParent());
//                Files.write(f, String.join(System.lineSeparator(), completed).getBytes(StandardCharsets.UTF_8),
//                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
//            } catch (IOException ignored) {}
//        }
//    }
//
//    // ---------- Pipeline DSL ----------
//    private static final class Pipeline {
//        final List<Stage> stages = new ArrayList<>();
//
//        static final class Stage {
//            final String name;
//            final List<Step> steps = new ArrayList<>();
//            Stage(String name) { this.name = name; }
//        }
//
//        static final class Step {
//            final String type;   // "echo" | "sh"
//            final String command;
//            Step(String type, String command) { this.type = type; this.command = command; }
//        }
//
//        static Pipeline parse(String def) {
//            Pipeline p = new Pipeline();
//            Pattern stagePat = Pattern.compile("stage\\s*\\(\\s*['\"](.*?)['\"]\\s*\\)\\s*\\{([\\s\\S]*?)\\}");
//            Matcher sm = stagePat.matcher(def);
//            while (sm.find()) {
//                String name = sm.group(1);
//                String body = sm.group(2);
//                Stage st = new Stage(name);
//
//                Matcher stepsM = Pattern.compile("steps\\s*\\{([\\s\\S]*?)\\}").matcher(body);
//                if (stepsM.find()) {
//                    String stepsBody = stepsM.group(1);
//                    Matcher cmdM = Pattern.compile("(echo|sh)\\s+['\"]([\\s\\S]*?)['\"]").matcher(stepsBody);
//                    while (cmdM.find()) {
//                        st.steps.add(new Step(cmdM.group(1), cmdM.group(2)));
//                    }
//                }
//                p.stages.add(st);
//            }
//            if (p.stages.isEmpty()) {
//                throw new IllegalArgumentException("No stages parsed from pipeline definition");
//            }
//            return p;
//        }
//    }
//
//    // ---------- Demo Main ----------
//    public static void main(String[] args) throws Exception {
//        RobustPipelineExecutor exec = new RobustPipelineExecutor(Paths.get("runs"));
//
//        // Payload workspace
//        Payload payload = new Payload();
//        payload.setName("ETL Job");
//        payload.setPath("D:/lap/java/dsa/path/to"); // <-- change to your workspace
//
//        // Jenkins-like pipeline
//        String pipeline =
//                "stage('Extract'){ steps { echo 'Starting Extract'; sh 'python extract.py' } }\n" +
//                        "stage('Transform'){ steps { echo 'Starting Transform'; sh 'python transform.py' } }\n" +
//                        "stage('Load'){ steps { echo 'Starting Load'; sh 'python load.py' } }";
//
//        RunConfig cfg = new RunConfig();
//        cfg.stepTimeoutMinutes = 60;
//        cfg.stepMaxRetries = 2;
//        cfg.retryBackoffSec = 15;
//        cfg.heartbeatSec = 5;
//        cfg.maxLogBytes = 50L * 1024 * 1024;
//        cfg.enableMetrics = true;
//        cfg.enableTracing = true;
//
//        SubmitResult sr = exec.submit(payload, pipeline, cfg);
//        if (!sr.accepted) {
//            System.out.println("Submit failed: " + sr.message);
//            return;
//        }
//
//        System.out.println("JobId: " + sr.handle.jobId);
//        System.out.println("RunDir: " + sr.handle.runDir);
//        System.out.println("Status: " + sr.handle.statusFile);
//        System.out.println("Checkpoint: " + sr.handle.checkpointFile);
//        System.out.println("Metrics: " + sr.handle.metricsFile);
//        System.out.println("Logs: " + sr.handle.runDir.resolve("logs"));
//
//        for (int i = 0; i < 10; i++) {
//            Thread.sleep(3000);
//            exec.readStatus(sr.handle.jobId).ifPresent(System.out::println);
//            exec.readMetrics(sr.handle.jobId).ifPresent(System.out::println);
//        }
//
//        // Example cancel (optional)
//        // exec.cancel(sr.handle.jobId);
//    }
//}



package com.executor1.service;

import com.executor1.entities2.Payload;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Robust, Jenkins-like pipeline executor with detailed logging:
 * - streaming stdout/stderr to rolling logs
 * - status.json heartbeat
 * - per-stage checkpoints & resume
 * - per-step timeout, retries, cancel
 * - Windows/Linux shell support
 * - detailed logs for jobs, stages, steps, retries, cancel, timeouts
 */
public class RobustPipelineExecutor {

    // ---------- Public API ----------

    public static final class RunConfig {
        public long stepTimeoutMinutes = 60;
        public int  stepMaxRetries     = 1;
        public long retryBackoffSec    = 10;
        public long heartbeatSec       = 5;
        public long maxLogBytes        = 50L * 1024 * 1024;
        public boolean resumeFromCheckpoints = true;
    }

    public static final class JobHandle {
        public final String jobId;
        public final Path workspace;
        public final Path runDir;
        public final Path statusFile;
        public final Path checkpointFile;

        JobHandle(String jobId, Path workspace, Path runDir, Path status, Path checkpoint) {
            this.jobId = jobId;
            this.workspace = workspace;
            this.runDir = runDir;
            this.statusFile = status;
            this.checkpointFile = checkpoint;
        }
    }

    public static final class SubmitResult {
        public final boolean accepted;
        public final String message;
        public final JobHandle handle;

        SubmitResult(boolean accepted, String message, JobHandle handle) {
            this.accepted = accepted;
            this.message = message;
            this.handle = handle;
        }
    }

    public enum JobState { PENDING, RUNNING, SUCCESS, FAILED, CANCELED }

    // ---------- Implementation ----------

    private final ExecutorService runPool = Executors.newCachedThreadPool();
    private final Map<String, RunningJob> jobs = new ConcurrentHashMap<>();
    private final Path rootRuns;

    public RobustPipelineExecutor(Path runsRoot) throws IOException {
        this.rootRuns = runsRoot;
        Files.createDirectories(rootRuns);
    }

    public SubmitResult submit(Payload payload, String pipelineDef, RunConfig cfg) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(pipelineDef, "pipelineDef");
        if (payload.getPath() == null || payload.getPath().isBlank()) {
            return new SubmitResult(false, "Payload path is required", null);
        }

        String jobId = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS").format(LocalDateTime.now());
        Path workspace = Paths.get(payload.getPath()).toAbsolutePath();
        Path runDir = rootRuns.resolve("job_" + jobId);
        Path logsDir = runDir.resolve("logs");
        Path status = runDir.resolve("status.json");
        Path checkpoint = runDir.resolve("checkpoint.json");

        try {
            Files.createDirectories(logsDir);
            Status s = Status.initial(jobId, workspace.toString());
            s.write(status);

            Pipeline pipeline = Pipeline.parse(pipelineDef);
            RunningJob rj = new RunningJob(jobId, workspace, runDir, logsDir, status, checkpoint, pipeline, cfg);
            jobs.put(jobId, rj);
            runPool.submit(() -> runJob(rj));
            return new SubmitResult(true, "Accepted", new JobHandle(jobId, workspace, runDir, status, checkpoint));
        } catch (Exception e) {
            return new SubmitResult(false, "Failed to create run: " + e.getMessage(), null);
        }
    }

    public boolean cancel(String jobId) {
        RunningJob rj = jobs.get(jobId);
        if (rj == null) return false;
        rj.cancelRequested.set(true);
        Process p = rj.currentProcess.get();
        if (p != null) {
            appendLog(rj.logsDir.resolve("job.log"), "[CANCEL] Cancel signal sent to PID=" + p.pid());
            p.destroy();
        }
        return true;
    }

    public Optional<String> readStatus(String jobId) {
        RunningJob rj = jobs.get(jobId);
        if (rj == null) return Optional.empty();
        try {
            return Optional.of(Files.readString(rj.statusFile, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.of("{\"error\":\"" + e.getMessage().replace("\"","\\\"") + "\"}");
        }
    }

    // ---------- Run Loop ----------

    private void runJob(RunningJob rj) {
        Status status = Status.initial(rj.jobId, rj.workspace.toString());
        status.state = JobState.RUNNING;
        status.startedAt = Instant.now();

        Path jobLog = rj.logsDir.resolve("job.log");
        appendLog(jobLog, "=== JOB START ===");
        appendLog(jobLog, "JobId: " + rj.jobId);
        appendLog(jobLog, "Workspace: " + rj.workspace);
        appendLog(jobLog, "RunDir: " + rj.runDir);
        appendLog(jobLog, "OS: " + System.getProperty("os.name") + " | User: " + System.getProperty("user.name"));
        appendLog(jobLog, "Pipeline stages: " + rj.pipeline.stages.size());

        Heartbeat hb = new Heartbeat(rj.statusFile, rj.cfg.heartbeatSec, () -> status.clone());
        hb.start();
        try {
            long jobStart = System.currentTimeMillis();
            Set<String> completedStages = rj.cfg.resumeFromCheckpoints ? Checkpoint.load(rj.checkpointFile) : new HashSet<>();

            for (Pipeline.Stage stage : rj.pipeline.stages) {
                if (rj.cancelRequested.get()) { status.state = JobState.CANCELED; break; }
                status.currentStage = stage.name;
                status.currentStep = null;
                status.write(rj.statusFile);

                Path stageLog = rj.logsDir.resolve("stage_" + safe(stage.name) + ".log");
                long stageStart = System.currentTimeMillis();
                appendLog(stageLog, ">>> ENTER Stage: " + stage.name);

                if (completedStages.contains(stage.name)) {
                    appendLog(stageLog, "[CHECKPOINT] Skipping stage: " + stage.name);
                    continue;
                }

                boolean stageOk = true;
                for (Pipeline.Step step : stage.steps) {
                    if (rj.cancelRequested.get()) { status.state = JobState.CANCELED; stageOk = false; break; }
                    status.currentStep = step.type + " " + step.command;
                    status.write(rj.statusFile);

                    long stepStart = System.currentTimeMillis();
                    appendLog(stageLog, "[STEP START] " + step.type + " " + step.command);

                    if ("echo".equals(step.type)) {
                        appendLog(stageLog, "[ECHO] " + step.command);
                        appendLog(stageLog, "[STEP END] echo | Duration: " + (System.currentTimeMillis() - stepStart) + " ms");
                        continue;
                    }

                    if ("sh".equals(step.type)) {
                        int attempt = 0;
                        boolean ok = false;
                        while (attempt <= rj.cfg.stepMaxRetries && !ok && !rj.cancelRequested.get()) {
                            attempt++;
                            appendLog(stageLog, "[SH] Attempt " + attempt + " executing: " + step.command + " (timeout=" + rj.cfg.stepTimeoutMinutes + "m)");

                            int exit = execStreaming(rj, step.command, stageLog, Duration.ofMinutes(rj.cfg.stepTimeoutMinutes));
                            if (exit == 0) {
                                ok = true;
                                appendLog(stageLog, "[SH] Exit code: 0 (SUCCESS)");
                            } else {
                                appendLog(stageLog, "[SH] Exit code: " + exit + " (FAIL)");
                                if (attempt <= rj.cfg.stepMaxRetries) {
                                    appendLog(stageLog, "[RETRY] Backing off " + rj.cfg.retryBackoffSec + "s...");
                                    sleepSeconds(rj.cfg.retryBackoffSec);
                                }
                            }
                        }
                        appendLog(stageLog, "[STEP END] sh '" + step.command + "' | Duration: " + (System.currentTimeMillis() - stepStart) + " ms");
                        if (!ok) { stageOk = false; break; }
                    }
                }

                long stageDuration = System.currentTimeMillis() - stageStart;
                if (!stageOk) {
                    appendLog(stageLog, "<<< FAIL Stage: " + stage.name + " | Duration: " + stageDuration + " ms");
                    status.state = rj.cancelRequested.get() ? JobState.CANCELED : JobState.FAILED;
                    break;
                } else {
                    appendLog(stageLog, "<<< SUCCESS Stage: " + stage.name + " | Duration: " + stageDuration + " ms");
                    completedStages.add(stage.name);
                    Checkpoint.save(rj.checkpointFile, completedStages);
                }
            }

            if (status.state == JobState.RUNNING) {
                status.state = JobState.SUCCESS;
            }
        } catch (Exception ex) {
            status.state = JobState.FAILED;
            status.error = ex.getMessage();
        } finally {
            status.endedAt = Instant.now();
            long jobDuration = status.startedAt != null ? (status.endedAt.toEpochMilli() - status.startedAt.toEpochMilli()) : 0;
            appendLog(jobLog, "=== JOB END (" + status.state + ") | Duration: " + jobDuration + " ms ===");
            status.write(rj.statusFile);
            hb.stop();
            jobs.remove(rj.jobId);
        }
    }

    // ---------- Process exec with streaming logs & rotation ----------

    private int execStreaming(RunningJob rj, String command, Path stageLog, Duration timeout) {
        ProcessBuilder pb;
        if (isWindows()) pb = new ProcessBuilder("cmd.exe", "/c", command);
        else             pb = new ProcessBuilder("bash", "-c", command);
        pb.directory(rj.workspace.toFile());

        try {
            Process p = pb.start();
            rj.currentProcess.set(p);

            CountDownLatch latch = new CountDownLatch(2);
            StreamGobbler outG = new StreamGobbler(p.getInputStream(), stageLog, rj.cfg.maxLogBytes, "[STDOUT]", latch);
            StreamGobbler errG = new StreamGobbler(p.getErrorStream(), stageLog, rj.cfg.maxLogBytes, "[STDERR]", latch);
            outG.start(); errG.start();

            boolean finished = p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!finished) {
                appendLog(stageLog, "[TIMEOUT] Killing process after " + timeout.toMinutes() + " min (PID=" + p.pid() + ")");
                p.destroy();
                if (p.isAlive()) {
                    sleepSeconds(3);
                    p.destroyForcibly();
                }
            }

            latch.await();
            return finished ? p.exitValue() : 124;
        } catch (Exception e) {
            appendLog(stageLog, "[ERROR] execStreaming crash: " + e.getMessage());
            return 1;
        } finally {
            rj.currentProcess.set(null);
        }
    }

    // ---------- Helpers ----------

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void sleepSeconds(long s) {
        try { TimeUnit.SECONDS.sleep(s); } catch (InterruptedException ignored) {}
    }

    private static String safe(String name) {
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static void appendLog(Path file, String line) {
        try {
            String ts = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now());
            Files.createDirectories(file.getParent());
            Files.writeString(file, "[" + ts + "] " + line + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }

    // ---------- Data structures ----------

    private static final class RunningJob {
        final String jobId;
        final Path workspace, runDir, logsDir, statusFile, checkpointFile;
        final Pipeline pipeline;
        final RunConfig cfg;
        final AtomicBoolean cancelRequested = new AtomicBoolean(false);
        final AtomicReference<Process> currentProcess = new AtomicReference<>(null);

        RunningJob(String jobId, Path workspace, Path runDir, Path logsDir, Path status, Path checkpoint,
                   Pipeline pipeline, RunConfig cfg) {
            this.jobId = jobId;
            this.workspace = workspace;
            this.runDir = runDir;
            this.logsDir = logsDir;
            this.statusFile = status;
            this.checkpointFile = checkpoint;
            this.pipeline = pipeline;
            this.cfg = cfg;
        }
    }

    private static final class StreamGobbler extends Thread {
        private final InputStream in;
        private final Path file;
        private final long maxBytes;
        private final String prefix;
        private final CountDownLatch latch;

        StreamGobbler(InputStream in, Path file, long maxBytes, String prefix, CountDownLatch latch) {
            this.in = in; this.file = file; this.maxBytes = maxBytes; this.prefix = prefix; this.latch = latch;
            setName("StreamGobbler-" + prefix);
            setDaemon(true);
        }

        @Override public void run() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    rotateIfNeeded(file, maxBytes);

                    String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            .format(LocalDateTime.now());
                    String formatted = "[" + ts + "][" + prefix + "] " + line;

                    // --- Persist to log file
                    appendLog(file, formatted);

                    // --- Mirror to console (stdout/stderr separated)
                    if ("STDOUT".equals(prefix)) {
                        System.out.println(formatted);
                    } else {
                        System.err.println(formatted);
                    }
                }
            } catch (IOException ignored) {
            } finally { latch.countDown(); }
        }

        private void rotateIfNeeded(Path file, long maxBytes) {
            try {
                if (Files.exists(file) && Files.size(file) >= maxBytes) {
                    Path rotated = Paths.get(file.toString() + "." +
                            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(LocalDateTime.now()));
                    Files.move(file, rotated, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (IOException ignored) {}
        }
    }

    private static final class Heartbeat {
        private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        private final Path statusFile;
        private final long periodSec;
        private final Supplier<Status> snapshot;

        Heartbeat(Path statusFile, long periodSec, Supplier<Status> snapshot) {
            this.statusFile = statusFile; this.periodSec = periodSec; this.snapshot = snapshot;
        }

        void start() { ses.scheduleAtFixedRate(() -> snapshot.get().write(statusFile), 0, periodSec, TimeUnit.SECONDS); }
        void stop()  { ses.shutdownNow(); }
    }

    private static final class Status {
        String jobId;
        JobState state = JobState.PENDING;
        String workspace;
        String currentStage;
        String currentStep;
        Instant startedAt;
        Instant endedAt;
        String error;

        static Status initial(String jobId, String workspace) {
            Status s = new Status();
            s.jobId = jobId; s.workspace = workspace;
            return s;
        }

        public Status clone() {
            Status s = new Status();
            s.jobId = jobId; s.state = state; s.workspace = workspace;
            s.currentStage = currentStage; s.currentStep = currentStep;
            s.startedAt = startedAt; s.endedAt = endedAt; s.error = error;
            return s;
        }

        void write(Path f) {
            try {
                String json = "{"
                        + "\"jobId\":\"" + esc(jobId) + "\","
                        + "\"state\":\"" + state + "\","
                        + "\"workspace\":\"" + esc(workspace) + "\","
                        + "\"currentStage\":\"" + esc(nullToEmpty(currentStage)) + "\","
                        + "\"currentStep\":\"" + esc(nullToEmpty(currentStep)) + "\","
                        + "\"startedAt\":\"" + (startedAt == null ? "" : startedAt) + "\","
                        + "\"endedAt\":\"" + (endedAt == null ? "" : endedAt) + "\","
                        + "\"error\":\"" + esc(nullToEmpty(error)) + "\""
                        + "}";
                Files.createDirectories(f.getParent());
                Files.writeString(f, json, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {}
        }

        private static String esc(String s) {
            return s.replace("\\","\\\\").replace("\"","\\\"");
        }
        private static String nullToEmpty(String s) { return s == null ? "" : s; }
    }

    // ---------- Checkpoint persistence ----------
    private static final class Checkpoint {
        static Set<String> load(Path f) {
            try {
                if (!Files.exists(f)) return new HashSet<>();
                String json = Files.readString(f, StandardCharsets.UTF_8);
                String[] lines = json.split("\\R");
                return new HashSet<>(Arrays.asList(lines));
            } catch (IOException e) {
                return new HashSet<>();
            }
        }

        static void save(Path f, Set<String> completed) {
            try {
                Files.createDirectories(f.getParent());
                Files.write(f, String.join(System.lineSeparator(), completed).getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    // ---------- Pipeline DSL ----------
    private static final class Pipeline {
        final List<Stage> stages = new ArrayList<>();

        static final class Stage {
            final String name;
            final List<Step> steps = new ArrayList<>();
            Stage(String name) { this.name = name; }
        }

        static final class Step {
            final String type;   // "echo" | "sh"
            final String command;
            Step(String type, String command) { this.type = type; this.command = command; }
        }

        static Pipeline parse(String def) {
            Pipeline p = new Pipeline();
            Pattern stagePat = Pattern.compile("stage\\s*\\(\\s*['\"](.*?)['\"]\\s*\\)\\s*\\{([\\s\\S]*?)\\}");
            Matcher sm = stagePat.matcher(def);
            while (sm.find()) {
                String name = sm.group(1);
                String body = sm.group(2);
                Stage st = new Stage(name);

                Matcher stepsM = Pattern.compile("steps\\s*\\{([\\s\\S]*?)\\}").matcher(body);
                if (stepsM.find()) {
                    String stepsBody = stepsM.group(1);
                    Matcher cmdM = Pattern.compile("(echo|sh)\\s+['\"]([\\s\\S]*?)['\"]").matcher(stepsBody);
                    while (cmdM.find()) {
                        st.steps.add(new Step(cmdM.group(1), cmdM.group(2)));
                    }
                }
                p.stages.add(st);
            }
            if (p.stages.isEmpty()) {
                throw new IllegalArgumentException("No stages parsed from pipeline definition");
            }
            return p;
        }
    }

    // ---------- Demo Main ----------
    public static void main(String[] args) throws Exception {
        RobustPipelineExecutor exec = new RobustPipelineExecutor(Paths.get("runs"));

        // Payload workspace
        Payload payload = new Payload();
        payload.setName("ETL Job");
        payload.setPath("D:/lap/java/dsa/path/to"); // <-- change to your workspace

        // Jenkins-like pipeline
        String pipeline =
                "stage('Extract'){ steps { echo 'Starting Extract'; sh 'python extract.py' } }\n" +
                        "stage('Transform'){ steps { echo 'Starting Transform'; sh 'python transform.py' } }\n" +
                        "stage('Load'){ steps { echo 'Starting Load'; sh 'python load.py' } }";

        RunConfig cfg = new RunConfig();
        cfg.stepTimeoutMinutes = 60;
        cfg.stepMaxRetries = 2;
        cfg.retryBackoffSec = 15;
        cfg.heartbeatSec = 5;
        cfg.maxLogBytes = 50L * 1024 * 1024;

        SubmitResult sr = exec.submit(payload, pipeline, cfg);
        if (!sr.accepted) {
            System.out.println("Submit failed: " + sr.message);
            return;
        }

        System.out.println("JobId: " + sr.handle.jobId);
        System.out.println("RunDir: " + sr.handle.runDir);
        System.out.println("Status: " + sr.handle.statusFile);
        System.out.println("Checkpoint: " + sr.handle.checkpointFile);
        System.out.println("Logs: " + sr.handle.runDir.resolve("logs"));

        for (int i = 0; i < 10; i++) {
            Thread.sleep(3000);
            exec.readStatus(sr.handle.jobId).ifPresent(System.out::println);
        }

        // Example cancel (optional)
        // exec.cancel(sr.handle.jobId);
    }
}
