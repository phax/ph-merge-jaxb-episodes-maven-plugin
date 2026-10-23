/*
 * Copyright (C) 2021-2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.maven.mergeepisodes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.charset.StandardCharsets;

import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.NonNull;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.helger.annotation.Nonnegative;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.xml.ChildElementIterator;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.write.XMLWriter;
import com.helger.xml.serialize.write.XMLWriterSettings;

/**
 * Test for <code>MergeJaxbEpisodesMojo.mergeEpisodes</code> using real world episode files.
 *
 * @author Philip Helger
 */
public final class MergeJaxbEpisodesTest
{
  private static final String NS_JAKARTA = "https://jakarta.ee/xml/ns/jaxb";
  private static final String NS_SUN = "http://java.sun.com/xml/ns/jaxb";

  @NonNull
  private static ICommonsList <File> _fatturapa ()
  {
    // Jakarta episode files of 4 executions of the same JAXB plugin (ph-fatturapa)
    final ICommonsList <File> ret = new CommonsArrayList <> ();
    for (final String s : new String [] { "120", "121", "122", "123" })
      ret.add (new File ("src/test/resources/episodes/fatturapa/v" + s + "/sun-jaxb.episode"));
    return ret;
  }

  @NonNull
  private static ICommonsList <File> _europass ()
  {
    // Sun episode files
    return new CommonsArrayList <> (new File ("src/test/resources/poms/unittest1/a/sun-jaxb.episode"),
                                    new File ("src/test/resources/poms/unittest1/b/sun-jaxb.episode"));
  }

  @NonNull
  private static Document _merge (final ICommonsList <File> aFiles, final boolean bUseJakarta) throws Exception
  {
    final byte [] aBytes = MergeJaxbEpisodesMojo.mergeEpisodes (aFiles, bUseJakarta, "test comment", null);
    final Document ret = DOMReader.readXMLDOM (aBytes);
    assertNotNull ("Merged result is not valid XML", ret);
    return ret;
  }

  @Nonnegative
  private static int _countDescendants (@NonNull final Element aRoot, final String sNS, final String sLocalName)
  {
    return aRoot.getElementsByTagNameNS (sNS, sLocalName).getLength ();
  }

  /**
   * Check that the top-level elements of the merged document are the top-level elements of all
   * source files (in order), that all of them are in the expected namespace and that the "tns"
   * prefix - which is only used inside the "scd" attribute value - still resolves to the same
   * namespace URI as in the source.
   */
  @Nonnegative
  private static int _countClasses (@NonNull final ICommonsList <File> aFiles, final String sNS)
  {
    int ret = 0;
    for (final File aFile : aFiles)
      ret += _countDescendants (DOMReader.readXMLDOM (aFile).getDocumentElement (), sNS, "class");
    assertTrue (ret > 0);
    return ret;
  }

  private static void _checkTopLevel (@NonNull final Document aDoc,
                                      final String sNS,
                                      final String sVersion,
                                      @NonNull final ICommonsList <File> aSources)
  {
    final Element aRoot = aDoc.getDocumentElement ();
    assertEquals ("bindings", aRoot.getLocalName ());
    assertEquals (sNS, aRoot.getNamespaceURI ());
    assertEquals (sVersion, aRoot.getAttribute ("version"));
    assertEquals ("true", aRoot.getAttribute ("if-exists"));

    final ICommonsList <Element> aExpected = new CommonsArrayList <> ();
    for (final File aSource : aSources)
      for (final Element aChild : new ChildElementIterator (DOMReader.readXMLDOM (aSource).getDocumentElement ()))
        aExpected.add (aChild);
    assertFalse (aExpected.isEmpty ());

    final ICommonsList <Element> aActual = new CommonsArrayList <> (new ChildElementIterator (aRoot));
    assertEquals (aExpected.size (), aActual.size ());
    for (int i = 0; i < aActual.size (); ++i)
    {
      final Element aSrc = aExpected.get (i);
      final Element aDst = aActual.get (i);
      assertEquals (sNS, aDst.getNamespaceURI ());
      assertEquals ("x-schema::tns", aDst.getAttribute ("scd"));
      assertNotNull (aSrc.lookupNamespaceURI ("tns"));
      assertEquals (aSrc.lookupNamespaceURI ("tns"), aDst.lookupNamespaceURI ("tns"));
    }
  }

  @Test
  public void testFatturaPAJakarta () throws Exception
  {
    final Document aDoc = _merge (_fatturapa (), true);
    _checkTopLevel (aDoc, NS_JAKARTA, "3.0", _fatturapa ());

    final Element aRoot = aDoc.getDocumentElement ();
    assertEquals (4, _countDescendants (aRoot, NS_JAKARTA, "schemaBindings"));
    assertEquals (_countClasses (_fatturapa (), NS_JAKARTA), _countDescendants (aRoot, NS_JAKARTA, "class"));
    assertEquals (0, _countDescendants (aRoot, NS_SUN, "class"));

    final String sAll = new String (MergeJaxbEpisodesMojo.mergeEpisodes (_fatturapa (), true, null, null),
                                    StandardCharsets.UTF_8);
    for (final String s : new String [] { "v120", "v121", "v122", "v123" })
      assertTrue (s, sAll.contains ("com.helger.fatturapa." + s + ".FPA" + s.substring (1) + "FatturaElettronicaType"));
  }

  @Test
  public void testFatturaPASun () throws Exception
  {
    // Jakarta files, converted to the Sun namespace
    final Document aDoc = _merge (_fatturapa (), false);
    _checkTopLevel (aDoc, NS_SUN, "2.1", _fatturapa ());
    assertEquals (_countClasses (_fatturapa (), NS_JAKARTA),
                  _countDescendants (aDoc.getDocumentElement (), NS_SUN, "class"));
    assertEquals (0, _countDescendants (aDoc.getDocumentElement (), NS_JAKARTA, "class"));
  }

  @Test
  public void testEuropassSun () throws Exception
  {
    final Document aDoc = _merge (_europass (), false);
    if (false)
      System.out.println (XMLWriter.getNodeAsString (aDoc,
                                                     new XMLWriterSettings ().setUseExistingNamespaceDeclarations (true)));
    _checkTopLevel (aDoc, NS_SUN, "2.1", _europass ());

    // Same number of classes as in the sources
    final int nExpected = _countClasses (_europass (), NS_SUN);
    assertTrue (nExpected > 0);
    assertEquals (nExpected, _countDescendants (aDoc.getDocumentElement (), NS_SUN, "class"));
  }

  @Test
  public void testEuropassJakarta () throws Exception
  {
    // Sun files, converted to the Jakarta namespace
    final Document aDoc = _merge (_europass (), true);
    _checkTopLevel (aDoc, NS_JAKARTA, "3.0", _europass ());
    assertEquals (0, _countDescendants (aDoc.getDocumentElement (), NS_SUN, "class"));
    assertEquals (_countClasses (_europass (), NS_SUN),
                  _countDescendants (aDoc.getDocumentElement (), NS_JAKARTA, "class"));
  }

  @Test
  public void testMixedInputs () throws Exception
  {
    final ICommonsList <File> aFiles = new CommonsArrayList <> (_fatturapa ());
    aFiles.addAll (_europass ());
    _checkTopLevel (_merge (aFiles, true), NS_JAKARTA, "3.0", aFiles);
  }

  @Test
  public void testInvalidFile () throws Exception
  {
    try
    {
      MergeJaxbEpisodesMojo.mergeEpisodes (new CommonsArrayList <> (new File ("pom.xml")), true, null, null);
      fail ();
    }
    catch (final MojoExecutionException ex)
    {
      // expected
    }
  }
}
