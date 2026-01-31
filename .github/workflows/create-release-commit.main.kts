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
    name = "Create Release Commit",
    consistencyCheckJobConfig = ConsistencyCheckJobConfig.Disabled,
    on = listOf(
        WorkflowCall(
            inputs = mapOf(
                "backend_folder" to WorkflowCall.Input(
                    description = "The backend folder",
                    required = false,
                    default = "backend",
                    type = WorkflowCall.Type.String,
                ),
                "frontend_folder" to WorkflowCall.Input(
                    description = "The frontend folder",
                    required = false,
                    default = "frontend",
                    type = WorkflowCall.Type.String,
                ),
                "backend_bump_type" to WorkflowCall.Input(
                    description = "Bump type for backend (major, minor, patch)",
                    required = false,
                    default = "patch",
                    type = WorkflowCall.Type.String,
                ),
                "frontend_bump_type" to WorkflowCall.Input(
                    description = "Bump type for frontend (major, minor, patch)",
                    required = false,
                    default = "patch",
                    type = WorkflowCall.Type.String,
                ),
            ),
            secrets = mapOf(
                "GH_TOKEN" to WorkflowCall.Secret(
                    description = "GitHub token to push changes",
                    required = true,
                ),
            ),
        ),
    ),
    sourceFile = __FILE__,
) {
    job(
        id = "create-release-commit",
        runsOn = RunnerType.UbuntuLatest,
    ) {
        uses(
            name = "Checkout",
            action = Checkout(
                fetchDepth = Checkout.FetchDepth.Value(0),
            )
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
            name = "Bump backend version",
            command = """
                cd "${'$'}{{ inputs.backend_folder }}"
                BUMP_TYPE="${'$'}{{ inputs.backend_bump_type }}"
                
                # Get current version (e.g., 1.2.3-SNAPSHOT)
                FULL_VERSION=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
                
                # Fix SC2001 & SC2086: Use Bash parameter expansion to strip suffix
                # This is faster and safer than echo | sed
                CURRENT_VERSION="${'$'}{FULL_VERSION%-SNAPSHOT}"
                
                IFS='.' read -ra ADDR <<< "${'$'}{CURRENT_VERSION}"
                MAJOR="${'$'}{ADDR[0]}"
                MINOR="${'$'}{ADDR[1]}"
                PATCH="${'$'}{ADDR[2]}"

                if [ "${'$'}BUMP_TYPE" == "major" ]; then
                  NEW_VERSION="$((MAJOR+1)).0.0"
                elif [ "${'$'}BUMP_TYPE" == "minor" ]; then
                  NEW_VERSION="${'$'}{MAJOR}.$((MINOR+1)).0"
                else
                  # For 'patch', we use the current version numbers without the SNAPSHOT suffix
                  NEW_VERSION="${'$'}{MAJOR}.${'$'}{MINOR}.${'$'}{PATCH}"
                fi

                echo "Releasing backend version: ${'$'}{NEW_VERSION} (from ${'$'}{FULL_VERSION})"
                mvn versions:set -DnewVersion="${'$'}{NEW_VERSION}" -DgenerateBackupPoms=false
                mvn versions:commit
            """.trimIndent(),
        )

        run(
            name = "Bump frontend version",
            command = """
                cd ${"$"}{{ inputs.frontend_folder }}
                echo "Bumping frontend via npm version ${"$"}{{ inputs.frontend_bump_type }}"
                npm version ${"$"}{{ inputs.frontend_bump_type }} --no-git-tag-version
            """.trimIndent(),
        )

        run(
            name = "Commit and Push",
            env = mapOf(
                "GH_TOKEN" to expr { GITHUB_TOKEN }
            ),
            command = """
                git add .
                if git diff --staged --quiet; then
                  echo "No changes to commit"
                  exit 0
                fi
                git commit -m "chore: release version bump"
                git push origin HEAD:${"$"}{{ github.ref_name }}
            """.trimIndent(),
        )
    }
}