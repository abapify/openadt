package org.openadt.core;

public interface AdtTransportClient {
    ProxyResponse execute(SystemProfile system, ProxyRequest request);
}
