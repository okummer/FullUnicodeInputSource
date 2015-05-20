package com.coremedia.xml;

import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.Reader;

/**
 * An input source that replaces supplementary characters in an XML input stream
 * with character entities if they appear as attribute values.
 * This works around the bug JDK-8058175 in the Xerces is provided with
 * Java 8 and earlier.
 * The specified encoding is used for decoding a given an input stream.
 * If no encoding is specified, the encoding is guessed and/or read from
 * the XML header.
 */
public class FullUnicodeInputSource extends InputSource {
  /**
   * The reader that performs the actual parsing and transformation of the input stream.
   *
   * @noinspection IOResourceOpenedButNotSafelyClosed
   */
  private final FullUnicodeXmlReader reader = new FullUnicodeXmlReader();

  /**
   * Create a new input source from the given byte stream.
   *
   * @param input the input stream
   */
  public FullUnicodeInputSource(InputStream input) {
    setByteStream(input);
  }

  /**
   * Create a new input source from the given character stream.
   *
   * @param characterStream the input stream
   */
  public FullUnicodeInputSource(Reader characterStream) {
    setCharacterStream(characterStream);
  }

  /**
   * Create a new input source from the given system id.
   * The id may be an absolute URI or a file path.
   *
   * @param systemId the system id
   */
  public FullUnicodeInputSource(String systemId) {
    setSystemId(systemId);
  }

  @Override
  public Reader getCharacterStream() {
    return reader;
  }

  @Override
  public void setByteStream(InputStream byteStream) {
    reader.setByteStream(byteStream);
  }

  @Override
  public void setCharacterStream(Reader characterStream) {
    reader.setCharacterStream(characterStream);
  }

  @Override
  public void setSystemId(String systemId) {
    super.setSystemId(systemId);
    reader.setSystemId(systemId);
  }

  @Override
  public void setEncoding(String encoding) {
    reader.setEncoding(encoding);
  }
}
