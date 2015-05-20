An input source that replaces supplementary characters in an XML input stream
with character entities if they appear as attribute values.
This works around the bug JDK-8058175 in the Xerces provided with Java 8
and earlier.
To this end, the library parses the streamed XML data and replaces supplementary
characters that appear in inappropriate places with numeric XML character entities.

The com.coremedia.xml.FullUnicodeInputSource class is a drop-in
replacement of org.xml.sax.InputSource.
