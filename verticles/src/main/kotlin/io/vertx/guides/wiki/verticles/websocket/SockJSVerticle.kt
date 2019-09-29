package io.vertx.guides.wiki.verticles.websocket

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Launcher
import io.vertx.ext.bridge.BridgeEventType
import io.vertx.ext.bridge.PermittedOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.StaticHandler
import io.vertx.ext.web.handler.sockjs.BridgeOptions
import io.vertx.ext.web.handler.sockjs.SockJSHandler
import io.vertx.kotlin.core.http.httpServerOptionsOf
import org.slf4j.LoggerFactory
import java.text.DateFormat
import java.time.Instant
import java.util.*
import kotlin.reflect.jvm.jvmName
import kotlin.system.exitProcess


class SockJsVerticle : AbstractVerticle() {

  companion object {
    val LOGGER = LoggerFactory.getLogger(SockJsVerticle::class.jvmName)
  }

  override fun start(startFuture: Future<Void>?) {
    // Allow events for the designated addresses in/out of the event bus bridge
    val router = Router.router(vertx)
    val opts = BridgeOptions()
      .addInboundPermitted(PermittedOptions().setAddress("chat.to.server"))
      .addOutboundPermitted(PermittedOptions().setAddress("chat.to.client"))

    // // Create the event bus bridge and add it to the router
    val sockjsHandler = SockJSHandler.create(vertx).bridge(opts) { be ->
      if (be.type() == BridgeEventType.REGISTER) {
        LOGGER.info("sockJS connected")
        //vertx.eventBus().publish("chat.to.client", "Hello! new subscriber")
      }
      be.complete(true)
    }
    router.route("/eventbus/*").handler(sockjsHandler)

    //// Create a router endpoint for the static content.
    router.route().handler(StaticHandler.create())

    vertx.createHttpServer(httpServerOptionsOf(clientAuthRequired = false)).requestHandler(router).listen(9090) { handler ->
      if (handler.failed()) {
        LOGGER.warn("Startup error", handler.cause())
        exitProcess(0)
      }
    }

    vertx.eventBus().consumer<String>("chat.to.server").handler { message ->
      val timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date.from(Instant.now()))
      // Send the message back out to all clients with the timestamp prepended.
      vertx.eventBus().publish("chat.to.client", timestamp + ": " + message.body())
    }

    LOGGER.info("Started WebSocket Verticle")
  }
}

fun main() {
  Launcher.executeCommand("run", SockJsVerticle::class.jvmName)
}
