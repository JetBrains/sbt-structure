package sbt.jetbrains

import sbt.Attributed

import java.nio.file.{Path => NioPath}
import java.io.File

object ClassathOpsCompat extends ClassathOpsCompat

trait ClassathOpsCompat {
  type FileRef = java.io.File
  type Out = java.io.File

  def toNioPath(a: Attributed[File]): NioPath =
    a.data.toPath()

  def toFile(a: Attributed[File]): File =
    a.data

  def toNioPaths(cp: Seq[Attributed[File]]): Vector[NioPath] =
    cp.map(_.data.toPath()).toVector

  def toFiles(cp: Seq[Attributed[File]]): Vector[File] =
    cp.map(_.data).toVector

  def toAttributedFiles(cp: Seq[Attributed[File]]): Seq[Attributed[File]] =
    cp
}

