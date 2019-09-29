package com.jtk.vertx.clients

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.reflect.jvm.jvmName


class WebSocketClient : AbstractVerticle() {

  companion object {
    val LOGGER = LoggerFactory.getLogger(WebSocketClient::class.jvmName)
  }

  override fun start(startFuture: Future<Void>?) {
    startClient()
  }

  private fun startClient() {
    var client = vertx.createHttpClient()
    client.webSocket(8080, "localhost", "/eventbus/start.to.server") {

      if (it.succeeded()) {
        var ws = it.result()
        ws.textMessageHandler { msg ->
          LOGGER.info("Client msg ${msg}")
          ws.writeTextMessage("Pong")
        }.exceptionHandler {
          LOGGER.warn("Closed restarting in 10s")
          restart(client, 5)
        }

      } else {
        LOGGER.error("Exception", it.cause())
      }

    }
  }

  private fun restart(client: HttpClient, delay: Int) {
    client.close()
    vertx.setTimer(TimeUnit.SECONDS.toMillis(delay.toLong())) { d -> startClient() }
  }

}


fun main() {
  //Launcher.executeCommand("run", WebSocketClient::class.jvmName)
  Vertx.vertx().deployVerticle(WebSocketClient())
}

