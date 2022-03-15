package org.folio.shared.index.client;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Main {
  static final Logger log = LogManager.getLogger(Main.class);

    /**
   * Main program for client.
   * @param args command-line args
   */
  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    WebClient webClient = WebClient.create(vertx);
    Client.exec(vertx, webClient, args)
        .eventually(x -> {
          webClient.close();
          vertx.close();
          return Future.succeededFuture();
        })
        .onFailure(e -> {
          log.error(e.getMessage(), e);
          System.exit(1);
        });
  }
}
