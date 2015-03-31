/*
 * Copyright 2014 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ibm.spark.boot

import java.io.{File, OutputStream}

import com.ibm.spark.utils.KeyValuePairUtils
import com.typesafe.config.{ConfigSyntax, ConfigParseOptions, Config, ConfigFactory}
import joptsimple.util.KeyValuePair
import joptsimple.{OptionParser, OptionSpec}

import scala.collection.JavaConverters._

class CommandLineOptions(args: Seq[String]) {
  private val parser = new OptionParser()
  parser.allowsUnrecognizedOptions()

  /*
   * Options supported by our kernel.
   */
  private val _help =
    parser.acceptsAll(Seq("help", "h").asJava, "display help information").forHelp()

  private val _version =
    parser.acceptsAll(Seq("version", "v").asJava, "display version information")

  private val _profile =
    parser.accepts("profile", "path to IPython JSON connection file")
      .withRequiredArg().ofType(classOf[File])

  private val _master =
    parser.accepts("master", "location of master Spark node")
      .withRequiredArg().ofType(classOf[String])

  private val _ip =
    parser.accepts("ip", "ip used to bind sockets")
      .withRequiredArg().ofType(classOf[String])

  private val _stdin_port = parser.accepts(
    "stdin-port", "port of the stdin socket"
  ).withRequiredArg().ofType(classOf[Int])

  private val _shell_port = parser.accepts(
    "shell-port", "port of the shell socket"
  ).withRequiredArg().ofType(classOf[Int])

  private val _iopub_port = parser.accepts(
    "iopub-port", "port of the iopub socket"
  ).withRequiredArg().ofType(classOf[Int])

  private val _control_port = parser.accepts(
    "control-port", "port of the control socket"
  ).withRequiredArg().ofType(classOf[Int])

  private val _heartbeat_port = parser.accepts(
    "heartbeat-port", "port of the heartbeat socket"
  ).withRequiredArg().ofType(classOf[Int])

  private val _spark_defaults =
    parser.accepts("spark-defaults", "attempt to load Apache Spark default configuration (SPARK_HOME must be set")

  private val _spark_configuration = parser.acceptsAll(
    Seq("spark-configuration", "S").asJava,
    "configuration setting for Apache Spark"
  ).withRequiredArg().ofType(classOf[KeyValuePair])

  private val _magic_url =
    parser.accepts("magic-url", "path to a magic jar")
      .withRequiredArg().ofType(classOf[String])

  private val options = parser.parse(args: _*)

  /*
   * Helpers to determine if an option is provided and the value with which it
   * was provided.
   */

  private def has[T](spec: OptionSpec[T]): Boolean =
    options.has(spec)

  private def get[T](spec: OptionSpec[T]): Option[T] =
    Some(options.valueOf(spec)).filter(_ != null)

  private def getAll[T](spec: OptionSpec[T]): Option[List[T]] =
    Some(options.valuesOf(spec).asScala.toList).filter(_ != null)

  /*
   * Expose options in terms of their existence/value.
   */

  val help: Boolean = has(_help)

  val version: Boolean = has(_version)

  /*
   * Config object has 3 levels and fallback in this order
   * 1. Comandline Args
   * 2. Apache Spark defaults (if requested)
   * 3. --profile file
   * 4. Defaults
   */
  def toConfig: Config = {
    val profileConfig: Config = get(_profile) match {
      case Some(x) =>
        ConfigFactory.parseFile(x)
      case None =>
        ConfigFactory.empty()
    }

    val sparkDefaultsConfig = if (has(_spark_defaults)) {
      val sparkDefaultsFile = for (sparkHome <- sys.env.get("SPARK_HOME"))
        yield new File(sparkHome + "/conf/spark-defaults.conf")

      val config = for (file <- sparkDefaultsFile if file.exists())
        yield ConfigFactory.parseFile(file, ConfigParseOptions.defaults().setSyntax(ConfigSyntax.PROPERTIES))

      (for (_c <- config)
        yield ConfigFactory.parseMap(Map(
          "spark_configuration" -> _c.entrySet.asScala.map(entry => entry.getKey + "=" + entry.getValue.unwrapped()).toList.mkString(",")
        ).asInstanceOf[Map[String, AnyRef]].asJava))
      .getOrElse(ConfigFactory.empty())
    } else
      ConfigFactory.empty()

    val commandLineConfig: Config = ConfigFactory.parseMap(Map(
        "spark.master" -> get(_master),
        "stdin_port" -> get(_stdin_port),
        "shell_port" -> get(_shell_port),
        "iopub_port" -> get(_iopub_port),
        "control_port" -> get(_control_port),
        "hb_port" -> get(_heartbeat_port),
        "ip" -> get(_ip),
        "interpreter_args" -> interpreterArgs,
        "magic_urls" -> getAll(_magic_url).map(_.asJava)
          .flatMap(list => if (list.isEmpty) None else Some(list)),
        "spark_configuration" -> getAll(_spark_configuration)
          .map(list => KeyValuePairUtils.keyValuePairSeqToString(list))
          .flatMap(str => if (str.nonEmpty) Some(str) else None)
    ).flatMap(removeEmptyOptions).asInstanceOf[Map[String, AnyRef]].asJava)

    commandLineConfig.withFallback(sparkDefaultsConfig).withFallback(profileConfig).withFallback(ConfigFactory.load)
  }

  private val removeEmptyOptions: ((String, Option[Any])) => Iterable[(String, Any)] = {
    pair => if (pair._2.isDefined) Some((pair._1, pair._2.get)) else None
  }

  /**
   *
   * @return
   */
  private def interpreterArgs: Option[java.util.List[String]] = {
    args.dropWhile(_ != "--").drop(1).toList match {
      case Nil => None
      case list: List[String] => Some(list.asJava)
    }
  }

  /**
   * Prints the help message to the output stream provided.
   * @param out The output stream to direct the help message
   */
  def printHelpOn(out: OutputStream) =
    parser.printHelpOn(out)
}

