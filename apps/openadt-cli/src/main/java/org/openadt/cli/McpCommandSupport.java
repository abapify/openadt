package org.openadt.cli;

import java.util.ArrayList;
import java.util.List;

/** Shared helpers for the {@code openadt mcp *} subcommands. */
final class McpCommandSupport {
    private McpCommandSupport() {}

    /** Start a launcher-args builder seeded with the picocli {@code remainder}. */
    static ArgsBuilder launcherArgs(List<String> remainder) {
        return new ArgsBuilder(remainder);
    }

    static final class ArgsBuilder {
        private final List<String> args = new ArrayList<>();
        private final List<String> remainder;

        private ArgsBuilder(List<String> remainder) {
            this.remainder = remainder;
        }

        /** Append {@code --name value} when {@code value} is non-null. */
        ArgsBuilder option(String name, Object value) {
            if (value != null) {
                args.add(name);
                args.add(value.toString());
            }
            return this;
        }

        /** Append a bare flag when {@code on} is true. */
        ArgsBuilder flag(String name, boolean on) {
            if (on) {
                args.add(name);
            }
            return this;
        }

        String[] build() {
            args.addAll(remainder);
            return args.toArray(String[]::new);
        }
    }
}
