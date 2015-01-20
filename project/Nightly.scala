/*
 * Copyright 2014 IBM Corp.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.text.SimpleDateFormat
import java.util.Calendar

import sbt._
import Keys._
import Classpaths._

object NightlyKeys {
  lazy val nightlyScopeFilter = settingKey[ScopeFilter](
    "Control sources to be included in nightly."
  )

  lazy val nightlyProjectFilter = settingKey[ScopeFilter.ProjectFilter](
    "Control projects to be included in nightly."
  )

  lazy val nightlyConfigurationFilter = settingKey[ScopeFilter.ConfigurationFilter](
    "Control configurations to be included in nightly."
  )

  lazy val nightlyVersion = settingKey[String](
    "Represents the version to use with nightly builds."
  )

  lazy val nightlyRepository = settingKey[Option[Resolver]](
    "Represents the repository destination to use with nightly builds."
  )

  lazy val nightly = taskKey[Unit](
    "Generates the nightly jars to associate with nightly builds."
  )
}

object Nightly {
  import NightlyKeys._

  private val NightlyVersionPostfix = "-NIGHTLY"

  /**
   * Returns the version in the form of year/month/day-NIGHTLY without the '/'.
   */
  private lazy val _nightlyVersion = Def.setting {
    val simpleDateFormat = new SimpleDateFormat("yyyyMMdd")
    val today = Calendar.getInstance().getTime

    val dateString = simpleDateFormat.format(today)

    dateString + NightlyVersionPostfix
  }

  /**
   * Returns the repository in the form of a local path.
   */
  private lazy val _nightlyRepository = Def.setting {
    Some("Local nightly repository" at (crossTarget.value / "nightly").getAbsolutePath)
  }

  /**
   * Publishes jars using publish with the version set to nightly and
   * destination set to a local nightly repository.
   */
  private lazy val _nightlyImpl = Def.taskDyn {
    val filter = nightlyScopeFilter.value

    publish.all(filter)
  }

  /**
   * Represents the settings associated with the Nightly setup.
   *
   * @param config The configuration to use for the underlying settings
   *
   * @return The sequence of setting/task keys
   */
  def settingsForConfig(config: Configuration) = Seq(
    nightlyScopeFilter := ScopeFilter(
      nightlyProjectFilter.value, nightlyConfigurationFilter.value
    ),
    nightlyProjectFilter := inAnyProject,
    nightlyConfigurationFilter := inConfigurations(config),
    nightlyVersion := _nightlyVersion.value,
    nightlyRepository := _nightlyRepository.value,
    nightly := _nightlyImpl.value
  )

  /**
   * Default settings tied to Compile.
   */
  lazy val settings = settingsForConfig(Compile)
}