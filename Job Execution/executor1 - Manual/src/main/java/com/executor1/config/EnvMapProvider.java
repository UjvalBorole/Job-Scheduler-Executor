package com.executor1.config;

import java.util.LinkedHashMap;
import java.util.Map;

public class EnvMapProvider {

    public static Map<String, String> getEnvMap() {
        Map<String, String> envMap = new LinkedHashMap<>();

        // =========================
        // 1. Core Programming Languages
        // =========================
        String[] languages = {
                "PYTHON", "JAVA", "JAVASCRIPT", "TYPESCRIPT", "NODE", "RUBY", "R", "PHP", "GO", "SCALA",
                "KOTLIN", "DOTNET", "C", "CPP", "CSHARP", "SWIFT", "OBJECTIVEC", "PERL", "HASKELL",
                "ELIXIR", "F#", "LUA", "COBOL", "FORTRAN", "MATLAB", "SAS", "VB", "ASSEMBLY", "DART",
                "JULIA", "RUST", "CLOJURE", "ERLANG", "PROLOG", "SCHEME", "LISP", "ELM", "NIM", "SOLIDITY",
                "BASH", "POWERSHELL", "SQL", "PLSQL", "GROOVY"
        };
        for (String lang : languages) envMap.put(lang, lang.toLowerCase());

        // =========================
        // 2. ETL / Big Data / Data Engineering
        // =========================
        String[] dataTools = {
                "SPARK_SUBMIT", "HADOOP", "HIVE", "PIG", "FLINK", "BEAM", "PRESTO", "SNOWFLAKE", "TERADATA",
                "MYSQL", "POSTGRESQL", "ORACLESQL", "SQLSERVER", "REDIS", "MONGODB", "CASSANDRA", "COUCHBASE",
                "ELASTICSEARCH", "BIGQUERY", "REDSHIFT", "S3", "HDFS", "MINIO", "AZURE_BLOB", "GCS",
                "MONGOEXPORT", "MONGOIMPORT", "MYSQLDUMP", "PGDUMP", "DBT", "DATAPROC", "DATAPREP",
                "SNOWPIPE", "DATABRICKS"
        };
        for (String tool : dataTools) envMap.put(tool, tool.toLowerCase().replace("_", "-"));

        // =========================
        // 3. DevOps / CI-CD Tools / Containers / Orchestration
        // =========================
        String[] devopsTools = {
                "GIT", "MAVEN", "GRADLE", "ANT", "JENKINS", "TEAMCITY", "GITHUB_ACTIONS", "GITLAB_CI",
                "TRAVIS", "CIRCLECI", "TERRAFORM", "ANSIBLE", "PUPPET", "CHEF", "VAULT", "NEXUS",
                "ARTIFACTORY", "SONARQUBE", "KUBECTL", "HELM", "ISTIO", "ARGOCD", "FLUXCD", "PROMETHEUS",
                "GRAFANA", "LOKI", "ELASTICSEARCH_BEATS", "LOGSTASH", "FILEBEAT", "METRICS_SERVER", "KEDA",
                "OPENSHIFT_CLI", "RANCHER_CLI", "DOCKER", "DOCKER_COMPOSE", "MINIKUBE", "VAULT_CLI",
                "CONSUL_CLI", "FLUX"
        };
        for (String tool : devopsTools) envMap.put(tool, tool.toLowerCase().replace("_", "-"));

        // =========================
        // 4. Scripting / Automation / Misc Tools
        // =========================
        String[] scripting = {
                "AWK", "SED", "TCL", "EXPECT", "CURL", "WGET", "HTTPIE", "FTP", "SFTP", "SSH",
                "RSYNC", "MAKE", "CMAKE", "NPM", "YARN", "PNPM", "BOWER", "GULP", "GRUNT",
                "JQ", "K6", "LOADRUNNER", "SELENIUM", "CYPRESS", "POSTMAN", "NEWRELIC", "DATADOG",
                "ELASTICSTACK", "KIBANA", "PROMETHEUS_OPERATOR", "ZIPKIN", "JAEGER"
        };
        for (String tool : scripting) envMap.put(tool, tool.toLowerCase().replace("_", "-"));

        // =========================
        // 5. Placeholder entries up to 1000
        // =========================
        int counter = envMap.size();
        for (int i = counter; i < 1000; i++) {
            envMap.put("TOOL_" + i, "tool-" + i);
        }

        return envMap;
    }
}
