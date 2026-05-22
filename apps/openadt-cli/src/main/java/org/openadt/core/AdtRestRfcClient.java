package org.openadt.core;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Executes ADT requests via the {@code SADT_REST_RFC_ENDPOINT} RFC function using SAP JCo.
 * <p>
 * JCo classes are accessed exclusively via reflection so that this project compiles
 * and unit tests run without SAP JCo on the classpath. A meaningful exception is thrown
 * at runtime if JCo is absent or the jar path is misconfigured.
 * <p>
 * RFC structure (simplified):
 * <pre>
 *   IMPORT  REQUEST
 *     REQUEST_LINE.METHOD
 *     REQUEST_LINE.URI
 *     REQUEST_LINE.VERSION
 *     HEADER_FIELDS[].NAME / VALUE
 *     MESSAGE_BODY (xstring)
 *
 *   EXPORT  RESPONSE
 *     STATUS_LINE.STATUS_CODE
 *     STATUS_LINE.REASON_PHRASE
 *     HEADER_FIELDS[].NAME / VALUE
 *     MESSAGE_BODY (xstring)
 * </pre>
 */
public class AdtRestRfcClient implements AdtTransportClient {
    private static final String RFC_FUNCTION = "SADT_REST_RFC_ENDPOINT";

    private final JCoDestinationFactory destinationFactory;

    public AdtRestRfcClient(JCoDestinationFactory destinationFactory) {
        this.destinationFactory = destinationFactory;
    }

    public ProxyResponse execute(SystemProfile system, ProxyRequest request) {
        try {
            Object destination = destinationFactory.getDestination(system);

            // destination.getRepository()
            Method getRepoMethod = destination.getClass().getMethod("getRepository");
            Object repository = getRepoMethod.invoke(destination);

            // repository.getFunction(RFC_FUNCTION)
            Method getFuncMethod = repository.getClass().getMethod("getFunction", String.class);
            Object function = getFuncMethod.invoke(repository, RFC_FUNCTION);

            // function.getImportParameterList()
            Method getImportMethod = function.getClass().getMethod("getImportParameterList");
            Object importParams = getImportMethod.invoke(function);

            // REQUEST structure
            Method getStructMethod = importParams.getClass().getMethod("getStructure", String.class);
            Object requestStruct = getStructMethod.invoke(importParams, "REQUEST");

            // REQUEST.REQUEST_LINE nested structure
            Method getNestedStructMethod = requestStruct.getClass().getMethod("getStructure", String.class);
            Object requestLine = getNestedStructMethod.invoke(requestStruct, "REQUEST_LINE");

            Method setValueMethod = requestLine.getClass().getMethod("setValue", String.class, Object.class);
            setValueMethod.invoke(requestLine, "METHOD", request.method());
            setValueMethod.invoke(requestLine, "URI", request.uri());
            setValueMethod.invoke(requestLine, "VERSION", request.version() != null ? request.version() : "HTTP/1.1");

            // REQUEST.MESSAGE_BODY
            Method setBodyMethod = requestStruct.getClass().getMethod("setValue", String.class, Object.class);
            setBodyMethod.invoke(requestStruct, "MESSAGE_BODY", request.body() != null ? request.body() : new byte[0]);

            // REQUEST.HEADER_FIELDS table
            Method getTableMethod = requestStruct.getClass().getMethod("getTable", String.class);
            Object headersTable = getTableMethod.invoke(requestStruct, "HEADER_FIELDS");
            Method appendRowMethod = headersTable.getClass().getMethod("appendRow");
            Method setValueOnRowMethod = headersTable.getClass().getMethod("setValue", String.class, Object.class);

            for (Map.Entry<String, String> entry : request.headers().entrySet()) {
                appendRowMethod.invoke(headersTable);
                setValueOnRowMethod.invoke(headersTable, "NAME", entry.getKey());
                setValueOnRowMethod.invoke(headersTable, "VALUE", entry.getValue());
            }

            // Execute the RFC function
            Method executeMethod = resolveExecuteMethod(function, destination);
            executeMethod.invoke(function, destination);

            // Read RESPONSE
            Method getExportMethod = function.getClass().getMethod("getExportParameterList");
            Object exportParams = getExportMethod.invoke(function);

            Method getExportStructMethod = exportParams.getClass().getMethod("getStructure", String.class);
            Object responseStruct = getExportStructMethod.invoke(exportParams, "RESPONSE");

            // RESPONSE.STATUS_LINE
            Method getStatusLineMethod = responseStruct.getClass().getMethod("getStructure", String.class);
            Object statusLine = getStatusLineMethod.invoke(responseStruct, "STATUS_LINE");

            Method getStringMethod = statusLine.getClass().getMethod("getString", String.class);
            int statusCode = readStatusCode(statusLine, getStringMethod);
            String reasonPhrase = (String) getStringMethod.invoke(statusLine, "REASON_PHRASE");
            String version = (String) getStringMethod.invoke(statusLine, "VERSION");

            // RESPONSE.MESSAGE_BODY
            Method getBodyMethod = responseStruct.getClass().getMethod("getByteArray", String.class);
            byte[] body = (byte[]) getBodyMethod.invoke(responseStruct, "MESSAGE_BODY");

            // RESPONSE.HEADER_FIELDS table
            Method getResponseTableMethod = responseStruct.getClass().getMethod("getTable", String.class);
            Object responseHeadersTable = getResponseTableMethod.invoke(responseStruct, "HEADER_FIELDS");
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

            return new ProxyResponse(
                version != null && !version.isBlank() ? version : "HTTP/1.1",
                statusCode,
                reasonPhrase,
                responseHeaders,
                body
            );

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SAP JCo library not found. Please configure jco_jar in config.", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute RFC call: " + e.getMessage(), e);
        }
    }

    Method resolveExecuteMethod(Object function, Object destination) throws Exception {
        for (Method method : function.getClass().getMethods()) {
            if (!"execute".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (method.getParameterTypes()[0].isAssignableFrom(destination.getClass())) {
                return method;
            }
        }
        throw new NoSuchMethodException(function.getClass().getName() + ".execute(" + destination.getClass().getName() + ")");
    }

    int readStatusCode(Object statusLine, Method getStringMethod) throws Exception {
        try {
            Method getIntMethod = statusLine.getClass().getMethod("getInt", String.class);
            return (int) getIntMethod.invoke(statusLine, "STATUS_CODE");
        } catch (Exception ignored) {
            String rawStatusCode = (String) getStringMethod.invoke(statusLine, "STATUS_CODE");
            return Integer.parseInt(rawStatusCode.trim());
        }
    }
}
