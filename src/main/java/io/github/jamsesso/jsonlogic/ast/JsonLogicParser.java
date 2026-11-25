package io.github.jamsesso.jsonlogic.ast;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class JsonLogicParser {
  private static final int MAX_STREAMING_DEPTH = 128;
  private static final JsonParser PARSER = new JsonParser();

  private JsonLogicParser() {
    // Utility class has no public constructor.
  }

  public static JsonLogicNode parse(String json) throws JsonLogicParseException {
    try {
      JsonLogicNode node = readDocument(json);
      return node != null ? node : parse(PARSER.parse(json));
    } catch (JsonLogicParseException e) {
      e.prependPartialJsonPath("$");
      throw e;
    } catch (JsonSyntaxException e) {
      throw new JsonLogicParseException(e, "$");
    }
  }

  private static JsonLogicNode readDocument(String json) {
    JsonReader reader = new JsonReader(new StringReader(json));
    reader.setLenient(true);
    try {
      boolean empty = true;
      JsonLogicNode node;
      try {
        reader.peek();
        empty = false;
        node = readNode(reader, 0);
      } catch (EOFException e) {
        if (empty) return JsonLogicNull.NULL;
        throw new JsonSyntaxException(e);
      }

      reader.setLenient(false);
      if (node != null && node != JsonLogicNull.NULL && reader.peek() != JsonToken.END_DOCUMENT) {
        throw new JsonSyntaxException("Did not consume the entire document.");
      }
      return node;
    } catch (MalformedJsonException | NumberFormatException e) {
      throw new JsonSyntaxException(e);
    } catch (IOException e) {
      throw new JsonIOException(e);
    }
  }

  private static JsonLogicNode readNode(JsonReader reader, int depth) throws IOException {
    if (depth >= MAX_STREAMING_DEPTH) {
      return null;
    }
    switch (reader.peek()) {
      case NULL:
        reader.nextNull();
        return JsonLogicNull.NULL;
      case BOOLEAN:
        return reader.nextBoolean() ? JsonLogicBoolean.TRUE : JsonLogicBoolean.FALSE;
      case STRING:
        return new JsonLogicString(reader.nextString());
      case NUMBER:
        return new JsonLogicNumber(PARSER.parse(reader).getAsNumber());
      case BEGIN_ARRAY:
        reader.beginArray();
        List<JsonLogicNode> elements = new ArrayList<>();
        while (reader.hasNext()) {
          JsonLogicNode element = readNode(reader, depth + 1);
          if (element == null) return null;
          elements.add(element);
        }
        reader.endArray();
        return new JsonLogicArray(elements);
      case BEGIN_OBJECT:
        reader.beginObject();
        if (!reader.hasNext()) return null;
        String key = reader.nextName();
        JsonLogicNode argument = readNode(reader, depth + 1);
        // Gson resolves duplicate keys and validates the whole document before rule validation.
        if (argument == null || reader.hasNext()) return null;
        reader.endObject();
        return operation(key, argument);
      default:
        return null;
    }
  }

  private static JsonLogicNode parse(JsonElement root) throws JsonLogicParseException {
    // Handle null
    if (root.isJsonNull()) {
      return JsonLogicNull.NULL;
    }

    // Handle primitives
    if (root.isJsonPrimitive()) {
      JsonPrimitive primitive = root.getAsJsonPrimitive();

      if (primitive.isString()) {
        return new JsonLogicString(primitive.getAsString());
      }

      if (primitive.isNumber()) {
        return new JsonLogicNumber(primitive.getAsNumber());
      }

      if (primitive.isBoolean() && primitive.getAsBoolean()) {
        return JsonLogicBoolean.TRUE;
      } else {
        return JsonLogicBoolean.FALSE;
      }
    }

    // Handle arrays
    if (root.isJsonArray()) {
      JsonArray array = root.getAsJsonArray();
      List<JsonLogicNode> elements = new ArrayList<>(array.size());

      for (int index = 0; index < array.size(); index++) {
        JsonElement element = array.get(index);
        JsonLogicNode arrayNode;
        try {
          arrayNode = parse(element);
        } catch (JsonLogicParseException e) {
          e.prependPartialJsonPath("[" + (index) + "]");
          throw e;
        }
        elements.add(arrayNode);
      }

      return new JsonLogicArray(elements);
    }

    // Handle objects & variables
    JsonObject object = root.getAsJsonObject();

    if (object.keySet().size() != 1) {
      throw new JsonLogicParseException("objects must have exactly 1 key defined, found " + object.keySet().size());
    }

    String key = object.keySet().stream().findAny().get();
    JsonLogicNode argumentNode;

    try {
      argumentNode = parse(object.get(key));
    } catch (JsonLogicParseException e) {
      e.prependPartialJsonPath("." + key);
      throw e;
    }

    return operation(key, argumentNode);
  }

  private static JsonLogicNode operation(String key, JsonLogicNode argumentNode) {
    JsonLogicArray arguments;
    // Always coerce single-argument operations into a JsonLogicArray with a single element.
    if (argumentNode instanceof JsonLogicArray) {
      arguments = (JsonLogicArray) argumentNode;
    } else {
      arguments = new JsonLogicArray(Collections.singletonList(argumentNode));
    }

    // Special case for variable handling
    if ("var".equals(key)) {
      JsonLogicNode defaultValue = arguments.size() > 1 ? arguments.get(1) : JsonLogicNull.NULL;
      return new JsonLogicVariable(arguments.size() < 1 ? JsonLogicNull.NULL : arguments.get(0), defaultValue);
    }

    // Handle regular operations
    return new JsonLogicOperation(key, arguments);
  }
}
