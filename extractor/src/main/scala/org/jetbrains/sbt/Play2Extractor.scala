package org.jetbrains.sbt

import sbt.Keys._
import sbt._

import scala.collection.mutable
import scala.xml.XML

/**
 * User: Dmitry.Naydanov
 * Date: 15.09.14.
 */
object Play2Extractor {
  private val PLAY_PLUGIN = "playPlugin"

  //marker
  private val PLAY_VERSION = "playVersion"
  //default
  private val TEMPLATES_IMPORT = "twirlTemplatesImports"
  private val TEMPLATE_IMPORT_ALIAS = "twirlTemplateImports"
  private val ROUTES_IMPORT = "playRoutesImports"
  //options
  private val TEST_OPTIONS = "testOptions"
  private val TEMPLATE_FORMATS = "playTemplatesFormats"
  //dirs
  private val PLAY_CONF_DIR = "playConf"
  private val SOURCE_DIR = "sourceDirectory"

  private val GLOBAL_TAG = "$global$"

  @inline private def processPath(path: String) = path stripSuffix "/" stripSuffix "\\"

  private class KeyChain(val markerKey: KeyWithScope, val keys: Seq[KeyWithScope], val aliasKeys: Seq[AliasKey] = Seq.empty) {
    protected val allKeys = (markerKey +: keys) ++ aliasKeys

    def processKey(key: ScopedKey[_]): Unit = {
      allKeys.find(_.extract(key))
    }
  }

  private abstract class KeyWithScope(val label: String, val projectRef: ProjectRef, val structureData: Settings[Scope]) {
    val myValues = mutable.HashMap[String, PlayValue]()

    def extract(key: ScopedKey[_]): Boolean = {
      val attrKey = key.key

      if (attrKey.label != label) false else {
        val project = key.scope.project match {
          case Select(ProjectRef(_, pName)) => Some(pName)
          case Global => Some(GLOBAL_TAG)
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

  private class AliasKey(label: String, val delegate: KeyWithScope)(implicit data: (ProjectRef, Settings[Scope]))
    extends KeyWithScope(label, data._1, data._2) {
    override def saveValue(key: String, value: PlayValue) = delegate.saveValue(key, value)

    override def transform(any: Any): Option[PlayValue] = delegate.transform(any)
  }

  private class StringKey(label: String)(implicit data: (ProjectRef, Settings[Scope]))
    extends KeyWithScope(label, data._1, data._2) {
    override def transform(any: Any): Option[PlayValue] = any match {
      case str: String => Some(PlayString(str))
      case null => Some(PlayString(""))
      case _ => None
    }
  }

  private class SeqStringKey(label: String)(implicit data: (ProjectRef, Settings[Scope]))
    extends KeyWithScope(label, data._1, data._2) {
    override def transform(any: Any): Option[PlayValue] = {
      any match {
        case seq: Seq[_] => Some(PlaySeqString(seq.map(_.toString)))
        case _ => None
      }
    }
  }

  private class FileKey(label: String)(implicit data: (ProjectRef, Settings[Scope]))
    extends KeyWithScope(label, data._1, data._2) {
    override def transform(any: Any): Option[PlayValue] = {
      any match {
        case file: File => Some(PlayString(processPath(file.getAbsolutePath)))
        case _ => None
      }
    }
  }

  private class PresenceKey(label: String, tagName: String)(implicit data: (ProjectRef, Settings[Scope]))
    extends KeyWithScope(label, data._1, data._2) {
    override def transform(any: Any): Option[PlayValue] = any match {
      case uri: URI =>
        val file = new File(uri)
        if (file.exists())
          Some(PlayString(processPath(file.getAbsolutePath)))
        else
          Some(PlayString(uri.toString))
      case _ => None
    }

    override def extract(key: ScopedKey[_]): Boolean = {
      val attrKey = key.key

      if (attrKey.label != label) false else {
        val project = key.scope.project match {
          case Select(ProjectRef(uri, pName)) => Some((pName, uri))
          case Global => Some((GLOBAL_TAG, ""))
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

class Play2Extractor(structure: sbt.Load.BuildStructure, projectRef: ProjectRef, state: State) {
  import org.jetbrains.sbt.Play2Extractor._

  private implicit val data = (projectRef, structure.data)

  //marker key
  private val PLAY_PLUGIN_KEY = new PresenceKey(PLAY_PLUGIN, "uri")
  //options keys
  private val PLAY_VERSION_KEY = new StringKey(PLAY_VERSION)
  private val TEST_OPTIONS_KEY = new StringKey(TEST_OPTIONS)
  //imports keys
  private val TEMPLATES_IMPORT_KEY = new SeqStringKey(TEMPLATES_IMPORT) {
    override def transform(any: Any): Option[PlayValue] =
      super.transform(any).map {
        case PlaySeqString(strings) => PlaySeqString(strings.map {
          case "views.%format%._" => "views.xml._"
          case value => value
         })
        case value => value
      }
  }
  private val TEMPLATE_IMPORT_KEY_ALIAS = new AliasKey(TEMPLATE_IMPORT_ALIAS, TEMPLATES_IMPORT_KEY)
  private val ROUTES_IMPORT_KEY = new SeqStringKey(ROUTES_IMPORT)
  //dirs keys
  private val PLAY_CONF_DIR_KEY = new FileKey(PLAY_CONF_DIR)
  private val SOURCE_DIR_KEY = new FileKey(SOURCE_DIR)

  private val ALL_USUAL_KEYS = Seq(PLAY_VERSION_KEY, TEST_OPTIONS_KEY, TEMPLATES_IMPORT_KEY,
    ROUTES_IMPORT_KEY, PLAY_CONF_DIR_KEY, SOURCE_DIR_KEY)

  private val ALL_ALIAS_KEYS = Seq(TEMPLATE_IMPORT_KEY_ALIAS)

  private val chain = new KeyChain(PLAY_PLUGIN_KEY, ALL_USUAL_KEYS, ALL_ALIAS_KEYS)

  def extract(): Option[Play2Data] = {
    val keys = state.attributes.get(sessionSettings) match {
      case Some(SessionSettings(_, _, settings, _, _, _)) => settings map { _.key }
      case _ => Seq.empty
    }

    keys.foreach(chain.processKey)

    val markerKey = chain.markerKey

    if (markerKey.myValues.isEmpty) None else {
      val foundProjects = markerKey.myValues.keySet.toSet

      val otherKeys = chain.keys.map {
        case k => k.toKeyInfo(foundProjects)
      }

      Some(Play2Data(markerKey.toKeyInfo +: otherKeys))
    }
  }
}
