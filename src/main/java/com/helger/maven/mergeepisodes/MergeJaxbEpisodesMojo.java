/*
 * Copyright (C) 2021-2025 Philip Helger (www.helger.com)
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

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.jspecify.annotations.NonNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.helger.annotation.Nonempty;
import com.helger.annotation.misc.Since;
import com.helger.base.CGlobal;
import com.helger.base.array.ArrayHelper;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.io.file.SimpleFileIO;
import com.helger.xml.NodeListIterator;
import com.helger.xml.XMLFactory;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.write.XMLWriter;

/**
 * @author Philip Helger
 * @description Merge all mentioned JAXB episode files "sun-jaxb.episode" into a single one
 */
@Mojo (name = "merge-jaxb-episodes", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, threadSafe = true)
public final class MergeJaxbEpisodesMojo extends AbstractMojo
{
  private static final String OUTPUT_FOLDER = "merged-jaxb-episode";
  private static final String FILENAME = "sun-jaxb.episode";

  @Parameter (property = "project", required = true, readonly = true)
  private MavenProject project;

  /**
   * The base directory to start scanning recursively. Defaults to
   * <code>${project.build.directory}</code>
   */
  @Parameter (name = "baseDirectory", defaultValue = "${project.build.directory}", required = true)
  private File baseDirectory;

  /**
   * Be a verbose in what we are doing?
   */
  @Parameter (name = "verbose", defaultValue = "false", required = false)
  private boolean verbose = false;

  /**
   * The list of files to be merged, relative to the baseDirectory
   */
  @Parameter (name = "episodeFiles", defaultValue = "**/" + FILENAME, required = true)
  private String [] episodeFiles;

  /**
   * Use Jakarta Namespace (JAXB 3.0 and 4.0) or Sun Namespace (JAXB 2.x)
   */
  @Since ("0.0.4")
  @Parameter (name = "useJakarta", defaultValue = "true", required = false)
  private boolean useJakarta = true;

  public void setBaseDirectory (@NonNull final File aDir)
  {
    baseDirectory = aDir;
    if (!baseDirectory.isAbsolute ())
      baseDirectory = new File (project.getBasedir (), aDir.getPath ());
    if (!baseDirectory.exists ())
      getLog ().error ("Base directory " + baseDirectory.toString () + " does not exist!");
  }

  public void setVerbose (final boolean b)
  {
    verbose = b;
  }

  public void setUseJakarta (final boolean b)
  {
    useJakarta = b;
  }

  @NonNull
  private static String _getResString (@NonNull final Resource aRes)
  {
    return "[dir=" +
           aRes.getDirectory () +
           "; includes=" +
           aRes.getIncludes () +
           "; excludes=" +
           aRes.getExcludes () +
           "; targetPath=" +
           aRes.getTargetPath () +
           "]";
  }

  private void _listResources (@NonNull final String sReason)
  {
    final List <Resource> aRess = project.getBuild ().getResources ();
    getLog ().info ("Maven resources [" + aRess.size () + "] - " + sReason);
    for (final Resource aRes : aRess)
      getLog ().info ("  Resource: " + _getResString (aRes));
  }

  private String _getHeaderComment (final List <File> aMatches)
  {
    final StringBuilder aCommentSB = new StringBuilder ();
    aCommentSB.append ("This file was automatically created by ph-merge-jaxb-episodes-maven-plugin - do NOT edit.");
    aCommentSB.append ("\nThis file was made up of:");
    for (final File aFile : aMatches)
      aCommentSB.append ("\n  ").append (aFile.getPath ());
    aCommentSB.append ("\n\nThis file was written at ")
              .append (ZonedDateTime.now (ZoneId.systemDefault ()).toString ());
    return aCommentSB.toString ();
  }

  private byte [] _mergeByReadingLines (@NonNull @Nonempty final List <File> aMatches)
  {
    if (verbose)
      getLog ().info ("Merging " + aMatches.size () + " files using line by line reading");

    final Charset aCS = StandardCharsets.UTF_8;
    try (NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ())
    {
      aBAOS.write ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n".getBytes (aCS));

      aBAOS.write ((useJakarta ? "<bindings xmlns=\"https://jakarta.ee/xml/ns/jaxb\" if-exists=\"true\" version=\"3.0\">\n"
                               : "<bindings xmlns=\"http://java.sun.com/xml/ns/jaxb\" if-exists=\"true\" version=\"2.1\">\n").getBytes (aCS));
      aBAOS.write (("<!--\n" + _getHeaderComment (aMatches) + "\n-->\n").getBytes (aCS));

      for (final File aFile : aMatches)
      {
        final List <String> aLines = new CommonsArrayList <> (10_000);
        SimpleFileIO.readFileLines (aFile, aCS, aLines);
        // Remove the first 2 and the last line
        aLines.remove (0);
        aLines.remove (0);
        aLines.remove (aLines.size () - 1);

        // Copy the rest into the file
        for (final String sLine : aLines)
        {
          aBAOS.write (sLine.getBytes (aCS));
          aBAOS.write ('\n');
        }
      }

      aBAOS.write ("</bindings>\n".getBytes (aCS));
      return aBAOS.toByteArray ();
    }
  }

  @NonNull
  private byte [] _mergeByReadingXML (@NonNull @Nonempty final ICommonsList <File> aMatches) throws MojoExecutionException
  {
    if (verbose)
      getLog ().info ("Merging " + aMatches.size () + " files using XML parsing/cloning");

    final Document aTargetDoc = XMLFactory.newDocument ();

    final Element aTargetRoot;
    if (useJakarta)
    {
      // <bindings xmlns="https://jakarta.ee/xml/ns/jaxb" if-exists="true"
      // version="3.0">
      aTargetRoot = (Element) aTargetDoc.appendChild (aTargetDoc.createElementNS ("https://jakarta.ee/xml/ns/jaxb",
                                                                                  "bindings"));
      aTargetRoot.setAttribute ("if-exists", "true");
      aTargetRoot.setAttribute ("version", "3.0");
      aTargetRoot.appendChild (aTargetDoc.createComment (_getHeaderComment (aMatches)));
    }
    else
    {
      // <bindings xmlns="http://java.sun.com/xml/ns/jaxb" if-exists="true"
      // version="2.1">
      aTargetRoot = (Element) aTargetDoc.appendChild (aTargetDoc.createElementNS ("http://java.sun.com/xml/ns/jaxb",
                                                                                  "bindings"));
      aTargetRoot.setAttribute ("if-exists", "true");
      aTargetRoot.setAttribute ("version", "2.1");
      aTargetRoot.appendChild (aTargetDoc.createComment (_getHeaderComment (aMatches)));
    }

    for (final File aFile : aMatches)
    {
      if (verbose)
        getLog ().info ("Parsing XML file '" + aFile.getPath () + "'");

      final Document aExistingDoc = DOMReader.readXMLDOM (aFile);
      if (aExistingDoc == null)
        throw new MojoExecutionException ("The file '" + aFile.getAbsolutePath () + "' is invalid XML");

      final Element aExistingRoot = aExistingDoc.getDocumentElement ();
      if (!"bindings".equals (aExistingRoot.getLocalName ()))
        throw new MojoExecutionException ("The file '" +
                                          aFile.getAbsolutePath () +
                                          "' does not seem to be a JAXB binding file (unexpected element name)");
      if (!"http://java.sun.com/xml/ns/jaxb".equals (aExistingRoot.getNamespaceURI ()))
        throw new MojoExecutionException ("The file '" +
                                          aFile.getAbsolutePath () +
                                          "' does not seem to be a JAXB binding file (unexpected namespace URI)");

      // Copy to target document
      for (final Node aChildNode : NodeListIterator.createChildNodeIterator (aExistingRoot))
      {
        aTargetRoot.appendChild (aTargetDoc.adoptNode (aChildNode.cloneNode (true)));
      }
    }

    return XMLWriter.getNodeAsBytes (aTargetDoc);
  }

  public void execute () throws MojoExecutionException
  {
    if (baseDirectory == null)
      throw new MojoExecutionException ("No baseDirectory specified!");
    if (ArrayHelper.isEmpty (episodeFiles))
      throw new MojoExecutionException ("No episode file is specified");

    final ICommonsList <File> aMatches = new CommonsArrayList <> ();

    if (false)
    {
      // Scan all existing resources
      for (final Resource aRes : project.getBuild ().getResources ())
      {
        final DirectoryScanner aScanner = new DirectoryScanner ();
        aScanner.setBasedir (aRes.getDirectory ());
        aScanner.setIncludes (aRes.getIncludes ().toArray (CGlobal.EMPTY_STRING_ARRAY));
        aScanner.setExcludes (aRes.getExcludes ().toArray (CGlobal.EMPTY_STRING_ARRAY));
        aScanner.setCaseSensitive (true);
        aScanner.scan ();

        final String [] aFilenames = aScanner.getIncludedFiles ();
        if (aFilenames != null)
          for (final String sFilename : aFilenames)
          {
            final File aFile = new File (aRes.getDirectory (), sFilename);

            if (sFilename.equals (FILENAME))
            {
              getLog ().info ("Found episode file to merge: " + aFile.getPath ());
              aMatches.add (aFile);
            }
          }
      }
    }
    else
    {
      // Scan the provided directory
      final DirectoryScanner aScanner = new DirectoryScanner ();
      aScanner.setBasedir (baseDirectory);
      aScanner.setIncludes (episodeFiles);
      // Exclude the result of a previous builds as well as our output file
      aScanner.setExcludes (new String [] { "classes/META-INF/" + FILENAME, "**/" + OUTPUT_FOLDER + "/" + FILENAME });
      aScanner.setCaseSensitive (true);
      aScanner.scan ();

      final String [] aFilenames = aScanner.getIncludedFiles ();
      if (aFilenames != null)
        for (final String sFilename : aFilenames)
        {
          final File aFile = new File (baseDirectory, sFilename);
          getLog ().info ("Found episode file to merge: " + aFile.getPath ());
          aMatches.add (aFile);
        }
    }

    if (aMatches.size () <= 1)
    {
      getLog ().warn ("Found " + aMatches.size () + " episode files - nothing to merge");
    }
    else
    {
      final byte [] aTargetXML;
      if (true)
        aTargetXML = _mergeByReadingLines (aMatches);
      else
      {
        // Unfortunately this approach does not work because the "tns" namespace
        // attributes are lost:
        // <bindings xmlns:tns="http://www.w3.org/ns/corevocabulary/business"
        // if-exists="true" scd="x-schema::tns">
        // becomes
        // <bindings if-exists="true" scd="x-schema::tns">
        // which renders the resulting file unuseable
        aTargetXML = _mergeByReadingXML (aMatches);
      }
      final File fTarget = new File (project.getBuild ().getDirectory () + "/" + OUTPUT_FOLDER, FILENAME);

      if (verbose)
        getLog ().info ("Writing combined " + FILENAME + " to '" + fTarget.getAbsolutePath () + "'");

      if (SimpleFileIO.writeFile (fTarget, aTargetXML).isFailure ())
        throw new MojoExecutionException ("Failed to write merged JAXB episode file to '" +
                                          fTarget.getAbsolutePath () +
                                          "'");

      if (verbose)
        _listResources ("Before modification");

      // Remove all existing Resources that contain "META-INF/sun-jaxb.episode"
      {
        final String sSearchName1 = "META-INF/" + FILENAME;
        final String sSearchName2 = "META-INF\\" + FILENAME;

        // Copy list to modify it
        for (final Resource aRes : new CommonsArrayList <> (project.getBuild ().getResources ()))
        {
          if (aRes.getIncludes ().contains (sSearchName1) || aRes.getIncludes ().contains (sSearchName2))
          {
            // Include is explicitly mentioned
            aRes.getIncludes ().remove (sSearchName1);
            aRes.getIncludes ().remove (sSearchName2);
            if (verbose)
              getLog ().info ("Removed '" + sSearchName1 + "' from: " + _getResString (aRes));

            if (aRes.getIncludes ().isEmpty ())
            {
              // Remove the whole resource if nothing is left, because empty
              // means "use all" and that is definitively not intended if only a
              // single resource is present.
              project.getBuild ().removeResource (aRes);

              if (verbose)
                getLog ().info ("Removed from project: " + _getResString (aRes));
            }
          }
          else
            if (aRes.getExcludes ().isEmpty ())
            {
              aRes.getExcludes ().add (sSearchName1);
              if (verbose)
                getLog ().info ("Excluding '" + sSearchName1 + "' from: " + _getResString (aRes));
            }
            else
            {
              if (verbose)
                getLog ().info ("  Unchanged Resource: " + _getResString (aRes));
            }
        }
      }

      // Add output file as a resource-file as the last action
      {
        final Resource aResource = new Resource ();
        aResource.setDirectory (fTarget.getParentFile ().getAbsolutePath ());
        aResource.addInclude (fTarget.getName ());
        aResource.setFiltering (false);
        aResource.setTargetPath ("META-INF/");
        project.addResource (aResource);
      }

      if (verbose)
        _listResources ("After modification");
    }
  }
}
