package org.ergoplatform.it

import java.io.File

import akka.japi.Option.Some
import com.typesafe.config.Config
import org.ergoplatform.it.container.{IntegrationSuite, Node}
import org.scalatest.{FreeSpec, OptionValues}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Random

class NodeRecoverySpec
  extends FreeSpec
    with IntegrationSuite
    with OptionValues {

  val shutdownAtHeight: Int = 5

  val localVolume = s"$localDataDir/node-recovery-spec/${Random.nextInt()}/data"
  val remoteVolume = "/app"

  val dir = new File(localVolume)
  dir.mkdirs()

  val offlineGeneratingPeer: Config = specialDataDirConfig(remoteVolume)
    .withFallback(offlineGeneratingPeerConfig)
    .withFallback(nodeSeedConfigs.head)

  val node: Node = docker.startNode(offlineGeneratingPeer, specialVolumeOpt = Some((localVolume, remoteVolume))).get

  "Node recovery after unexpected shutdown" in {

    val result = node.waitForHeight(shutdownAtHeight)
      .flatMap(_ => node.headerIdsByHeight(shutdownAtHeight))
      .flatMap { ids =>
        docker.forceStopNode(node.containerId)
        val restartedNode = docker
          .startNode(offlineGeneratingPeer, specialVolumeOpt = Some((localVolume, remoteVolume))).get
        restartedNode.waitForHeight(shutdownAtHeight)
          .flatMap(_ => restartedNode.headerIdsByHeight(shutdownAtHeight))
          .map(_.headOption.value shouldEqual ids.headOption.value)
      }

    Await.result(result, 4.minutes)
  }
}
