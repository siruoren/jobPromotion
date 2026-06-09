package com.siruoren.jobpromotion.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utility class for XML processing operations.
 * Extracted from PromotionService to promote reuse and separation of concerns.
 */
public class XmlUtil {

    private static final Logger LOGGER = Logger.getLogger(XmlUtil.class.getName());
    private static final Pattern GARBLED_CHAR_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

    private XmlUtil() {
    }

    /**
     * Clean job config XML: remove triggers, monitor triggers, and build discarder settings.
     */
    public static String cleanJobConfigXml(String configXml) {
        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(configXml)));

            cleanTriggers(doc);
            cleanMonitorTriggers(doc);
            cleanBuildDiscarder(doc);

            return transformDocument(doc);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to clean config XML, returning original", e);
            return cleanGarbledChars(configXml);
        }
    }

    /**
     * Clean folder config XML: only clean garbled chars, don't remove triggers etc.
     */
    public static String cleanFolderConfigXml(String configXml) {
        return cleanGarbledChars(configXml);
    }

    /**
     * Remove garbled/invalid XML characters from a string.
     */
    public static String cleanGarbledChars(String input) {
        if (input == null) return "";
        return GARBLED_CHAR_PATTERN.matcher(input).replaceAll("");
    }

    private static DocumentBuilderFactory createSecureDocumentBuilderFactory() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory;
    }

    private static void cleanTriggers(Document doc) {
        NodeList triggersList = doc.getElementsByTagName("triggers");
        for (int i = 0; i < triggersList.getLength(); i++) {
            Element triggers = (Element) triggersList.item(i);
            triggers.setTextContent("");
        }
    }

    private static void cleanMonitorTriggers(Document doc) {
        String[] monitorTags = {"monitor", "buildMonitor", "monitorJobs", "hudson.plugins.buildmonitor.BuildMonitor"};
        for (String tag : monitorTags) {
            NodeList nodes = doc.getElementsByTagName(tag);
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                el.getParentNode().removeChild(el);
            }
        }
    }

    private static void cleanBuildDiscarder(Document doc) {
        NodeList discarderList = doc.getElementsByTagName("buildDiscarder");
        for (int i = 0; i < discarderList.getLength(); i++) {
            Element discarder = (Element) discarderList.item(i);
            discarder.setTextContent("");
        }
    }

    private static String transformDocument(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        String result = writer.toString();

        return cleanGarbledChars(result);
    }
}
