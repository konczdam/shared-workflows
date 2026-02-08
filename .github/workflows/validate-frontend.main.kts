#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("actions:setup-node:v4")
@file:DependsOn("actions:cache:v4")
@file:DependsOn("actions:github-script:v7")

import io.github.typesafegithub.workflows.actions.actions.Cache
import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.GithubScript
import io.github.typesafegithub.workflows.actions.actions.SetupNode
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

workflow(
    name = "Validate Frontend",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "frontend_folder" to WorkflowCall.Input(
                    description = "Path to the frontend application",
                    required = false,
                    default = "frontend",
                    type = WorkflowCall.Type.String,
                ),
                "post_lint_comment" to WorkflowCall.Input(
                    description = "Whether to post a PR comment on lint failure ('true' or 'false')",
                    required = false,
                    default = "false",
                    type = WorkflowCall.Type.String,
                ),
                "install_command" to WorkflowCall.Input(
                    description = "Command to install dependencies",
                    required = false,
                    default = "npm ci",
                    type = WorkflowCall.Type.String,
                ),
                "build_command" to WorkflowCall.Input(
                    description = "Command to build the production app",
                    required = false,
                    default = "npm run build",
                    type = WorkflowCall.Type.String,
                ),
                "lint_command" to WorkflowCall.Input(
                    description = "Command to check for lint errors",
                    required = false,
                    default = "npm run lint-check",
                    type = WorkflowCall.Type.String,
                ),
            ),
        )
    ),
    sourceFile = __FILE__,
) {
    job(id = "validate", runsOn = RunnerType.UbuntuLatest) {
        uses(name = "Checkout", action = Checkout())

        // 1. Setup Node WITHOUT built-in cache
        uses(
            name = "Setup Node",
            action = SetupNode(
                nodeVersionFile = expr { "inputs.frontend_folder" } + "/.nvmrc",
            )
        )

        // 2. Manual NPM Cache with Fallback Keys
        uses(
            name = "Cache NPM dependencies",
            action = Cache(
                path = listOf("~/.npm"), // Standard path for NPM cache
                key = "npm-${expr { runner.os }}-${expr { "hashFiles(format('{0}/package-lock.json', inputs.frontend_folder))" }}",
                restoreKeys = listOf(
                    "npm-${expr { runner.os }}-"
                )
            )
        )

        run(
            name = "Install dependencies",
            workingDirectory = expr { "inputs.frontend_folder" },
            command = expr { "inputs.install_command" },
        )

        run(
            name = "Build production frontend",
            workingDirectory = expr { "inputs.frontend_folder" },
            command = expr { "inputs.build_command" },
        )

        run(
            id = "lint-step",
            name = "Run lint check",
            workingDirectory = expr { "inputs.frontend_folder" },
            command = expr { "inputs.lint_command" },
            continueOnError = true,

        )

        uses(
            name = "Comment on lint errors",
            condition = expr { "inputs.post_lint_comment == 'true' && steps.lint-step.outcome == 'failure'" },
            continueOnError = true,
            action = GithubScript(
                script = """
                    github.rest.issues.createComment({
                        issue_number: context.issue.number,
                        owner: context.repo.owner,
                        repo: context.repo.repo,
                        body: 'The frontend build passed, but there are lint errors. Please fix them before merging. You can use `npm run lint` to fix them automatically.'
                    })
                """.trimIndent(),
                githubToken = expr { "secrets.GITHUB_TOKEN" },
            )
        )
    }
}