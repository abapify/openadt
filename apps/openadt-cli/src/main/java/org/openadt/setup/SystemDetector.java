package org.openadt.setup;

import org.openadt.core.SystemProfile;
import java.util.List;

public interface SystemDetector {
    List<SystemProfile> detect();
}
