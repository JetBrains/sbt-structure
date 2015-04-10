package org.jetbrains.sbt
package extractors

import org.jetbrains.sbt.extractors.Extractor.Options
import org.jetbrains.sbt.extractors.Play2Extractor._
import sbt.Keys._
import sbt._

import scala.collection.mutable

/**
 * User: Dmitry.Naydanov
 * Date: 15.09.14.
 */
object Play2Extractor {

  private val GlobalTag = "$global$"

  @inline private def processPath(path: String) = path stripSuffix "/" stripSuffix "\\"

  private class KeyChain(val markerKey: KeyWithScope, val keys: Seq[KeyWithScope], val aliasKeys: Seq[AliasKey] = Seq.empty) {
    protected val allKeys = (markerKey +: keys) ++ aliasKeys

    def processKey(key: ScopedKey[_])(implicit state: State): Unit = {
      allKeys.find(_.extract(key))
    }
  }

  private abstract class KeyWithScope(val label: String, val projectRef: ProjectRef) {
    val myValues = mutable.HashMap[String, PlayValue]()

    def extract(key: ScopedKey[_])(implicit state: State): Boolean = {
      val structureData = Project.extract(state).structure.data
      val attrKey = key.key

      if (attrKey.label != label) false else {
        val project = key.scope.project match {
          case Select(ProjectRef(_, pName)) => Some(pName)
          case Global => Some(GlobalTag)
          case _ => None
        }

        project exists { pName =>
          SettingKey(attrKey).in(projectRef, Compile).get(structureData).flatMap(transform).exists { value =>
            saveValue(pName, value)
            true
          }
        }
      }
    }

    def saveValue(key: String, value: PlayValue) =
      myValues.put(key, value)

    def transform(any: Any): Option[PlayValue]

    def toKeyInfo(projectNames: Set[String]) =
      Play2Key(label, myValues.toMap.filter{case (p, _) => projectNames.contains(p)})

    def toKeyInfo: Play2Key =
      Play2Key(label, myValues.toMap)
  }

  private class AliasKey(label: String, val delegate: KeyWithScope)(implicit projectRef: ProjectRef)
      extends KeyWithScope(label, projectRef) {
    override def saveValue(key: String, value: PlayValue) = delegate.saveValue(key, value)
    override def transform(any: Any): Option[PlayValue] = delegate.transform(any)
  }

  private class StringKey(label: String)(implicit projectRef: ProjectRef)
      extends KeyWithScope(label, projectRef) {
    override def transform(any: Any): Option[PlayValue] = any match {
      case str: String => Some(PlayString(str))
      case null => Some(PlayString(""))
      case _ => None
    }
  }

  private class SeqStringKey(label: String)(implicit projectRef: ProjectRef)
      extends KeyWithScope(label, projectRef) {
    override def transform(any: Any): Option[PlayValue] = {
      any match {
        case seq: Seq[_] => Some(PlaySeqString(seq.map(_.toString)))
        case _ => None
      }
    }
  }

  private class FileKey(label: String)(implicit projectRef: ProjectRef)
      extends KeyWithScope(label, projectRef) {
    override def transform(any: Any): Option[PlayValue] = {
      any match {
        case file: File => Some(PlayString(processPath(file.getAbsolutePath)))
        case _ => None
      }
    }
  }

  private class PresenceKey(label: String, tagName: String)(implicit projectRef: ProjectRef)
      extends KeyWithScope(label, projectRef) {
    override def transform(any: Any): Option[PlayValue] = any match {
      case uri: URI =>
        val file = new File(uri)
        if (file.exists())
          Some(PlayString(processPath(file.getAbsolutePath)))
        else
          Some(PlayString(uri.toString))
      case _ => None
    }

    override def extract(key: ScopedKey[_])(implicit state: State): Boolean = {
      val attrKey = key.key

      if (attrKey.label != label) false else {
        val project = key.scope.project match {
          case Select(ProjectRef(uri, pName)) => Some((pName, uri))
          case Global => Some((GlobalTag, ""))
          case _ => None
        }

        project.exists {
          case (pName, uri) =>
            transform(uri).exists(p => {saveValue(pName, p); true})
            true
        }
      }
    }

    override def toKeyInfo(projectNames: Set[String]) =
      Play2Key(tagName, myValues.toMap.filter { case (p, _) => projectNames.contains(p) })

    override def toKeyInfo: Play2Key =
      Play2Key(tagName, myValues.toMap)
  }
}

class Play2Extractor(projectRef: ProjectRef) extends Extractor {

  override type Data = Play2Data

  implicit val projectRefImplicit = projectRef

  private object Keys {
    //marker key
    val PlayPlugin = new PresenceKey("playPlugin", "uri")

    //options keys
    val PlayVersion = new StringKey("playVersion")
    val TestOptions = new StringKey("testOptions")

    //imports keys
    val TemplatesImports = new SeqStringKey("twirlTemplatesImports") {
      override def transform(any: Any): Option[PlayValue] =
        super.transform(any).map {
          case PlaySeqString(strings) => PlaySeqString(strings.map {
            case "views.%format%._" => "views.xml._"
            case value => value
          })
          case value => value
        }
    }
    val TemplateImportsAlias = new AliasKey("twirlTemplateImports", TemplatesImports)
    val RoutesImports = new SeqStringKey("playRoutesImports")

    //dirs keys
    val PlayConfDirectory = new FileKey("playConf")
    val SourceDirectory = new FileKey("sourceDirectory")

    val AllUsual = Seq(PlayVersion, TestOptions, TemplatesImports,
      RoutesImports, PlayConfDirectory, SourceDirectory)

    val AllAliases = Seq(TemplateImportsAlias)
  }

  override def extract(implicit state: State, options: Options): Option[Data] = {
    val chain = new KeyChain(Keys.PlayPlugin, Keys.AllUsual, Keys.AllAliases)

    val keys = state.attributes.get(sessionSettings) match {
      case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
      case _ => Seq.empty
    }

    keys.foreach(chain.processKey)

    val markerKey = chain.markerKey
    if (markerKey.myValues.isEmpty) {
      None
    } else {
      val foundProjects = markerKey.myValues.keySet.toSet
      val otherKeys = chain.keys.map(_.toKeyInfo(foundProjects))
      Some(Play2Data(markerKey.toKeyInfo +: otherKeys))
    }
  }
}
