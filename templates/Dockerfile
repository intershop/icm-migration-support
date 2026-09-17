# Agent container for an ICM 7.10 to current migration.
#
# Everything the icm-migration playbook needs is installed here, so the agent can work without asking
# for a tool it does not have. What it cannot install is in docker-compose.yml: the mounts, and the
# artifact feed credentials that decide whether the agent can build at all.
#
# See skills/icm-migration/references/agent-environment.md for what each item is for and what its
# absence costs.

FROM eclipse-temurin:21-jdk-alpine

# git       the migration tool commits through JGit, one commit per step
# bash      the playbook's shell snippets assume it
# curl      fetching sources, probing a running ICM
# unzip     Kotlin, and inspecting jars in the Gradle cache
# jq        reading the migration tool's operations.jsonl
# ripgrep   fast search over a large read-only sources mount
# python3   the migration tooling: inventory, the rename corpus, the duplicate-key scanner,
#           the ICM HTTP client. All but one use only the standard library.
# py3-yaml  the single third-party Python dependency, from the distro rather than pip so that
#           PEP 668 does not force --break-system-packages
RUN apk add --no-cache \
        bash git openssh-client curl unzip jq ripgrep \
        nodejs npm \
        python3 py3-pip py3-yaml

# Kotlin. Step 070 of the 7.10-to-11 set shells out to a Kotlin script. Without it that step fails
# once per cartridge: 36 failed operations on the reference project, every one of them noise.
# Note for Windows hosts: the tool invokes "kotlin.bat" literally, without applying PATHEXT, so a
# working `kotlin` command there proves nothing. Inside this image there is no such problem.
ARG KOTLIN_VERSION=2.4.20
RUN set -eux; \
    base="https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}"; \
    curl -fsSL -o /tmp/kotlin.zip "${base}/kotlin-compiler-${KOTLIN_VERSION}.zip"; \
    curl -fsSL -o /tmp/kotlin.sha256 "${base}/kotlin-compiler-${KOTLIN_VERSION}.zip.sha256"; \
    echo "$(cut -d' ' -f1 /tmp/kotlin.sha256)  /tmp/kotlin.zip" | sha256sum -c -; \
    unzip -q /tmp/kotlin.zip -d /opt; \
    rm /tmp/kotlin.zip /tmp/kotlin.sha256
ENV PATH="/opt/kotlinc/bin:${PATH}"

# ---------------------------------------------------------------------------
# The agent itself. Everything above is agent-agnostic; this is the only part
# to change if you drive the migration with something other than Claude Code.
# ---------------------------------------------------------------------------
RUN npm install -g @anthropic-ai/claude-code

WORKDIR /workspace

# Fail early and loudly rather than halfway through a migration.
RUN java -version && kotlin -version && python3 -c "import yaml" && git --version

CMD ["claude"]
