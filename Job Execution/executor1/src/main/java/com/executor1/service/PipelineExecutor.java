package com.executor1.service;

import com.executor1.entities2.Payload;
import com.executor1.entities2.RunStatus;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

public class PipelineExecutor {

    public void execute(Payload payload) {
        // Create log file per execution
        String logFileName = "logs/" + payload.getJobId() + "-" + payload.getAttemptNumber() + ".log";
        try {
            Files.createDirectories(Paths.get("logs"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        File logFile = new File(logFileName);

        try (BufferedWriter logWriter = new BufferedWriter(new FileWriter(logFile, true))) {
            logWriter.write("=== Starting pipeline for Job " + payload.getJobId() + " at " + LocalDateTime.now() + " ===\n");

            payload.setStatus(RunStatus.RUNNING);
            payload.setStartTime(LocalDateTime.now());

            // Read environment variables from pipeline file
            Map<String, String> env = parsePipelineFile(payload.getPath());

            runStage("Extract", env.get("PYTHON"), env.get("EXTRACT_FILE"), logWriter);
            runStage("Transform", env.get("PYTHON"), env.get("TRANSFORM_FILE"), logWriter);
            runStage("Load", env.get("PYTHON"), env.get("LOAD_FILE"), logWriter);

            payload.setStatus(RunStatus.SUCCESS);
            payload.setEndTime(LocalDateTime.now());
            logWriter.write("✅ Pipeline executed successfully\n");

        } catch (Exception e) {
            payload.setStatus(RunStatus.FAILED);
            payload.setEndTime(LocalDateTime.now());
            try (BufferedWriter logWriter = new BufferedWriter(new FileWriter(logFile, true))) {
                logWriter.write("❌ Pipeline failed: " + e.getMessage() + "\n");
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            // Also print to console
            System.err.println("❌ Pipeline failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void runStage(String stageName, String python, String script, BufferedWriter logWriter) throws IOException, InterruptedException {
        logWriter.write("\n--- Stage: " + stageName + " ---\n");
        logWriter.flush();
        System.out.println("▶ Running stage: " + stageName);

        ProcessBuilder builder = new ProcessBuilder(python, script);
        builder.redirectErrorStream(true);
        Process process = builder.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logWriter.write(line + "\n");
                logWriter.flush();
                System.out.println(line); // also print to console
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMsg = stageName + " failed with exit code " + exitCode;
            logWriter.write("❌ " + errorMsg + "\n");
            logWriter.flush();
            System.err.println(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        logWriter.write("✅ " + stageName + " completed successfully\n");
        logWriter.flush();
    }

    // Very simple parser for Jenkinsfile-like environment section
    private Map<String, String> parsePipelineFile(String path) throws IOException {
        Map<String, String> env = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        for (String line : lines) {
            line = line.trim();
            if (line.contains("=")) {
                String[] parts = line.split("=");
                if (parts.length == 2) {
                    env.put(parts[0].replaceAll("[^A-Z_]", ""), parts[1].replace("\"", "").trim());
                }
            }
        }
        return env;
    }

    // Main method for testing
    public static void main(String[] args) {
        Payload payload = new Payload();
        payload.setJobId(101L);
        payload.setAttemptNumber(1);
        payload.setPath("pipeline.groovy"); // your Jenkins-like file path

        PipelineExecutor executor = new PipelineExecutor();
        executor.execute(payload);

        System.out.println("Final Status: " + payload.getStatus());
    }
}
