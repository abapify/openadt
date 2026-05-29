package org.openadt.sap.adt.sdk;

import org.openadt.config.SystemProfile;

public interface AdtTransportClient {
    ProxyResponse execute(SystemProfile system, ProxyRequest request);
}
