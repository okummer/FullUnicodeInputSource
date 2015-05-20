package com.coremedia.xml;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

/**
 * Tests of {@link FullUnicodeXmlReader}
 * regarding the XML parsing, the encoding detection, and the
 * transformation of supplementary characters.
 * These test do not involve a round trip through an XML parser.
 * Instead the output of the reader is analyzed directly.
 */
public class FullUnicodeXmlReaderTest {
  // Tests for XML parsing

  @Test
  public void testEmojiInProcessingInstruction() {
    doTest("<?\uD83D\uDE02?><elem/>");
  }

  @Test
  public void testEmojiInComment() {
    doTest("<!--\uD83D\uDE02--><elem/>");
  }

  @Test
  public void testEmojiInDoctype() {
    doTest("<!DOCTYPE \uD83D\uDE02><elem/>");
  }

  @Test
  public void testEmojiInProcessingInstructionInDoctype() {
    doTest("<!DOCTYPE <?\uD83D\uDE02?>><elem/>");
  }

  @Test
  public void testEmojiInCommentInDoctype() {
    doTest("<!DOCTYPE <!--\uD83D\uDE02-->><elem/>");
  }

  @Test
  public void testEmojiInTagInDoctype() {
    doTest("<!DOCTYPE name [<!ELEMENT \uD83D\uDE02 EMPTY>]><elem/>");
  }

  @Test
  public void testEmojiInAttributeInDoctype() {
    doTest("<!DOCTYPE name SYSTEM '\uD83D\uDE02'><elem/>");
  }

  @Test
  public void testEmojiInAttributeInDoctypeWithDoubleQuotes() {
    doTest("<!DOCTYPE name SYSTEM \"\uD83D\uDE02\"><elem/>");
  }

  @Test
  public void testEmojiInAttributeInAttlistInDoctype() {
    doTest("<!DOCTYPE name [<!ATTLIST name CDATA '\uD83D\uDE02'>]><elem/>",
            "<!DOCTYPE name [<!ATTLIST name CDATA '&#128514;'>]><elem/>");
  }

  @Test
  public void testEmojiInAttributeInAttlistInDoctypeWithDoubleQuotes() {
    doTest("<!DOCTYPE name [<!ATTLIST name CDATA \"\uD83D\uDE02\">]><elem/>",
            "<!DOCTYPE name [<!ATTLIST name CDATA \"&#128514;\">]><elem/>");
  }

  @Test
  public void testEmojiInCharacters() {
    doTest("<elem>\uD83D\uDE02</elem>");
  }

  @Test
  public void testEmojiInEntity() {
    doTest("<elem>&\uD83D\uDE02;</elem>");
  }

  @Test
  public void testEmojiInCdata() {
    doTest("<elem><![CDATA[\uD83D\uDE02]]></elem>");
  }

  @Test
  public void testEmojiInTagName() {
    doTest("<elem\uD83D\uDE02 attr='value'/>");
  }

  @Test
  public void testEmojiInAttributeName() {
    doTest("<elem attr\uD83D\uDE02='value'/>");
  }

  @Test
  public void testEmojiInAttribute() {
    doTest("<elem attr='\uD83D\uDE02'/>",
            "<elem attr='&#128514;'/>");
  }

  @Test
  public void testEmojiInAttributeWithDoubleQuotes() {
    doTest("<elem attr=\"\uD83D\uDE02\"/>",
            "<elem attr=\"&#128514;\"/>");
  }

  @Test
  public void testCombined() {
    doTest("<!DOCTYPE name [<!ATTLIST name CDATA \"\uD83D\uDE02\">]><elem/>",
            "<!DOCTYPE name [<!ATTLIST name CDATA \"&#128514;\">]><elem/>");


    doTest("<?\uD83D\uDE02?>" +
                    "<!DOCTYPE \uD83D\uDE02 SYSTEM '\uD83D\uDE02' [" +
                    "<!ATTLIST \uD83D\uDE02 CDATA \"\uD83D\uDE02\">" +
                    "<?\uD83D\uDE02?>" +
                    "<!--\uD83D\uDE02-->" +
                    "<!ELEMENT \uD83D\uDE02 EMPTY>" +
                    "]>" +
                    "<!--\uD83D\uDE02-->" +
                    "<\uD83D\uDE02 \uD83D\uDE02=\"\uD83D\uDE02\" \uD83D\uDE03='\uD83D\uDE02'>" +
                    "&\uD83D\uDE02;" +
                    "\uD83D\uDE02" +
                    "<![CDATA[\uD83D\uDE02]]>" +
                    "<\uD83D\uDE02 \uD83D\uDE03='\uD83D\uDE02' \uD83D\uDE02=\"\uD83D\uDE02\"/>" +
                    "</\uD83D\uDE02>",
            "<?\uD83D\uDE02?>" +
                    "<!DOCTYPE \uD83D\uDE02 SYSTEM '\uD83D\uDE02' [" +
                    "<!ATTLIST \uD83D\uDE02 CDATA \"&#128514;\">" +
                    "<?\uD83D\uDE02?>" +
                    "<!--\uD83D\uDE02-->" +
                    "<!ELEMENT \uD83D\uDE02 EMPTY>" +
                    "]>" +
                    "<!--\uD83D\uDE02-->" +
                    "<\uD83D\uDE02 \uD83D\uDE02=\"&#128514;\" \uD83D\uDE03='&#128514;'>" +
                    "&\uD83D\uDE02;" +
                    "\uD83D\uDE02" +
                    "<![CDATA[\uD83D\uDE02]]>" +
                    "<\uD83D\uDE02 \uD83D\uDE03='&#128514;' \uD83D\uDE02=\"&#128514;\"/>" +
                    "</\uD83D\uDE02>");
  }

  // Tests for encoding detection

  @Test
  public void testGuessEncodingUTF32LEWithBOM() {
    doTestEncoding("UTF-32LE", true);
  }

  @Test
  public void testGuessEncodingUTF32BEWithBOM() {
    doTestEncoding("UTF-32BE", true);
  }

  @Test
  public void testGuessEncodingUTF16LEWithBOM() {
    doTestEncoding("UTF-16LE", true);
  }

  @Test
  public void testGuessEncodingUTF16BEWithBOM() {
    doTestEncoding("UTF-16BE", true);
  }

  @Test
  public void testGuessEncodingUTF8WithBOM() {
    doTestEncoding("UTF-8", true);
  }

  @Test
  public void testGuessEncodingUTF32LE() {
    doTestEncoding("UTF-32LE", false);
  }

  @Test
  public void testGuessEncodingUTF32BE() {
    doTestEncoding("UTF-32BE", false);
  }

  @Test
  public void testGuessEncodingUTF16LE() {
    doTestEncoding("UTF-16LE", false);
  }

  @Test
  public void testGuessEncodingUTF16BE() {
    doTestEncoding("UTF-16BE", false);
  }

  @Test
  public void testGuessEncodingUTF8() {
    doTestEncoding("UTF-8", false);
  }

  @Test
  public void testGuessEncodingCp1047() {
    doTestEncoding("Cp1047", false);
  }

  @Test
  public void testGuessEncodingISO88591() {
    doTestEncoding("ISO-8859-1", false);
  }

  @Test
  public void testGuessEncodingISO885915() {
    doTestEncoding("ISO-8859-15", false);
  }

  /**
   * Test encoding detection with an umlaut for all encodings that
   * support encoding umlauts (excluding known exceptions).
   *
   * @throws UnsupportedEncodingException
   */
  @Test
  public void testGuessEncodingsWithAUmlaut() throws UnsupportedEncodingException {
    for (String charset : new HashSet<String>(Charset.availableCharsets().keySet())) {
      // Compound text is an extremely verbose encoding not suitable for XML storage.
      if (Charset.forName(charset).canEncode() && !charset.contains("COMPOUND_TEXT")) {
        // Check that all necessary characters can be encoded.
        String testString = "\u00c4ax<&?'";
        if (testString.equals(new String(testString.getBytes(charset), charset))) {
          doTestEncoding(charset, false);
        }
      }
    }
  }

  @Test
  public void testSetEncoding() {
    String input = "<elem>\u20ac\u20ac</elem>";
    doTest(input, input, "ISO-8859-15", true);
  }

  private void doTestEncoding(String encoding, boolean includeBOM) {
    String inputWithHeader = "<?xml encoding='" + encoding + "'?>" +
            "<elem>\u00c4</elem>";
    doTest((includeBOM ? "\ufeff" : "") + inputWithHeader, inputWithHeader, encoding, false);
  }

  private void doTest(String input) {
    doTest(input, input);
  }

  private void doTest(String input, String expectedOutput) {
    doTest(input, expectedOutput, "UTF-8", false);
  }

  private void doTest(String input, String expectedOutput, String encoding, boolean forceEncoding) {
    byte[] inputBytes = input.getBytes(Charset.forName(encoding));
    ByteArrayInputStream inputStream = new ByteArrayInputStream(inputBytes);
    //noinspection IOResourceOpenedButNotSafelyClosed
    FullUnicodeXmlReader reader = new FullUnicodeXmlReader();
    reader.setByteStream(inputStream);
    if (forceEncoding) {
      reader.setEncoding(encoding);
    }

    doTest(reader, expectedOutput);
  }

  private void doTest(FullUnicodeXmlReader reader, String expectedOutput) {
    try {
      try {
        char[] buffer = new char[1000];
        int len = reader.read(buffer);
        String output = new String(buffer, 0, len);
        // Ignore initial BOMs in output. Some encodings preserve them, some don't.
        if (output.charAt(0) == '\ufeff') {
          output = output.substring(1);
        }
        assertEquals(expectedOutput, output);

        assertEquals(0, reader.read(buffer, 0, 0));
        assertEquals(-1, reader.read(buffer, 0, 1));
      } finally {
        reader.close();
      }
    } catch (IOException e) {
      throw new IllegalStateException("IOException not expected", e);
    }
  }

  // Tests that cannot be performed on the basis of byte streams,
  // because they involve incorrectly formed character streams.

  @Test
  public void testLoneHighSurrogate() {
    doTestWithReader("<elem attr=\"\uD83D\"/>");
  }

  @Test
  public void testLoneLowSurrogate() {
    doTestWithReader("<elem attr=\"\uDE02\"/>");
  }

  private void doTestWithReader(String input) {
    //noinspection IOResourceOpenedButNotSafelyClosed
    FullUnicodeXmlReader reader = new FullUnicodeXmlReader();
    reader.setCharacterStream(new StringReader(input));
    doTest(reader, input);
  }
}