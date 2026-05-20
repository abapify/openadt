package org.openadt.core;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdtRestRfcClient {
    private static final String RFC_FUNCTION = "SADT_REST_RFC_ENDPOINT";

    private final JCoDestinationFactory destinationFactory;

    public AdtRestRfcClient(JCoDestinationFactory destinationFactory) {
        this.destinationFactory = destinationFactory;
    }

    public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
        try {
            Object destination = destinationFactory.getDestination(system);

            Method getRepoMethod = destination.getClass().getMethod("getRepository");
            Object repository = getRepoMethod.invoke(destination);

            Method getFuncMethod = repository.getClass().getMethod("getFunction", String.class);
            Object function = getFuncMethod.invoke(repository, RFC_FUNCTION);

            Method getImportMethod = function.getClass().getMethod("getImportParameterList");
            Object importParams = getImportMethod.invoke(function);

            // Set REQUEST structure
            Method getStructMethod = importParams.getClass().getMethod("getStructure", String.class);
            Object requestStruct = getStructMethod.invoke(importParams, "REQUEST");

            Method setValueMethod = requestStruct.getClass().getMethod("setValue", String.class, Object.class);
            setValueMethod.invoke(requestStruct, "METHOD", request.method());
            setValueMethod.invoke(requestStruct, "PATH", request.uri());
            setValueMethod.invoke(requestStruct, "BODY", request.body() != null ? request.body() : new byte[0]);

            // Set headers table
            Method getTableMethod = importParams.getClass().getMethod("getTable", String.class);
            Object headersTable = getTableMethod.invoke(importParams, "HEADERS");
            Method appendRowMethod = headersTable.getClass().getMethod("appendRow");
            Method setValueOnRowMethod = headersTable.getClass().getMethod("setValue", String.class, Object.class);

            for (Map.Entry<String, String> entry : request.headers().entrySet()) {
                appendRowMethod.invoke(headersTable);
                setValueOnRowMethod.invoke(headersTable, "NAME", entry.getKey());
                setValueOnRowMethod.invoke(headersTable, "VALUE", entry.getValue());
            }

            // Execute
            Method executeMethod = function.getClass().getMethod("execute", destination.getClass());
            executeMethod.invoke(function, destination);

            // Read response
            Method getExportMethod = function.getClass().getMethod("getExportParameterList");
            Object exportParams = getExportMethod.invoke(function);

            Method getExportStructMethod = exportParams.getClass().getMethod("getStructure", String.class);
            Object responseStruct = getExportStructMethod.invoke(exportParams, "RESPONSE");

            Method getValueMethod = responseStruct.getClass().getMethod("getInt", String.class);
            int statusCode = (int) getValueMethod.invoke(responseStruct, "STATUS_CODE");

            Method getStringMethod = responseStruct.getClass().getMethod("getString", String.class);
            String reasonPhrase = (String) getStringMethod.invoke(responseStruct, "REASON");

            Method getByteArrayMethod = responseStruct.getClass().getMethod("getByteArray", String.class);
            byte[] body = (byte[]) getByteArrayMethod.invoke(responseStruct, "BODY");

            // Read response headers
            Method getExportTableMethod = exportParams.getClass().getMethod("getTable", String.class);
            Object responseHeadersTable = getExportTableMethod.invoke(exportParams, "HEADERS");
            Method getNumRowsMethod = responseHeadersTable.getClass().getMethod("getNumRows");
            int numRows = (int) getNumRowsMethod.invoke(responseHeadersTable);

            Map<String, String> responseHeaders = new LinkedHashMap<>();
            Method setRowMethod = responseHeadersTable.getClass().getMethod("setRow", int.class);
            Method getStringFromTableMethod = responseHeadersTable.getClass().getMethod("getString", String.class);
            for (int i = 0; i < numRows; i++) {
                setRowMethod.invoke(responseHeadersTable, i);
                String name = (String) getStringFromTableMethod.invoke(responseHeadersTable, "NAME");
                String value = (String) getStringFromTableMethod.invoke(responseHeadersTable, "VALUE");
                responseHeaders.put(name, value);
            }

            return new ProxyResponse("HTTP/1.1", statusCode, reasonPhrase, responseHeaders, body);

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SAP JCo library not found. Please configure jco_jar in config.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute RFC call: " + e.getMessage(), e);
        }
    }
}
