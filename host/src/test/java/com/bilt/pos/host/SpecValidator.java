package com.bilt.pos.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.PathType;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.oas.OpenApi31;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * The Session Protocol spec ({@code schema/session-protocol}) as a validator for what the host
 * sends. The multi-file OpenAPI document is read as it is: the path files are walked with Jackson
 * to find the request and response schema of each route, and the component schemas are handed to
 * the networknt validator as OpenAPI 3.1 JSON Schema, which follows the files' relative {@code
 * $ref}s itself. Nothing is bundled and nothing is fetched, so it runs wherever the repository is.
 *
 * <p>A failure names the JSON path, the offending value and the schema path that rejected it.
 */
final class SpecValidator {

  /** System property naming the spec directory; otherwise it is looked for up the tree. */
  static final String DIR_PROPERTY = "bilt.sessionProtocolDir";

  private static final String JSON = "application/json";
  private static final String SSE = "text/event-stream";

  private static final class Lazy {
    static final SpecValidator SHARED = new SpecValidator(locate());
  }

  static SpecValidator shared() {
    return Lazy.SHARED;
  }

  /** A node of one spec document and where it lives, so relative references resolve from it. */
  private static final class Located {
    final URI document;
    final String pointer;
    final JsonNode node;

    Located(URI document, String pointer, JsonNode node) {
      this.document = document;
      this.pointer = pointer;
      this.node = node;
    }

    String key() {
      return document + "#" + pointer;
    }
  }

  private static final class Route {
    final String template;
    final Pattern pattern;
    final int literalLength;
    final Located item;

    Route(String template, Located item) {
      this.template = template;
      this.item = item;
      StringBuilder regex = new StringBuilder();
      int literals = 0;
      for (String segment : template.split("/", -1)) {
        if (regex.length() > 0) {
          regex.append('/');
        }
        if (segment.startsWith("{") && segment.endsWith("}")) {
          regex.append("[^/]+");
        } else {
          regex.append(Pattern.quote(segment));
          literals += segment.length();
        }
      }
      this.pattern = Pattern.compile(regex.toString());
      this.literalLength = literals;
    }
  }

  private final Path dir;
  private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
  /** The status for reusing an idempotency key with a different body. */
  private static final int IDEMPOTENCY_REUSE = 422;

  private final Map<URI, JsonNode> documents = new ConcurrentHashMap<>();
  private final Map<String, JsonSchema> schemas = new ConcurrentHashMap<>();
  private final JsonSchemaFactory factory;
  private final SchemaValidatorsConfig config;
  private final URI openapi;
  private final List<Route> routes = new ArrayList<>();

  private static Path locate() {
    String configured = System.getProperty(DIR_PROPERTY);
    if (configured != null && !configured.isBlank()) {
      return Path.of(configured);
    }
    for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      Path candidate = dir.resolve("schema").resolve("session-protocol");
      if (Files.isRegularFile(candidate.resolve("openapi.yaml"))) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "schema/session-protocol was not found above the working directory; set -D" + DIR_PROPERTY);
  }

  SpecValidator(Path dir) {
    this.dir = dir.toAbsolutePath().normalize();
    this.openapi = this.dir.resolve("openapi.yaml").toUri();
    this.factory =
        JsonSchemaFactory.getInstance(
            SpecVersion.VersionFlag.V202012,
            builder ->
                builder
                    .metaSchema(OpenApi31.getInstance())
                    .defaultMetaSchemaIri(OpenApi31.getInstance().getIri()));
    this.config =
        SchemaValidatorsConfig.builder()
            .pathType(PathType.JSON_POINTER)
            .formatAssertionsEnabled(true)
            .build();
    JsonNode paths = document(openapi).path("paths");
    paths
        .fields()
        .forEachRemaining(
            entry ->
                routes.add(
                    new Route(
                        entry.getKey(),
                        deref(
                            new Located(
                                openapi, "/paths/" + escape(entry.getKey()), entry.getValue())))));
  }

  /** The directory holding {@code examples/}. */
  Path examples() {
    return dir.resolve("examples");
  }

  // ─── What tests call ───

  /** Validates a value against a named entry of {@code components.schemas}. */
  void schema(String name, JsonNode value) {
    Located located =
        deref(
            new Located(
                openapi,
                "/components/schemas/" + name,
                document(openapi).at("/components/schemas/" + name)));
    if (located.node.isMissingNode()) {
      throw new AssertionError("the spec defines no schema named " + name);
    }
    validate(located, value, "schema " + name);
  }

  /** Validates one event envelope against {@code Event}. */
  void event(JsonNode event) {
    schema("Event", event);
  }

  /** Validates a request body against the route's {@code requestBody} schema. */
  void request(String method, String path, JsonNode body) {
    Route route = route(method, path);
    Located operation = operation(route, method);
    Located requestBody = child(operation, "requestBody");
    if (requestBody == null) {
      throw new AssertionError(method + " " + route.template + " takes no request body");
    }
    Located schema = bodySchema(deref(requestBody));
    if (schema == null) {
      throw new AssertionError(method + " " + route.template + " has no JSON request schema");
    }
    validate(schema, body, method + " " + route.template + " request");
  }

  /**
   * Validates a response: the status must be one the route documents (bar the idempotency-key
   * reuse refusal, which the spec documents once for every keyed route), and the body must match
   * that status's schema, or be empty when it has none.
   */
  void response(String method, String path, int status, JsonNode body) {
    Route route = route(method, path);
    Located operation = operation(route, method);
    String label = method + " " + route.template + " -> " + status;
    Located responses = child(operation, "responses");
    Located response = responses == null ? null : child(responses, Integer.toString(status));
    if (response == null) {
      if (status == IDEMPOTENCY_REUSE) {
        // the spec describes this refusal in the Idempotency-Key parameter, not per route
        schema("SessionError", body);
        return;
      }
      throw new AssertionError(label + " is not a response the spec lists for this route");
    }
    Located schema = bodySchema(deref(response));
    if (schema == null) {
      if (body != null && !body.isNull() && !body.isMissingNode()) {
        throw new AssertionError(label + " must have no body, but had: " + abbreviate(body));
      }
      return;
    }
    validate(schema, body, label);
  }

  // ─── Routes ───

  private Route route(String method, String path) {
    int query = path.indexOf('?');
    String plain = query < 0 ? path : path.substring(0, query);
    // a templated path can match another's literal one (a {sessionId} segment matches "nope"), so
    // the template with the most literal characters is the one the request meant
    Route best = null;
    for (Route route : routes) {
      if (route.pattern.matcher(plain).matches()
          && (best == null || route.literalLength > best.literalLength)) {
        best = route;
      }
    }
    if (best == null) {
      throw new AssertionError(method + " " + plain + " matches no path in the spec");
    }
    return best;
  }

  private Located operation(Route route, String method) {
    Located operation = child(route.item, method.toLowerCase(Locale.ROOT));
    if (operation == null) {
      throw new AssertionError(method + " " + route.template + " is not an operation in the spec");
    }
    return deref(operation);
  }

  /** The JSON schema of a request body or response object, or {@code null} when it has none. */
  private Located bodySchema(Located holder) {
    Located content = child(holder, "content");
    if (content == null) {
      return null;
    }
    Located media = child(content, JSON);
    if (media == null) {
      media = child(content, SSE);
    }
    if (media == null) {
      return null;
    }
    Located schema = child(media, "schema");
    return schema == null ? null : deref(schema);
  }

  // ─── Documents and references ───

  private JsonNode document(URI uri) {
    return documents.computeIfAbsent(
        uri,
        key -> {
          try {
            return yaml.readTree(Path.of(key).toFile());
          } catch (IOException e) {
            throw new UncheckedIOException("could not read spec document " + key, e);
          }
        });
  }

  private static Located child(Located parent, String name) {
    JsonNode node = parent.node.get(name);
    return node == null
        ? null
        : new Located(parent.document, parent.pointer + "/" + escape(name), node);
  }

  /** Follows {@code $ref} chains, each resolved against the document it appears in. */
  private Located deref(Located located) {
    Located current = located;
    for (int hops = 0; current.node.isObject() && current.node.has("$ref"); hops++) {
      if (hops > 16) {
        throw new AssertionError("reference loop at " + located.key());
      }
      current = resolve(current.document, current.node.get("$ref").asText());
    }
    return current;
  }

  private Located resolve(URI base, String ref) {
    int hash = ref.indexOf('#');
    String target = hash < 0 ? ref : ref.substring(0, hash);
    String pointer = hash < 0 ? "" : ref.substring(hash + 1);
    URI document = target.isEmpty() ? base : base.resolve(target);
    JsonNode node = document(document).at(pointer);
    if (node.isMissingNode()) {
      throw new AssertionError("dangling $ref '" + ref + "' from " + base);
    }
    return new Located(document, pointer, node);
  }

  private static String escape(String token) {
    return token.replace("~", "~0").replace("/", "~1");
  }

  // ─── Validation ───

  private JsonSchema jsonSchema(Located schema) {
    return schemas.computeIfAbsent(
        schema.key(), key -> factory.getSchema(SchemaLocation.of(key), config));
  }

  private void validate(Located schema, JsonNode value, String label) {
    JsonNode subject = value == null || value.isMissingNode() ? NullNode.getInstance() : value;
    Set<ValidationMessage> messages = jsonSchema(schema).validate(subject);
    if (messages.isEmpty()) {
      return;
    }
    StringBuilder report =
        new StringBuilder(label)
            .append(" does not conform to ")
            .append(schemaName(schema))
            .append(":\n");
    for (ValidationMessage message : messages) {
      String at = message.getInstanceLocation().toString();
      report
          .append("  ")
          .append(message.getMessage())
          .append("\n      value: ")
          .append(abbreviate(subject.at(at)))
          .append("\n      schema: ")
          .append(message.getEvaluationPath())
          .append('\n');
    }
    report.append("  body: ").append(abbreviate(subject));
    throw new AssertionError(report.toString());
  }

  private String schemaName(Located schema) {
    String file = dir.toUri().relativize(schema.document).toString();
    return file + "#" + schema.pointer;
  }

  private static String abbreviate(JsonNode node) {
    String text = String.valueOf(node);
    return text.length() <= 600 ? text : text.substring(0, 600) + "…";
  }
}
