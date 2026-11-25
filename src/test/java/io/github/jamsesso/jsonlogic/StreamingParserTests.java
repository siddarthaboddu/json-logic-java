package io.github.jamsesso.jsonlogic;
import io.github.jamsesso.jsonlogic.ast.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class StreamingParserTests {
  @Test public void duplicateOperatorUsesTheLastValue() throws Exception {
    assertEquals(3.0, new JsonLogic().apply("{\"+\":{},\"+\":[1,2]}",null));
  }

  @Test public void multipleKeysReportTheObjectBeforeItsInvalidChild() throws Exception {
    try {
      JsonLogicParser.parse("{\"if\":[true,{\"x\":{},\"y\":1},0]}");
      fail("expected an invalid operation");
    } catch (JsonLogicParseException error) {
      assertEquals("objects must have exactly 1 key defined, found 2",error.getMessage());
      assertEquals("$.if[1]",error.getJsonPath());
    }
  }

  @Test public void laterSyntaxErrorTakesPrecedenceOverInvalidRule() throws Exception {
    try {
      JsonLogicParser.parse("[{},");
      fail("expected a syntax error");
    } catch (JsonLogicParseException error) {
      assertEquals("$",error.getJsonPath());
      assertTrue(error.getCause() instanceof com.google.gson.JsonSyntaxException);
    }
  }

  @Test public void invalidUnicodeEscapePreservesSyntaxCauseAndRootPath() throws Exception {
    try {
      JsonLogicParser.parse("'\\u00zz'");
      fail("expected an invalid escape");
    } catch (JsonLogicParseException error) {
      assertEquals("$", error.getJsonPath());
      assertTrue(error.getCause() instanceof com.google.gson.JsonSyntaxException);
      assertTrue(error.getCause().getCause() instanceof NumberFormatException);
    }
  }
  @Test public void acceptsLegacyLenientSyntax() throws Exception {
    assertEquals(3.0,new JsonLogic().apply("{/* legacy comment */ '+':[1,2]}",null));
    assertSame(JsonLogicNull.NULL,JsonLogicParser.parse("   "));
    assertSame(JsonLogicNull.NULL,JsonLogicParser.parse("null 1"));
  }
}
