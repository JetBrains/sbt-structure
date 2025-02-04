package org.jetbrains.sbt.integrationTests.utils

import org.dom4j.Element
import org.dom4j.io.{OutputFormat, SAXReader, XMLWriter}

import java.io.{StringReader, StringWriter}
import scala.collection.JavaConverters.asScalaIteratorConverter

object XmlUtils {

  /** Load and sanitize XML to exclude values that cause troubles when comparing in tests */
  def readXmlStringSanitizedAndFormatted(xmlString: String): String = {
    val reader = new SAXReader()
    val document = reader.read(new StringReader(xmlString))

    def removeValueTags(node: Element): Unit = {
      val elements = node.elementIterator().asScala.toSeq
      elements.foreach { element =>
        if (element.getName == "setting") {
          element.elements("value").clear()
        }
        removeValueTags(element)
      }
    }

    removeValueTags(document.getRootElement)

    val format: OutputFormat = OutputFormat.createPrettyPrint()
    format.setIndentSize(2)
    //format.setExpandEmptyElements(true)
    format.setNewLineAfterDeclaration(false)
    format.setTrimText(true)
    format.setPadText(false) //workaround for https://github.com/dom4j/dom4j/issues/147

    val stringWriter = new StringWriter()
    val writer = new XMLWriter(stringWriter, format)
    writer.write(document)

    stringWriter.toString
  }
}
