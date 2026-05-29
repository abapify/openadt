FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# Base tools + gh CLI repo key
RUN rm -f /etc/apt/sources.list.d/yarn.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends \
        maven \
        ripgrep \
        unzip \
        zip \
        curl \
        jq \
        python3 \
        python3-pip \
        gnupg \
    && rm -rf /var/lib/apt/lists/*

# GitHub CLI
RUN curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
        | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
    && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
        > /etc/apt/sources.list.d/github-cli.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends gh \
    && rm -rf /var/lib/apt/lists/*

# .NET SDK (release packaging parity with the Windows CI runner)
RUN curl -fsSL https://dot.net/v1/dotnet-install.sh \
        | bash -s -- --channel 8.0 --install-dir /usr/local/dotnet \
    && ln -s /usr/local/dotnet/dotnet /usr/local/bin/dotnet

# Node.js LTS + npm
RUN curl -fsSL https://deb.nodesource.com/setup_lts.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/*

# Bun (JS runtime / package manager used by all scripts and nx)
RUN curl -fsSL https://bun.sh/install | bash \
    && ln -s /root/.bun/bin/bun /usr/local/bin/bun
