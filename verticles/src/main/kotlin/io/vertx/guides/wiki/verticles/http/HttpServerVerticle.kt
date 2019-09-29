package io.vertx.guides.wiki.verticles.http

import com.github.rjeschke.txtmark.Processor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Launcher
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import io.vertx.guides.wiki.MainVerticle
import io.vertx.kotlin.core.eventbus.deliveryOptionsOf
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.reflect.jvm.jvmName


class HttpServerVerticle : AbstractVerticle() {
  companion object {
    val LOGGER = LoggerFactory.getLogger(HttpServerVerticle::class.jvmName)
    val CONFIG_HTTP_SERVER_PORT = "http.server.port"
    val CONFIG_WIKIDB_QUEUE = "wikidb.queue"
    val EMPTY_PAGE_MARKDOWN =
      """
      # A new page

      Feel-free to write in Markdown!
      """

  }

  var wikiDbQueue = "wikidb.queue"
  var templateEngine: FreeMarkerTemplateEngine? = null

  override fun start(startFuture: Future<Void>?) {
    //The AbstractVerticle#config() method allows accessing the verticle configuration that has been
    //provided. The second parameter is a default value in case no specific value was given
    //Configuration values can not just be String objects but also integers, boolean values, complex
    //JSON data, etc.
    wikiDbQueue = config().getString(CONFIG_WIKIDB_QUEUE, "wikidb.queue")
    val server = vertx.createHttpServer()

    val router = Router.router(vertx)
    router["/"].handler(indexHandler)
    router["/wiki/:page"].handler(pageRenderingHandler)
    router.post().handler(BodyHandler.create())
    router.post("/save").handler(pageUpdateHandler)
    router.post("/create").handler(pageCreationHandler)
    router.post("/delete").handler(pageDeletionHandler)

    templateEngine = FreeMarkerTemplateEngine.create(vertx)

    val portNumber = config().getInteger(CONFIG_HTTP_SERVER_PORT, 8080)
    /**
    The HTTP server makes use of the vertx-web project to easily define dispatching routes for incoming
    HTTP requests. Indeed, the Vert.x core API allows to start HTTP servers and listen for incoming
    connections, but it does not provide any facility to, say, have different handlers depending on the
    requested URL or processing request bodies. This is the role of a router as it dispatches requests to
    different processing handlers depending on the URL, the HTTP method, etc.
     */
    server.requestHandler(router)
      .listen(portNumber) {
        if (it.succeeded()) {
          MainVerticle.LOGGER.info("HTTP server running at $portNumber")
          startFuture?.complete()
        } else {
          MainVerticle.LOGGER.error("Could not start a HTTP server", it.cause())
          startFuture?.fail(it.cause())
        }
      }


  }
  /**
  The RoutingContext instance can be used to put arbitrary key / value data that is then available
  from templates, or chained router handlers.
   */
  private val indexHandler: (RoutingContext) -> Unit = { context ->

    //Delivery options allow us to specify headers, payload codecs and timeouts
    val options = deliveryOptionsOf(headers = mapOf("action" to "all-pages"))
    //The vertx object gives access to the event bus, and we send a message to the queue for the
    //database verticle.
    //As we can see, an event bus message consists of a body, options, and it can optionally expect a reply.
    //In the event that no response is expected there is a variant of the send method that does not have a
    //handler.
    vertx.eventBus().send<JsonObject>(wikiDbQueue, JsonObject(), options) { reply ->
      // Upon success a reply contains a payload
      if (reply.succeeded()) {
        val body = reply.result().body()
        context.put("title", "Wiki home")
        context.put("pages", body.getJsonArray("pages").list)
        templateEngine?.render(context.data(), "templates/index.ftl") {
          if (it.succeeded()) {
            context.response().putHeader("Content-Type", "text/html")
            context.response().end(it.result())
          } else {
            context.fail(it.cause())
          }
        }
      } else {
        context.fail(reply.cause())
      }
    }
  }


  private val pageRenderingHandler: (RoutingContext) -> Unit = { context ->
    val requestedPage = context.request().getParam("page")
    val request = JsonObject().put("page", requestedPage)
    val options = deliveryOptionsOf(headers = mapOf("action" to "get-page"))
    vertx.eventBus().send<JsonObject>(wikiDbQueue, request, options) { reply ->
      if (reply.succeeded()) {
        val body = reply.result().body()
        val found = body.getBoolean("found")
        val rawContent = body.getString("rawContent", EMPTY_PAGE_MARKDOWN)
        context.put("title", requestedPage)
        context.put("id", body.getInteger("id", -1))
        context.put("newPage", if (found) "no" else "yes")
        context.put("rawContent", rawContent)
        context.put("content", Processor.process(rawContent))
        context.put("timestamp", Date().toString())
        templateEngine?.render(context.data(), "templates/page.ftl") { ar ->
          if (ar.succeeded()) {
            context.response().putHeader("Content-Type", "text/html")
            context.response().end(ar.result())
          } else {
            context.fail(ar.cause())
          }
        }
      } else {
        context.fail(reply.cause())
      }
    }
  }
  private val pageCreationHandler: (RoutingContext) -> Unit = { context ->
    val pageName = context.request().getParam("name")
    var location = "/wiki/$pageName"
    if (pageName == null || pageName.isEmpty()) {
      location = "/"
    }
    context.response().statusCode = 303;
    context.response().putHeader("Location", location);
    context.response().end();
  }

  private val pageUpdateHandler: (RoutingContext) -> Unit = { context ->
    val title = context.request().getParam("title")
    val request = JsonObject()
      .put("id", context.request().getParam("id"))
      .put("title", title)
      .put("markdown", context.request().getParam("markdown"))

    val options = if ("yes".equals(context.request().getParam("newPage"))) {
      deliveryOptionsOf(headers = mapOf("action" to "create-page"))
    } else {
      deliveryOptionsOf(headers = mapOf("action" to "save-page"))
    }

    vertx.eventBus().send<JsonObject>(wikiDbQueue, request, options) { reply ->
      if (reply.succeeded()) {
        context.response().statusCode = 303
        context.response().putHeader("Location", "/wiki/$title")
        context.response().end()
      } else {
        context.fail(reply.cause())
      }
    }
  }
  private val pageDeletionHandler: (RoutingContext) -> Unit = { context ->
    val id = context.request().getParam("id")
    val request = JsonObject().put("id", id)
    val options = DeliveryOptions().addHeader("action", "delete-page");

    vertx.eventBus().send<Any>(wikiDbQueue, request, options) { reply ->
      if (reply.succeeded()) {
        context.response().statusCode = 303;
        context.response().putHeader("Location", "/");
        context.response().end();
      } else {
        context.fail(reply.cause());
      }
    }
  }

}

fun main() {
  Launcher.executeCommand("run", HttpServerVerticle::class.jvmName)
}
