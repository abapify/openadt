package org.openadt.bootstrap;

import org.openadt.config.SystemProfile;
import java.util.List;

public interface SystemDetector {
    List<SystemProfile> detect();
}
