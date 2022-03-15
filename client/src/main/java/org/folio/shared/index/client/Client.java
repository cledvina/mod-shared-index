package org.folio.shared.index.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.marc4j.MarcJsonWriter;
import org.marc4j.MarcStreamReader;
import org.marc4j.converter.impl.AnselToUnicode;

public class Client {
  static final Logger log = LogManager.getLogger(Client.class);

  UUID sourceId = UUID.randomUUID();
  String tenant = "testlib";
  String okapiUrl = "http://localhost:9130";
  int chunkSize = 1;
  Integer localSequence = 0;
  WebClient webClient;
  Vertx vertx;
  Map<String,String> headers = new HashMap<>();

  public Client(WebClient webClient, Vertx vertx) {
    this.webClient = webClient;
    this.vertx = vertx;
  }

  public void setSourceId(UUID sourceId) {
    this.sourceId = sourceId;
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }

  public void setTenant(String tenant) {
    headers.put(XOkapiHeaders.TENANT, tenant);
  }

  public void setOkapiUrl(String okapiUrl) {
    this.okapiUrl = okapiUrl;
  }

  private Future<Void> sendChunk(MarcStreamReader reader) {
    JsonArray records = new JsonArray();
    while (reader.hasNext() && records.size() < chunkSize) {
      org.marc4j.marc.Record marcRecord = reader.next();
      char charCodingScheme = marcRecord.getLeader().getCharCodingScheme();
      if (charCodingScheme == ' ') {
        marcRecord.getLeader().setCharCodingScheme('a');
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      MarcJsonWriter writer = new MarcJsonWriter(out, MarcJsonWriter.MARC_IN_JSON);
      if (charCodingScheme == ' ') {
        writer.setConverter(new AnselToUnicode());
      }
      writer.write(marcRecord);
      JsonObject marcPayload = new JsonObject(out.toString());
      JsonObject inventoryPayload = new JsonObject();
      records.add(new JsonObject()
          .put("localId", Integer.toString(localSequence++))
          .put("marcPayload", marcPayload)
          .put("inventoryPayload", inventoryPayload));
    }
    if (records.isEmpty()) {
      return Future.succeededFuture();
    }
    JsonObject request = new JsonObject()
        .put("sourceId", sourceId)
        .put("records", records);

    return webClient.putAbs(okapiUrl + "/shared-index/records")
        .putHeader(XOkapiHeaders.TENANT, tenant)
        .putHeader(XOkapiHeaders.URL, okapiUrl)
        .expect(ResponsePredicate.SC_OK)
        .expect(ResponsePredicate.JSON)
        .sendJsonObject(request).compose(x -> sendChunk(reader));
  }

  /**
   * Send file with ISO2709 to shared-index.
   * @param fname filename
   * @return async result
   */
  public Future<Void> sendFile(String fname)  {
    InputStream stream;
    try {
      stream = new FileInputStream(fname);
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
    return sendChunk(new MarcStreamReader(stream));
  }
}