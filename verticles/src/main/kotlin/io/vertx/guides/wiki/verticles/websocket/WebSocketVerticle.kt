package io.vertx.guides.wiki.verticles.websocket

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import kotlin.reflect.jvm.jvmName


class WebSocketVerticle : AbstractVerticle() {

  companion object {
    val LOGGER = LoggerFactory.getLogger(WebSocketVerticle::class.jvmName)
  }

  override fun start(startFuture: Future<Void>?) {
    val server = vertx.createHttpServer()

    server.websocketHandler { webSocket ->
      LOGGER.info("Websocket is connected to ${webSocket.path()}")

      webSocket.textMessageHandler { msg ->
        LOGGER.info("Server msg ${msg}")
        webSocket.writeTextMessage("Ping")
      }
      if (webSocket.path().startsWith("/eventbus/")) {
        webSocket.writeTextMessage("Ping")
      } else {
        LOGGER.warn("Invalid path detected ${webSocket.path()}")
        webSocket.reject()
      }

    }.listen(8080, "localhost")

  }
}

fun main() {
  //Launcher.executeCommand("run", WebSocketVerticle::class.jvmName)
  Vertx.vertx().deployVerticle(WebSocketVerticle())
}
