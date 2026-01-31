#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("io.github.typesafegithub:github-workflows-kt:3.7.0")
@file:Repository("https://bindings.krzeminski.it")
@file:DependsOn("actions:checkout:v4")
@file:DependsOn("actions:setup-java:v4")
@file:DependsOn("actions:setup-node:v4")

import io.github.typesafegithub.workflows.actions.actions.Checkout
import io.github.typesafegithub.workflows.actions.actions.SetupJava
import io.github.typesafegithub.workflows.actions.actions.SetupNode
import io.github.typesafegithub.workflows.domain.RunnerType
import io.github.typesafegithub.workflows.domain.triggers.WorkflowCall
import io.github.typesafegithub.workflows.dsl.expressions.Contexts
import io.github.typesafegithub.workflows.dsl.expressions.expr
import io.github.typesafegithub.workflows.dsl.workflow
import io.github.typesafegithub.workflows.yaml.ConsistencyCheckJobConfig

val GITHUB_TOKEN by Contexts.secrets

workflow(
    name = "Prepare Next Development Cycle",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "backend_folder" to WorkflowCall.Input(
                    description = "The backend folder to build",
                    required = false,
                    default = "backend",
                    type = WorkflowCall.Type.String,
                ),
                "frontend_folder" to WorkflowCall.Input(
                    description = "The frontend folder to build",
                    required = false,
                    default = "frontend",
                    type = WorkflowCall.Type.String,
                ),
            ),
            secrets = mapOf(
                "GH_TOKEN" to WorkflowCall.Secret(
                    description = "GitHub token with permissions to create branches and pull requests",
                    required = true,
                ),
            ),
        ),
    ),
    sourceFile = __FILE__,
) {
    job(
        id = "prepare-next-dev-cycle",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        run(
            name = "Print event name",
            command = "echo Event name: " + expr { github.event_name }
        )
        uses(
            name = "Checkout",
            action = Checkout(
                ref = "master",
                fetchDepth = Checkout.FetchDepth.Value(0),
            )
        )

        run(
            name = "Check if develop branch exists",
            command = """
                if git ls-remote --exit-code --heads origin develop; then
                  echo "Branch 'develop' already exists. Cancelling workflow."
                  exit 1
                fi
            """.trimIndent(),
        )

        uses(
            name = "Setup Java",
            action = SetupJava(
                distribution = SetupJava.Distribution.Temurin,
                cache = SetupJava.BuildPlatform.Maven,
                javaVersionFile = expr { "inputs.backend_folder" } + "/.java-version",
            ),
        )

        uses(
            name = "Setup Node",
            action = SetupNode(
                nodeVersionFile = expr { "inputs.frontend_folder" } + "/.nvmrc"
            ),
        )

        run(
            name = "Configure Git",
            command = """
                git config user.name "github-actions[bot]"
                git config user.email "github-actions[bot]@users.noreply.github.com"
            """.trimIndent(),
        )

        run(
            name = "Create develop branch",
            command = """
                BRANCH_NAME="develop"
                git checkout -b "${'$'}BRANCH_NAME"
                echo "BRANCH_NAME=${'$'}BRANCH_NAME" >> "${'$'}GITHUB_ENV"
            """.trimIndent(),
        )

        run(
            name = "Bump backend version",
            command = """
                # Use input or default to 'backend'
                BACKEND_FOLDER="${'$'}{{ inputs.backend_folder || 'backend' }}"
                cd ${'$'}BACKEND_FOLDER
                
                # Use Maven versions plugin to bump patch version
                mvn versions:set -DnextSnapshot=true
                mvn versions:commit
            """.trimIndent(),
        )

        run(
            name = "Bump frontend version",
            command = """
                # Use input or default to 'frontend'
                FRONTEND_FOLDER="${'$'}{{ inputs.frontend_folder || 'frontend' }}"
                cd ${'$'}FRONTEND_FOLDER
                npm version prerelease --preid=snapshot --no-git-tag-version
            """.trimIndent(),
        )

        run(
            name = "Commit changes",
            command = """
                git add .
                if git diff --staged --quiet; then
                  echo "No changes to commit"
                  exit 0
                fi
                git commit -m "chore: prepare next development cycle"
            """.trimIndent(),
        )

        run(
            name = "Push branch",
            command = """
                git push origin "${'$'}BRANCH_NAME"
            """.trimIndent(),
        )

        run(
            name = "Create Pull Request",
            env = mapOf(
                "GH_TOKEN" to expr { GITHUB_TOKEN }
            ),
            command = """
                gh pr create \
                  --title "chore: prepare next development cycle" \
                  --body "Automated PR to bump versions after release tag ${'$'}{{ github.ref_name }}" \
                  --base master \
                  --head "${'$'}BRANCH_NAME"
            """.trimIndent(),
        )
    }
}
