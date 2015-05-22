package com.coremedia.xml;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

/**
 * Tests of {@link FullUnicodeInputSource} regarding the priority of sources,
 * the lifecycle, and the interaction with an XML parser.
 */
public class FullUnicodeInputSourceTest {
  /**
   * The round-trip test for the actual workaround: when using an ordinary
   * {@link org.xml.sax.InputSource}, the value of the second attribute
   * would be garbled.
   */
  @Test
  public void testEmojiInAttribute() throws Exception {
    byte[] inputBytes = "<elem attr1=\"\uD83D\uDE02\" attr2=\"\uD83D\uDE02\"/>".getBytes(Charset.forName("UTF-8"));
    ByteArrayInputStream inputStream = new ByteArrayInputStream(inputBytes);
    InputSource inputSource = new FullUnicodeInputSource(inputStream);
    checkInputSource("<elem attr1=\"\uD83D\uDE02\" attr2=\"\uD83D\uDE02\"/>", inputSource);
  }

  // Tests for the source configuration

  @Test
  public void testReaderTakesPreference() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<a/>".getBytes("UTF-8")));
    inputSource.setCharacterStream(new StringReader("<b/>"));
    inputSource.setSystemId("file:///nothing");
    checkInputSource("<b/>", inputSource);
  }

  @Test
  public void testInputStreamCanBeOverridden() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<a/>".getBytes("UTF-8")));
    inputSource.setByteStream(new ByteArrayInputStream("<b/>".getBytes("UTF-8")));
    checkInputSource("<b/>", inputSource);
  }

  @Test
  public void testAbsoluteSystemId() throws Exception {
    File tempFile = File.createTempFile("FullUnicodeInputSourceTest", "xml");
    try {
      FileUtils.write(tempFile, "<a/>");
      InputSource inputSource = new FullUnicodeInputSource(tempFile.getAbsoluteFile().toURI().toString());
      checkInputSource("<a/>", inputSource);
    } finally {
      FileUtils.deleteQuietly(tempFile);
    }
  }

  @Test
  public void testRelativeSystemId() throws Exception {
    File tempFile = File.createTempFile("FullUnicodeInputSourceTest", "xml");
    try {
      FileUtils.write(tempFile, "<a/>");
      InputSource inputSource = new FullUnicodeInputSource(tempFile.getAbsolutePath());
      checkInputSource("<a/>", inputSource);
    } finally {
      FileUtils.deleteQuietly(tempFile);
    }
  }

  @Test
  public void testSetEncoding() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<a>€</a>".getBytes("ISO-8859-15")));
    inputSource.setEncoding("ISO8859_15");
    checkInputSource("<a>€</a>", inputSource);
  }

  @Test
  public void testEncodingTakesPrecedence() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<?xml version='1.0' encoding='UTF-8'?><a>€</a>".getBytes("ISO-8859-15")));
    inputSource.setEncoding("ISO8859_15");
    checkInputSource("<a>€</a>", inputSource);
  }

  // Lifecycle tests

  @Test(expected = IllegalStateException.class)
  public void testSetCharacterStreamAfterRead() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new StringReader("<a/>"));
    inputSource.getCharacterStream().read();
    inputSource.setCharacterStream(new StringReader("<b/>"));
  }

  @Test(expected = IllegalStateException.class)
  public void testSetByteStreamAfterRead() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<a/>".getBytes("UTF-8")));
    inputSource.getCharacterStream().read();
    inputSource.setByteStream(new ByteArrayInputStream("<b/>".getBytes("UTF-8")));
  }

  @Test(expected = IllegalStateException.class)
  public void testSetSystemIdAfterRead() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<a/>".getBytes("UTF-8")));
    inputSource.getCharacterStream().read();
    inputSource.setSystemId("file:///nothing");
  }

  @Test(expected = IllegalStateException.class)
  public void testSetEncodingAfterRead() throws Exception {
    InputSource inputSource = new FullUnicodeInputSource(new ByteArrayInputStream("<a/>".getBytes("UTF-8")));
    inputSource.getCharacterStream().read();
    inputSource.setEncoding("UTF-8");
  }

  // Utility methods

  private void checkInputSource(String expected, InputSource inputSource) throws Exception {
    // For verifying the interoperability with the XML stack,
    // go through a full parse-DOM-unparse cyle.
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    Document document = db.parse(inputSource);

    Transformer transformer = TransformerFactory.newInstance().newTransformer();
    StreamResult streamResult = new StreamResult(new StringWriter());
    Source source = new DOMSource(document);
    transformer.transform(source, streamResult);

    // Drop the XML header, which is not preserved in a DOM.
    String stringResult = streamResult.getWriter().toString();
    int headerEnd = stringResult.lastIndexOf("?>");
    if (headerEnd != -1) {
      stringResult = stringResult.substring(headerEnd + 2);
    }
    // Normalize the test character, which might be represented as an entity
    // or as a character.
    stringResult = stringResult.replace("&#128514;", "\uD83D\uDE02");

    assertEquals(expected, stringResult);
  }
}