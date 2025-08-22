package com.watcher.utils;

import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import com.cronutils.model.CronType;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class CronMetadataExtractor {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT+0530 (India Standard Time)'", Locale.US);

    public static List<String> getFormattedExecutionTimes(String cronExpression, int count) {
        try {
            List<ZonedDateTime> rawTimes = getExecutionTimes(cronExpression, count);
            if (rawTimes == null) {
                return Collections.singletonList("Invalid expression: " + cronExpression);
            }
            if (rawTimes.isEmpty()) {
                return Collections.singletonList("Expired expression: " + cronExpression);
            }

            List<String> formatted = new ArrayList<>();
            for (ZonedDateTime time : rawTimes) {
                formatted.add(FORMATTER.format(time));
            }
            return formatted;

        } catch (Exception e) {
            return Collections.singletonList("Invalid expression: " + cronExpression);
        }
    }

    public static List<ZonedDateTime> getExecutionTimes(String cronExpression, int count) {
        List<ZonedDateTime> result = tryParse(cronExpression, count, CronType.UNIX);

        if (result == null || result.isEmpty()) {
            result = tryParse(cronExpression, count, CronType.QUARTZ);
        }

        return result;
    }

    public static List<Map.Entry<String, LocalDateTime>> getFormattedAndLocalExecutionTimes(String cronExpression, int count) {
        List<ZonedDateTime> rawTimes = getExecutionTimes(cronExpression, count);
        List<Map.Entry<String, LocalDateTime>> results = new ArrayList<>();

        if (rawTimes == null || rawTimes.isEmpty()) {
            results.add(new AbstractMap.SimpleEntry<>("Invalid or expired", null));
            return results;
        }

        for (ZonedDateTime zdt : rawTimes) {
            String formatted = FORMATTER.format(zdt);
            LocalDateTime local = zdt.toLocalDateTime();
            results.add(new AbstractMap.SimpleEntry<>(formatted, local));
        }

        return results;
    }

    private static List<ZonedDateTime> tryParse(String cronExpression, int count, CronType cronType) {
        try {
            CronDefinition definition = CronDefinitionBuilder.instanceDefinitionFor(cronType);
            CronParser parser = new CronParser(definition);
            Cron cron = parser.parse(cronExpression);
            cron.validate();

            ExecutionTime executionTime = ExecutionTime.forCron(cron);
            ZonedDateTime now = ZonedDateTime.now(ZONE_ID);

            List<ZonedDateTime> results = new ArrayList<>();
            Optional<ZonedDateTime> next = executionTime.nextExecution(now);

            for (int i = 0; i < count && next.isPresent(); i++) {
                results.add(next.get());
                next = executionTime.nextExecution(next.get());
            }

            return results;
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        String[] testExpressions = {
                "0 0 10 ? 1/6 SUN *"     // Expired
//                "0 30 5 * * ?",               // Quartz style (valid)
//                "30 5 * * *",                 // Unix style (valid)
//                "0 */15 9-17 * * ?",          // Quartz (valid)
//                "invalid cron"               // Invalid
        };

        for (String cron : testExpressions) {
            System.out.println("Input: " + cron);
            List<Map.Entry<String, LocalDateTime>> pairs = getFormattedAndLocalExecutionTimes(cron, 5);
            for (Map.Entry<String, LocalDateTime> entry : pairs) {
                System.out.println("→ Formatted: " + entry.getKey() + ", LocalDateTime: " + entry.getValue());
            }
            System.out.println("------------------------");
        }
    }
}
