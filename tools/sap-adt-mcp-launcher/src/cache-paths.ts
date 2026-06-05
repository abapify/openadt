import { join } from "node:path";

/** Eclipse semantic cache suffix used by adt-lsc for project metadata. */
export const SEMANTIC_CACHE_SUFFIX = join(
  ".metadata",
  ".plugins",
  "org.eclipse.core.resources.semantic",
  ".cache",
);

/** Eclipse `.destination.properties` file name (lives under each project dir). */
export const DESTINATION_FILE = ".destination.properties";
