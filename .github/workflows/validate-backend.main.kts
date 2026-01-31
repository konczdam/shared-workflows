#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("actions:setup-java:v4")
@file:DependsOn("actions:cache:v4")

import io.github.typesafegithub.workflows.actions.actions.Cache
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Validate Backend",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "backend_folder" to WorkflowCall.Input(
                    description = "Path to the backend application",
                    required = false,
                    default = "backend",
                    type = WorkflowCall.Type.String,
                ),
                "start_postgres" to WorkflowCall.Input(
                    description = "Whether to start and configure a local PostgreSQL instance (set to 'true' or 'false')",
                    required = false,
                    default = "false",
                    type = WorkflowCall.Type.String, // Changed to String
                ),
                "db_user" to WorkflowCall.Input(
                    description = "Database username",
                    required = false,
                    default = "admin",
                    type = WorkflowCall.Type.String,
                ),
                "db_password" to WorkflowCall.Input(
                    description = "Database password",
                    required = false,
                    default = "password",
                    type = WorkflowCall.Type.String,
                ),
                "db_name" to WorkflowCall.Input(
                    description = "Database name",
                    required = false,
                    default = "app_db",
                    type = WorkflowCall.Type.String,
                ),
                "run_linter" to WorkflowCall.Input(
                    description = "Whether to run the linter/ktlint (set to 'true' or 'false')",
                    required = false,
                    default = "true",
                    type = WorkflowCall.Type.String, // Changed to String
                ),
                "linter_command" to WorkflowCall.Input(
                    description = "The command to run for linting",
                    required = false,
                    default = "./mvnw ktlint:check",
                    type = WorkflowCall.Type.String,
                ),
                "build_command" to WorkflowCall.Input(
                    description = "The command to build the application",
                    required = false,
                    default = "./mvnw clean install -Denvironment=github_workflow",
                    type = WorkflowCall.Type.String,
                ),
            )
        )
    ),
    sourceFile = __FILE__,
) {
    job(id = "validate", runsOn = RunnerType.UbuntuLatest) {
        uses(name = "Checkout", action = Checkout())

        uses(
            name = "Setup Java",
            action = SetupJava(
                distribution = SetupJava.Distribution.Temurin,
                javaVersionFile = expr { "inputs.backend_folder" } + "/.java-version",
            ),
        )

        uses(
            name = "Cache Maven dependencies",
            action = Cache(
                path = listOf("~/.m2/repository"),
                key = "maven-${expr { runner.os }}-${expr { "hashFiles(format('{0}/**/pom.xml', inputs.backend_folder))" }}",
                restoreKeys = listOf(
                    "maven-${expr { runner.os }}-"
                )
            )
        )

        run(
            name = "Run Linter",
            // Condition now checks the string value explicitly
            condition = expr { "inputs.run_linter == 'true'" },
            workingDirectory = expr { "inputs.backend_folder" },
            command = expr { "inputs.linter_command" },
        )

        run(
            name = "Start and Configure PostgreSQL",
            // Condition now checks the string value explicitly
            condition = expr { "inputs.start_postgres == 'true'" },
            env = mapOf(
                "DB_USER" to expr { "inputs.db_user" },
                "DB_PASSWORD" to expr { "inputs.db_password" },
                "DB_NAME" to expr { "inputs.db_name" },
            ),
            command = """
                sudo systemctl start postgresql.service
                pg_isready
                
                # Configure Postgres
                sudo -u postgres createuser -s -d -r -w "${'$'}DB_USER"
                sudo -u postgres psql -c "ALTER ROLE ${'$'}DB_USER WITH PASSWORD '${'$'}DB_PASSWORD';"
                sudo -u postgres createdb -O "${'$'}DB_USER" "${'$'}DB_NAME"
                
                echo "PostgreSQL started and database '${'$'}DB_NAME' created for user '${'$'}DB_USER'"
            """.trimIndent()
        )

        run(
            name = "Build Backend",
            workingDirectory = expr { "inputs.backend_folder" },
            command = expr { "inputs.build_command" },
        )
    }
}