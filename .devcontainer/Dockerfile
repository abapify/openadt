FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ARG NODE_MAJOR=22

# Base tools + gh CLI repo key
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        maven \
        ripgrep \
        wget \
        unzip \
        zip \
        curl \
        jq \
        python3 \
        python3-pip \
        gnupg \
    && rm -rf /var/lib/apt/lists/*

# GitHub CLI
RUN curl -fsSL -o /usr/share/keyrings/githubcli-archive-keyring.gpg https://cli.github.com/packages/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends gh \
    && rm -rf /var/lib/apt/lists/*

# .NET SDK (release packaging parity with the Windows CI runner)
RUN curl -fsSL -o /tmp/dotnet-install.sh https://dot.net/v1/dotnet-install.sh \
    && bash /tmp/dotnet-install.sh --channel 8.0 --install-dir /usr/local/dotnet \
    && rm /tmp/dotnet-install.sh \
    && ln -s /usr/local/dotnet/dotnet /usr/local/bin/dotnet

# Node.js LTS + npm
RUN mkdir -p /etc/apt/keyrings \
    && curl -fsSL -o /tmp/nodesource-repo.gpg.key https://deb.nodesource.com/gpgkey/nodesource-repo.gpg.key \
    && gpg --dearmor -o /etc/apt/keyrings/nodesource.gpg /tmp/nodesource-repo.gpg.key \
    && rm /tmp/nodesource-repo.gpg.key \
    && chmod go+r /etc/apt/keyrings/nodesource.gpg \
    && echo "deb [signed-by=/etc/apt/keyrings/nodesource.gpg] https://deb.nodesource.com/node_${NODE_MAJOR}.x nodistro main" \
        > /etc/apt/sources.list.d/nodesource.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Bun (JS runtime / package manager used by all scripts and nx)
ENV BUN_INSTALL=/usr/local/bun
RUN curl -fsSL -o /tmp/bun-install.sh https://bun.sh/install \
    && bash /tmp/bun-install.sh \
    && rm /tmp/bun-install.sh \
    && ln -s /usr/local/bun/bin/bun /usr/local/bin/bun
