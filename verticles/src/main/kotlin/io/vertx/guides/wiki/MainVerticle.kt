package io.vertx.guides.wiki

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Launcher
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.web.templ.freemarker.FreeMarkerTemplateEngine
import io.vertx.guides.wiki.verticles.db.WikiDatabaseVerticle
import io.vertx.kotlin.core.deploymentOptionsOf
import org.slf4j.LoggerFactory
import kotlin.reflect.jvm.jvmName


/**
 * @AbstractVerticle provides for:

1. life-cycle start and stop methods to override,
2. a protected field called vertx that references the Vert.x environment where the verticle is being deployed,
3. an accessor to some configuration object that allows passing external configuration to a verticle.

 */

private val SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key,Name varchar(255) unique, Content clob)"
private val SQL_GET_PAGE = "select Id, Content from Pages where Name = ?"
private val SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)"
private val SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?"
private val SQL_ALL_PAGES = "select Name from Pages"
private val SQL_DELETE_PAGE = "delete from Pages where Id = ?"


val EMPTY_PAGE_MARKDOWN =
  """
      # A new page

      Feel-free to write in Markdown!
"""

var dbClient: JDBCClient? = null

class MainVerticle : AbstractVerticle() {

  companion object {
    val LOGGER = LoggerFactory.getLogger("MainVerticle")
  }

  var templateEngine: FreeMarkerTemplateEngine? = null

  /**
  There are 2 forms of start (and stop) methods:
  1 with no argument and 1 with a future object reference.
  The no-argument variants imply that the verticle initialization or house-keeping phases
  always succeed unless an exception is being thrown. The variants with a future object provide a
  more fine-grained approach to eventually signal that operations succeeded or not. Indeed, some
  initialization or cleanup code may require asynchronous operations, so reporting via a future object
  naturally fits with asynchronous idioms.
   */
  override fun start(startFuture: Future<Void>) {
    //Deploying a verticle is an asynchronous operation, so we need a Future for that. The String
    //parametric type is because a verticle gets an identifier when successfully deployed
    val dbVerticleDeployment = Future.future<String>()

    vertx.deployVerticle(WikiDatabaseVerticle(), dbVerticleDeployment)
//Sequential composition with compose allows to run one asynchronous operation after the other.
//When the initial future completes successfully, the composition function is invoked

    dbVerticleDeployment.compose { id ->
      val httpVerticleDeployment = Future.future<String>()
      vertx.deployVerticle("io.vertx.guides.wiki.verticles.http.HttpServerVerticle")
      //The DeploymentOption class allows to specify a number of parameters and especially the number
      //of instances to deploy
      deploymentOptionsOf(instances = 2)
      httpVerticleDeployment
    }.setHandler { ar ->
      if (ar.succeeded()) {
        startFuture.complete()
      } else {
        startFuture.fail(ar.cause())
      }
    }
  }

}
fun main() {
  Launcher.executeCommand("run", MainVerticle::class.jvmName)
}
