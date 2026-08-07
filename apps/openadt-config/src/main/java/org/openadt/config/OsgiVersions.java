package org.openadt.config;

/**
 * Compares OSGi bundle versions of the form {@code major.minor.micro[.qualifier]} as used by Eclipse
 * p2 bundle file names, for example {@code 3.34.100.v20251111-1421} or {@code 1.1.2.202109301733}.
 *
 * <p>The three numeric segments are compared numerically (a missing segment counts as {@code 0}) so
 * that {@code 3.24.100} sorts above {@code 3.24.0}, which string comparison would get wrong. The
 * qualifier is compared lexicographically only as a tiebreaker.
 */
public final class OsgiVersions {
    private static final int NUMERIC_SEGMENTS = 3;

    private OsgiVersions() {
    }

    public static int compare(String left, String right) {
        String leftVersion = left == null ? "" : left;
        String rightVersion = right == null ? "" : right;

        String[] leftParts = leftVersion.split("\\.", NUMERIC_SEGMENTS + 1);
        String[] rightParts = rightVersion.split("\\.", NUMERIC_SEGMENTS + 1);

        for (int i = 0; i < NUMERIC_SEGMENTS; i++) {
            int comparison = Integer.compare(numericAt(leftParts, i), numericAt(rightParts, i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return qualifierAt(leftParts).compareTo(qualifierAt(rightParts));
    }

    private static int numericAt(String[] parts, int index) {
        if (index >= parts.length) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[index].trim());
        } catch (NumberFormatException notNumeric) {
            return 0;
        }
    }

    private static String qualifierAt(String[] parts) {
        return parts.length > NUMERIC_SEGMENTS ? parts[NUMERIC_SEGMENTS] : "";
    }
}
