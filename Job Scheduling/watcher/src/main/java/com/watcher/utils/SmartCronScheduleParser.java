package com.watcher.utils;

import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SmartCronScheduleParser {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");

    // Format like: Wed Jul 30 2025 05:30:00 GMT+0530 (India Standard Time)
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0530 (India Standard Time)'", Locale.US);

    public static List<String> getExecutionTimesWithFallback(String cronExpression, int count) {
        List<String> result = tryParse(cronExpression, count, detectCronType(cronExpression));

        // If parsing failed or empty list, fallback to Quartz
        if (result == null || result.isEmpty()) {
            result = tryParse(cronExpression, count, CronType.QUARTZ);
        }

        return result == null ? Collections.emptyList() : result;
    }

    private static List<String> tryParse(String cronExpression, int count, CronType cronType) {
        try {
            CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(cronType);
            CronParser parser = new CronParser(cronDefinition);
            Cron cron = parser.parse(cronExpression);
            cron.validate();

            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            ZonedDateTime now = ZonedDateTime.now(ZONE_ID);
            Optional<ZonedDateTime> next = executionTime.nextExecution(now);

            List<String> times = new ArrayList<>();
            for (int i = 0; i < count && next.isPresent(); i++) {
                ZonedDateTime nextTime = next.get();
                times.add(FORMATTER.format(nextTime));
                next = executionTime.nextExecution(nextTime);
            }

            return times;
        } catch (Exception e) {
            System.err.println("Failed parsing as " + cronType + ": " + e.getMessage());
            return null;
        }
    }

    private static CronType detectCronType(String cronExpression) {
        int fields = cronExpression.trim().split("\\s+").length;
        return (fields == 5) ? CronType.UNIX : CronType.QUARTZ;
    }

    public static void main(String[] args) {
        // 🚀 Try with either UNIX or QUARTZ style
        List<String> cronSamples = Arrays.asList(
                "0 30 5,6,7,8 * * ?",   // Quartz style: 5:30–8:30 AM daily
                "30 5 * * *",          // Unix style: 5:30 AM every day
                "0 0/15 9-17 * * MON-FRI", // Quartz style: Every 15 mins 9AM-5PM weekdays
                "invalid cron"         // To test graceful fallback
        );

        for (String cron : cronSamples) {
            System.out.println("⏰ Parsing: " + cron);
            List<String> times = getExecutionTimesWithFallback(cron, 5);
            if (times.isEmpty()) {
                System.out.println("❌ No valid schedule found.");
            } else {
                times.forEach(System.out::println);
            }
            System.out.println("----------------------------------------------------");
        }
    }
}
