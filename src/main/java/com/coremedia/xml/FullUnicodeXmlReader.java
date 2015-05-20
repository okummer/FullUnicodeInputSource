package com.coremedia.xml;

import org.apache.commons.io.input.XmlStreamReader;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Deque;

/**
 * A reader that replaces supplementary characters in an XML input stream
 * with character entities if they appear as attribute values.
 * This works around the bug JDK-8058175 in the Xerces provided with
 * Java 8 and earlier.
 * The specified encoding is used for decoding a given an input stream.
 * If no encoding is specified, the encoding is guessed and/or read from
 * the XML header.
 * For the actual parsing a stack of modes is maintained.
 * Each modes searches for known character sequences that necessitate
 * the switch to another mode or possibly to the previous mode
 * if the end of a grammatical object has been found.
 * XML is sufficiently restricted that a finite look ahead is sufficient
 * to determine mode switches.
 */
final class FullUnicodeXmlReader extends Reader {
  /**
   * The size of the BufferedInputStream. Sufficiently large to avoid
   * the most obvious performance problems and to be able to reset the
   * stream after parsing the XML header.
   */
  private static final int BUFFER_SIZE = 8192;

  // Parse modes.
  // Each mode represents a grammatical unit of an XML document.
  // Each mode defines marker strings that indicate the end
  // of the grammatical unit or the start of a nested grammatical unit.

  /**
   * The parse mode for a processing instruction
   */
  private final ParseMode processingInstructionMode =
          new ParseMode().
                  withStop("?>");
  /**
   * The parse mode for a comment
   */
  private final ParseMode commentMode =
          new ParseMode().
                  withStop("-->");
  /**
   * The parse mode for a CDATA section
   */
  private final ParseMode cdataMode =
          new ParseMode().
                  withStop("]]>");
  /**
   * The parse mode for an entity
   */
  private final ParseMode entityMode =
          new ParseMode().
                  withStop(";");
  /**
   * The parse mode for an attribute in single quotes
   */
  private final ParseMode singleQuoteAttributeMode =
          new ParseMode(true).
                  withStop("'");
  /**
   * The parse mode for an attribute in double quotes
   */
  private final ParseMode doubleQuoteAttributeMode =
          new ParseMode(true).
                  withStop("\"");
  /**
   * The parse mode for a XML tag, either opening or closing
   */
  private final ParseMode tagMode =
          new ParseMode().
                  withTransition("'", singleQuoteAttributeMode).
                  withTransition("\"", doubleQuoteAttributeMode).
                  withStop(">");
  /**
   * The parse mode for an attribute in single quotes that is known
   * not to need or to allow escaping of supplementary characters.
   */
  private final ParseMode singleQuoteAttributeModeNoEscape =
          new ParseMode().
                  withStop("'");
  /**
   * The parse mode for an attribute in double quotes that is known
   * not to need or to allow escaping of supplementary characters.
   */
  private final ParseMode doubleQuoteAttributeModeNoEscape =
          new ParseMode().
                  withStop("\"");
  /**
   * The parse mode for an XML tag that is known
   * not to need or to allow escaping of supplementary characters.
   */
  private final ParseMode tagModeNoEscape =
          new ParseMode().
                  withTransition("'", singleQuoteAttributeModeNoEscape).
                  withTransition("\"", doubleQuoteAttributeModeNoEscape).
                  withStop(">");
  /**
   * The parse mode for an embedded document type definition.
   */
  private final ParseMode doctypeMode =
          new ParseMode().
                  withTransition("<?", processingInstructionMode).
                  withTransition("<!--", commentMode).
                  withTransition("<!ATTLIST", tagMode).
                  withTransition("<", tagModeNoEscape).
                  withTransition("\"", doubleQuoteAttributeModeNoEscape).
                  withTransition("'", singleQuoteAttributeModeNoEscape).
                  withStop(">");
  /**
   * The parse mode for an entire XML document.
   */
  private final ParseMode topLevelMode =
          new ParseMode().
                  withTransition("<?", processingInstructionMode).
                  withTransition("<!DOCTYPE", doctypeMode).
                  withTransition("<!--", commentMode).
                  withTransition("<![CDATA[", cdataMode).
                  withTransition("<", tagMode).
                  withTransition("&", entityMode);

  /**
   * The system id as provided through {@link #setSystemId(String)}.
   * System ids are URLs or file system paths.
   * The system id is used as a fallback if neither the reader nor
   * the input stream is provided.
   */
  private String systemId;
  /**
   * The raw (undecoded and unescaped) input stream as provided through
   * {@link #setByteStream(java.io.InputStream)}.
   */
  private InputStream rawInputStream;
  /**
   * The raw (unescaped) reader as provided through
   * {@link #setCharacterStream(java.io.Reader)}.
   * The reader takes precendence over the input stream.
   */
  private Reader rawReader;
  /**
   * The encoding as provided through {@link #setEncoding(String)}.
   * The encoding is used to decode the input stream into a character sequence.
   * If no encoding is given, the encoding is inferred from the XML header.
   */
  private String encoding;

  /**
   * The reader for retrieving the unescaped characters from the
   * backing stream. This is either the raw reader or an input stream
   * reader with an appropriate encoding.
   */
  private Reader reader;

  /**
   * A buffer storing the next few characters from the reader.
   * This allows the parser to look ahead a few characters before
   * making a mode switch, greatly reducing the number of possible
   * modes. (For example there is not mode that applies when only
   * a less than sign has been read, but when it is still undecided
   * whether a comment or an element will follow.)
   */
  private final StringBuilder lookAheadBuffer = new StringBuilder();

  /**
   * A buffer storing already transformed characters for reading
   * from this object. This buffer is used when more than one character
   * becomes available during a mode switch. (For example, the entire
   * start comment character sequence is added to this buffer at once
   * and is read character by character later on.)
   */
  private final StringBuilder transformed = new StringBuilder();

  /**
   * A stack of parse modes. The first element of the stack defines
   * the strategy for parsing the character stream at this point.
   */
  private final Deque<ParseMode> modes = new ArrayDeque<ParseMode>(Arrays.asList(topLevelMode));

  /**
   * Set the encoding.
   * The encoding is used to decode the input stream into a character sequence.
   * If no encoding is given, the encoding is inferred from the XML header.
   *
   * @param encoding the encoding
   */
  public void setEncoding(String encoding) {
    assertReadNotStarted();
    this.encoding = encoding;
  }

  /**
   * Set the raw (undecoded and unescaped) input stream.
   *
   * @param byteStream the input stream
   */
  public void setByteStream(InputStream byteStream) {
    assertReadNotStarted();
    this.rawInputStream = byteStream;
  }

  /**
   * Set the raw (unescaped) reader.
   *
   * @param characterStream the reader
   */
  public void setCharacterStream(Reader characterStream) {
    assertReadNotStarted();
    this.rawReader = characterStream;
  }

  /**
   * Set the system id.
   * System ids are URLs or file system paths.
   * The system id is used as a fallback if neither the reader nor
   * the input stream is provided.
   *
   * @param systemId the system id
   */
  public void setSystemId(String systemId) {
    assertReadNotStarted();
    this.systemId = systemId;
  }

  /**
   * Make sure that the configuration of this reader does not change
   * after characters have been read. Late changes would result in
   * a mismatch between the configuration and the characters that are read.
   */
  private void assertReadNotStarted() {
    if (reader != null) {
      throw new IllegalStateException("stream has already been read when setting encoding");
    }
  }

  /**
   * Return the reader for providing the unparsed stream of characters.
   *
   * @return the reader
   * @throws IOException if a I/O problem occurred
   * @noinspection IOResourceOpenedButNotSafelyClosed
   */
  private Reader getReader() throws IOException {
    if (reader == null) {
      if (rawReader != null) {
        reader = new BufferedReader(rawReader, BUFFER_SIZE / 2);
      } else {
        InputStream inputStream = getInputStream();
        if (encoding != null) {
          reader = new InputStreamReader(inputStream, Charset.forName(encoding));
        } else {
          reader = new XmlStreamReader(inputStream);
        }
      }
    }
    return reader;
  }

  /**
   * Return an input stream for backing the reader.
   * The stream is either the raw input stream or a stream
   * of the resource indicated by the system id.
   *
   * @return an input stream for backing the reader
   * @throws IOException if a I/O problem occurred
   */
  private InputStream getInputStream() throws IOException {
    InputStream inputStream;
    if (rawInputStream != null) {
      inputStream = rawInputStream;
    } else {
      URI uri = null;
      try {
        uri = new URI(systemId);
      } catch (URISyntaxException e) {
        // Ignore, likely an obscure file name.
      }
      if (uri != null && uri.isAbsolute()) {
        // A real URI.
        inputStream = uri.toURL().openStream();
      } else {
        // A file path.
        //noinspection IOResourceOpenedButNotSafelyClosed
        inputStream = new FileInputStream(systemId);
      }
    }
    return inputStream;
  }

  @Override
  public int read(char[] chars, int off, int len) throws IOException {
    for (int i = 0; i < len; i++) {
      int c = getNext();
      if (c < 0) {
        return i == 0 ? -1 : i;
      }
      chars[off + i] = (char)c;
    }
    return len;
  }

  /**
   * Return the next character as a non-negative integer,
   * or -1 to indicate the end of the stream.
   *
   * @return the next character
   */
  private int getNext() throws IOException {
    if (transformed.length() == 0) {
      transformNext();
    }
    if (transformed.length() == 0) {
      return -1;
    }
    char c = transformed.charAt(0);
    transformed.deleteCharAt(0);
    return 0xFFFF & c;
  }

  /**
   * Prepare more characters to be returned.
   */
  private void transformNext() throws IOException {
    // Try to fill the look ahead buffer to detect the end of the stream.
    fillLookAheadBuffer(1);
    // If there are more characters to be transformed, ...
    if (lookAheadBuffer.length() > 0) {
      // ... delegate the transformation to the current mode.
      modes.getFirst().transformNext();
    }
  }

  /**
   * Make sure that the given number of characters is available in
   * the look ahead buffer. The size of the buffer may be less than the
   * indicated character count if the end of the stream has been reached.
   *
   * @param count the character count
   * @throws IOException
   */
  private void fillLookAheadBuffer(int count) throws IOException {
    while (lookAheadBuffer.length() < count) {
      int c = getReader().read();
      if (c < 0) {
        return;
      }
      lookAheadBuffer.append((char) c);
    }
  }

  /**
   * Determine whether the look-ahead buffer starts with the given string.
   * If yes, transfer the string to the transformed buffer immediately and return
   * true.
   * If no, return false and leave both buffers unchanged.
   *
   * @param candidate the string to transfer.
   * @return whether the string was transferred
   */
  private boolean tryTransfer(String candidate) throws IOException {
    fillLookAheadBuffer(candidate.length());
    if (lookAheadBuffer.length() < candidate.length()) {
      return false;
    }
    for (int i = 0; i < candidate.length(); i++) {
      if (candidate.charAt(i) != lookAheadBuffer.charAt(i)) {
        return false;
      }
    }
    lookAheadBuffer.delete(0, candidate.length());
    transformed.append(candidate);
    return true;
  }

  @Override
  public void close() throws IOException {
    getReader().close();
  }

  /**
   * A transition from one parse to another parse mode.
   * The transition is triggered by the given indicator string
   * that has to occur in the character sequence.
   * If the next mode is null, this indicates that the current mode
   * has to be removed from the stack, because a grammatical object
   * has ended.
   */
  private static class Transition {
    private final String indicator;
    private final ParseMode next;

    Transition(String indicator, ParseMode next) {
      this.indicator = indicator;
      this.next = next;
    }

    String getIndicator() {
      return indicator;
    }

    ParseMode getNext() {
      return next;
    }
  }

  /**
   * A mode for parsing an XML stream.
   */
  private class ParseMode {
    /**
     * Whether to escape supplementary characters with character entities in this mode.
     */
    private final boolean escape;
    /**
     * The transitions that are allowed in this mode.
     */
    private final Collection<Transition> transitions = new ArrayList<Transition>();

    /**
     * Create a new parse mode.
     *
     * @param escape whether to escape supplementary characters
     */
    ParseMode(boolean escape) {
      this.escape = escape;
    }

    /**
     * Create a new parse mode that does not escape supplementary characters.
     */
    ParseMode() {
      this.escape = false;
    }

    /**
     * Add a transition to this parse mode and return this parse mode.
     * The transition is triggered by the given indicator and
     * pushes the given next mode onto the mode stack.
     *
     * @param indicator the indicator string
     * @param next the next mode
     * @return this
     */
    ParseMode withTransition(String indicator, ParseMode next) {
      transitions.add(new Transition(indicator, next));
      return this;
    }

    /**
     * Add transition to the previous mode and return this parse mode.
     * The transition to the previous mode is triggered by the given indicator.
     *
     * @param indicator the indicator string
     * @return this
     */
    ParseMode withStop(String indicator) {
      return withTransition(indicator, null);
    }

    /**
     * Remove one or more characters from the look ahead buffer
     * and add that transformed character or characters to the
     * transformed buffer.
     *
     * @throws IOException if a I/O problem occurred
     */
    void transformNext() throws IOException {
      // Test whether any transition matches the next characters
      // to be transformed. Transitions registered earlier take precedence.
      for (Transition transition : transitions) {
        // If a match is found (implicitly transferring the matched indicator), ...
        if (tryTransfer(transition.getIndicator())) {
          // ... the mode stack is updated accordingly.
          ParseMode next = transition.getNext();
          if (next == null) {
            modes.removeFirst();
          } else {
            modes.addFirst(next);
          }
          return;
        }
      }
      transferOneCodePoint();
    }

    /**
     * Transfer one code point from the look ahead buffer to the transformed
     * buffer, escaping supplementary character if required in this mode.
     * One code point consists of one or two characters.
     *
     * @throws IOException
     */
    void transferOneCodePoint() throws IOException {
      // Two characters might be needed if the first is a high surrogate.
      fillLookAheadBuffer(2);
      int i = lookAheadBuffer.codePointAt(0);
      boolean isSupplementaryCodePoint = Character.isSupplementaryCodePoint(i);

      // Remove the characters defining the code point from the look ahead buffer.
      if (isSupplementaryCodePoint) {
        // A supplementary character.
        lookAheadBuffer.delete(0, 2);
      } else {
        // A BMP (basic multilingual plane) character.
        lookAheadBuffer.delete(0, 1);
      }

      // Escape if necessary.
      if (escape && isSupplementaryCodePoint) {
        // The main workaround for JDK-8058175:
        // Represent supplementary characters in attribute values as numeric entities.
        transformed.append("&#").append(i).append(";");
      } else {
        transformed.appendCodePoint(i);
      }
    }
  }
}
