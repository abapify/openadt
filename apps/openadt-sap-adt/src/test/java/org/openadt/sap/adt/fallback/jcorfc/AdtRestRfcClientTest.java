package org.openadt.sap.adt.fallback.jcorfc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdtRestRfcClientTest {
    interface DestinationContract {}

    static class DestinationImpl implements DestinationContract {}

    static class FakeFunction {
        public void execute(DestinationContract destination) {
            // test double noop
        }
    }

    static class FakeStatusLine {
        public int getInt(String name) {
            throw new NumberFormatException("bad int");
        }

        public String getString(String name) {
            return "406 ";
        }
    }

    @Test
    void resolvesExecuteMethodByInterface() throws Exception {
        AdtRestRfcClient client = new AdtRestRfcClient(null);

        Method method = client.resolveExecuteMethod(new FakeFunction(), new DestinationImpl());

        assertEquals("execute", method.getName());
        assertEquals(DestinationContract.class, method.getParameterTypes()[0]);
    }

    @Test
    void readsTrimmedStatusCodeWhenJcoIntConversionFails() throws Exception {
        AdtRestRfcClient client = new AdtRestRfcClient(null);
        Method getString = FakeStatusLine.class.getMethod("getString", String.class);

        int statusCode = client.readStatusCode(new FakeStatusLine(), getString);

        assertEquals(406, statusCode);
    }
}
