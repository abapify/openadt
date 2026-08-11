package org.openadt.sap.adt.sdk;

/** One caller-owned, stateful ADT SDK session. */
public interface StatefulAdtTransportSession extends AutoCloseable {
    ProxyResponse execute(ProxyRequest request);

    @Override
    void close();
}
